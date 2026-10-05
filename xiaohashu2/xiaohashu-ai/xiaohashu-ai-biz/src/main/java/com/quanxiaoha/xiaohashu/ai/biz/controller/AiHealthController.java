package com.quanxiaoha.xiaohashu.ai.biz.controller;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模块探针接口（M1/M2 验证用）
 *
 * <p>网关对 {@code /ai/**} 不做白名单放行，未登录会直接 401，
 * 因此这两个接口同时用来验证「网关鉴权 + userId 透传」链路是否打通。</p>
 */
@RestController
@RequestMapping("/ai")
public class AiHealthController {

    /**
     * 存活探针
     */
    @GetMapping("/health")
    public Response<String> health() {
        return Response.success("xiaohashu-ai is up");
    }

    /**
     * 当前登录用户探针
     *
     * @return 网关透传并写入上下文的 userId；未登录时为 null（正常应被网关拦截，走不到这里）
     */
    @GetMapping("/whoami")
    public Response<Long> whoami() {
        return Response.success(LoginUserContextHolder.getUserId());
    }

}
