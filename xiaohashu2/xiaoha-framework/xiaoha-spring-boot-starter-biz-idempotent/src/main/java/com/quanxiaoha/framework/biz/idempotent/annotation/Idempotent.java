package com.quanxiaoha.framework.biz.idempotent.annotation;

import com.quanxiaoha.framework.biz.idempotent.enums.IdempotentPolicyEnum;

import java.lang.annotation.*;

/**
 * 接口幂等注解：标注在需要幂等控制的方法上，由 IdempotentAspect 统一拦截处理
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface Idempotent {

    /**
     * 幂等策略，默认 TOKEN
     *
     * @return
     */
    IdempotentPolicyEnum policy() default IdempotentPolicyEnum.TOKEN;

    /**
     * PARAM 策略必填：生成幂等 Key 的 SpEL 表达式，支持 #a0、#p0（方法参数下标形式）或参数名形式引用方法参数，如：#a0.phone
     *
     * @return
     */
    String key() default "";

    /**
     * PARAM 策略：幂等 Key 的有效期（单位：秒），窗口期内重复请求将被拒绝，默认 60 秒
     *
     * @return
     */
    int ttl() default 60;

    /**
     * 命中重复请求时返回给前端的提示语，为空时使用默认文案
     *
     * @return
     */
    String message() default "";

}
