package com.quanxiaoha.xiaohashu.notification.biz.util;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.NotificationSessionDTO;
import com.quanxiaoha.xiaohashu.notification.biz.service.NotificationTokenValidator;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/*
 * 通知请求的 Token 解析与登录校验工具
 * */
@Component
public class NotificationAuthUtils {

    @Resource
    private NotificationTokenValidator notificationTokenValidator;
    @Resource
    private NotificationSettings notificationSettings;

    /*
     * 校验网关用户上下文、登录 Token 和通知服务开关
     * */
    public NotificationSessionDTO authenticate(HttpServletRequest request) {
        // 只信任网关过滤器写入的用户上下文，不读取客户端自行填写的用户 ID
        Long userId = LoginUserContextHolder.getUserId();
        List<String> authorizationHeaders = Collections.list(request.getHeaders("Authorization"));
        // 多个 Authorization 请求头可能存在身份歧义，必须拒绝
        if (userId == null || userId <= 0 || authorizationHeaders.size() != 1) {
            throw new BizException(ResponseCodeEnum.LOGIN_REQUIRED);
        }

        // 按 Bearer 协议提取 Token，其他认证格式不作为通知登录凭证
        String authorization = authorizationHeaders.get(0);
        if (!authorization.startsWith("Bearer ")) {
            throw new BizException(ResponseCodeEnum.LOGIN_REQUIRED);
        }
        String token = authorization.substring(7);
        // 拒绝空凭证、空白字符及逗号拼接的多个凭证
        if (token.isBlank() || token.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == ',')) {
            throw new BizException(ResponseCodeEnum.LOGIN_REQUIRED);
        }

        // 服务端再次验证 Token 与用户 ID 的归属，防止直接调用服务端口绕过网关
        notificationTokenValidator.require(token, userId);
        // 校验通过后再检查开关，维持未登录请求先返回 401 的顺序
        if (!notificationSettings.isEnabled()) {
            throw new BizException(ResponseCodeEnum.SERVICE_NOT_ENABLED);
        }
        return new NotificationSessionDTO(userId, token);
    }
}
