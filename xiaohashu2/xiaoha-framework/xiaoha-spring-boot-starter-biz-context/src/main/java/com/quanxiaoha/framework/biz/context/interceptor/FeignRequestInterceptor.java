package com.quanxiaoha.framework.biz.context.interceptor;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.GlobalConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/*
* feign请求拦截器，所有 Feign 调用都会经过这里
* RequestInterceptor统一处理所有 Feign 发出的请求
* */
@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        //从上下文中获取用户id
        Long userId = LoginUserContextHolder.getUserId();
        //判断用户id是否为空，不为空，添加到feign请求头中，使用header方法。-----header("请求头key", "请求头value")
        if(Objects.nonNull(userId)){
            requestTemplate.header(GlobalConstants.USER_ID,String.valueOf(userId));
            log.info("########## feign 请求设置请求头 userId: {}", userId);
        }

    }
}
