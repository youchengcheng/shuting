package com.quanxiaoha.framework.biz.idempotent.aspect;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.biz.idempotent.annotation.Idempotent;
import com.quanxiaoha.framework.biz.idempotent.constant.IdempotentConstants;
import com.quanxiaoha.framework.biz.idempotent.enums.IdempotentPolicyEnum;
import com.quanxiaoha.framework.biz.idempotent.enums.IdempotentResponseCodeEnum;
import com.quanxiaoha.framework.biz.idempotent.service.IdempotentService;
import com.quanxiaoha.framework.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等切面：拦截标注 @Idempotent 的方法
 * <p>
 * TOKEN 策略：请求头携带 Idempotent-Token，基于 Redis 单条 DEL 命令原子消费，删除成功即为首次请求；
 * PARAM 策略：按 SpEL 表达式生成幂等 Key，基于 Redis 单条 SET NX EX 命令原子占位，窗口期内拒绝重复请求。
 * <p>
 * 通用约定：
 * 1. Redis 异常：降级放行 + 告警日志，不阻塞业务；
 * 2. 日志不打印 Token 明文。
 */
@Aspect
@Slf4j
@Order(1)
public class IdempotentAspect {

    /** SpEL 表达式解析器 */
    private static final ExpressionParser KEY_EXPRESSION_PARSER = new SpelExpressionParser();
    /** 方法参数名解析器 */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    /** Key 表达式编译结果缓存 */
    private static final Map<String, Expression> KEY_EXPRESSION_CACHE = new ConcurrentHashMap<>();

    private final IdempotentService idempotentService;

    public IdempotentAspect(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    /**
     * 环绕：凡是添加 @Idempotent 的方法，都会执行以下逻辑
     */
    @Around("@annotation(idempotent)")
    public Object doAround(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 目前支持 TOKEN、PARAM 两种策略
        if (idempotent.policy() == IdempotentPolicyEnum.PARAM) {
            return doAroundParam(joinPoint, idempotent);
        }
        return doAroundToken(joinPoint, idempotent);
    }

    /**
     * TOKEN 策略：基于请求头 Idempotent-Token 的一次性令牌消费
     */
    private Object doAroundToken(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 请求类、方法、URI，仅用于日志定位，不打印 Token 明文
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        HttpServletRequest request = currentRequest();
        String uri = Objects.isNull(request) ? null : request.getRequestURI();

        // 从请求头中获取幂等 Token
        String token = Objects.isNull(request) ? null : request.getHeader(IdempotentConstants.IDEMPOTENT_TOKEN_HEADER);

        // 灰度期：请求头未携带 Token，直接放行，仅记录日志
        if (StringUtils.isBlank(token)) {
            log.warn("[幂等][灰度放行] 请求未携带 {}，直接放行。URI: {}, 请求类: {}, 方法: {}",
                    IdempotentConstants.IDEMPOTENT_TOKEN_HEADER, uri, className, methodName);
            return joinPoint.proceed();
        }

        // 获取当前登录用户 ID（网关已强制登录，此处为兜底）
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(userId)) {
            log.error("[幂等][降级放行] 未获取到当前登录用户 ID，降级放行。URI: {}, 请求类: {}, 方法: {}",
                    uri, className, methodName);
            return joinPoint.proceed();
        }

        // 原子消费 Token：单条 DEL 命令判定，禁止 check-then-set
        boolean consumed;
        try {
            consumed = idempotentService.consume(userId, token);
        } catch (Exception e) {
            // Redis 异常降级：告警并放行，不阻塞业务
            log.error("[幂等][降级放行] Redis 异常，降级放行。URI: {}, 请求类: {}, 方法: {}, userId: {}",
                    uri, className, methodName, userId, e);
            return joinPoint.proceed();
        }

        // 消费失败：Token 已被消费或已过期，判定为重复请求，拒绝
        if (!consumed) {
            log.warn("[幂等][拒绝] 重复提交。URI: {}, 请求类: {}, 方法: {}, userId: {}",
                    uri, className, methodName, userId);
            throw new BizException(IdempotentResponseCodeEnum.DUPLICATE_SUBMIT);
        }

        // 首次请求，放行
        return joinPoint.proceed();
    }

    /**
     * PARAM 策略：基于 SpEL 表达式生成幂等 Key 的窗口防重
     */
    private Object doAroundParam(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 请求类、方法、URI，仅用于日志定位
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        HttpServletRequest request = currentRequest();
        String uri = Objects.isNull(request) ? null : request.getRequestURI();

        // 解析幂等 Key 表达式
        String dedupKey;
        try {
            dedupKey = IdempotentConstants.buildIdempotentParamKey(evaluateKey(joinPoint, idempotent.key()));
        } catch (Exception e) {
            // 表达式解析失败：告警并降级放行，不阻塞业务
            log.error("[幂等][降级放行] PARAM 策略 Key 表达式解析失败，降级放行。表达式: {}, URI: {}, 请求类: {}, 方法: {}",
                    idempotent.key(), uri, className, methodName, e);
            return joinPoint.proceed();
        }

        // 原子占位：单条 SET NX EX 命令，占位成功即为窗口期内首次请求，禁止 check-then-set
        boolean acquired;
        try {
            acquired = idempotentService.tryAcquire(dedupKey, Duration.ofSeconds(idempotent.ttl()));
        } catch (Exception e) {
            // Redis 异常降级：告警并放行，不阻塞业务
            log.error("[幂等][降级放行] Redis 异常，降级放行。Key: {}, URI: {}, 请求类: {}, 方法: {}",
                    dedupKey, uri, className, methodName, e);
            return joinPoint.proceed();
        }

        // 占位失败：窗口期内重复请求，拒绝
        if (!acquired) {
            log.warn("[幂等][拒绝] 窗口期内重复请求。Key: {}, URI: {}, 请求类: {}, 方法: {}",
                    dedupKey, uri, className, methodName);
            throw buildRepeatRequestException(idempotent);
        }

        // 首次请求，放行
        return joinPoint.proceed();
    }

    /**
     * 解析 PARAM 策略的幂等 Key，支持 #a0、#p0（方法参数下标形式）或参数名形式引用方法参数，如：#a0.phone
     */
    private String evaluateKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        if (StringUtils.isBlank(keyExpression)) {
            throw new IllegalArgumentException("@Idempotent PARAM 策略必须配置 key 表达式");
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EvaluationContext evaluationContext = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), PARAMETER_NAME_DISCOVERER);
        Expression expression = KEY_EXPRESSION_CACHE.computeIfAbsent(keyExpression, KEY_EXPRESSION_PARSER::parseExpression);
        Object value = expression.getValue(evaluationContext);
        if (Objects.isNull(value) || StringUtils.isBlank(String.valueOf(value))) {
            throw new IllegalArgumentException("@Idempotent PARAM 策略 key 表达式结果为空: " + keyExpression);
        }
        return String.valueOf(value);
    }

    /**
     * 构建窗口期内重复请求的异常，支持通过注解 message 属性自定义提示语
     */
    private BizException buildRepeatRequestException(Idempotent idempotent) {
        BizException bizException = new BizException(IdempotentResponseCodeEnum.REPEAT_REQUEST);
        if (StringUtils.isNotBlank(idempotent.message())) {
            bizException.setErrorMessage(idempotent.message());
        }
        return bizException;
    }

    /**
     * 获取当前请求对象
     */
    private HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

}
