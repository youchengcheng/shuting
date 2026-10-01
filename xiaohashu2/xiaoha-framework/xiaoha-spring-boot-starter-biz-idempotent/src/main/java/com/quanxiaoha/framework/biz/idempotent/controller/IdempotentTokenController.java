package com.quanxiaoha.framework.biz.idempotent.controller;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.biz.idempotent.enums.IdempotentResponseCodeEnum;
import com.quanxiaoha.framework.biz.idempotent.service.IdempotentService;
import com.quanxiaoha.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 幂等 Token 申请接口
 */
@RestController
@RequestMapping("/idempotent")
@Slf4j
public class IdempotentTokenController {

    private final IdempotentService idempotentService;

    public IdempotentTokenController(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    /**
     * 申请幂等 Token：GET /idempotent/token
     */
    @GetMapping("/token")
    public Response<String> applyToken() {
        // 获取当前登录用户 ID（由网关透传，上下文过滤器写入 ThreadLocal）
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(userId)) {
            log.warn("[幂等][告警] 令牌申请失败，未获取到当前登录用户 ID");
            return Response.fail(IdempotentResponseCodeEnum.TOKEN_APPLY_FAIL);
        }

        try {
            String token = idempotentService.generate(userId);
            log.info("[幂等] 令牌申请成功，userId: {}", userId);
            return Response.success(token);
        } catch (Exception e) {
            // Redis 异常：告警并返回失败，避免发放未写入 Redis 的“毒令牌”
            log.error("[幂等][告警] 令牌申请失败，userId: {}", userId, e);
            return Response.fail(IdempotentResponseCodeEnum.TOKEN_APPLY_FAIL);
        }
    }

}
