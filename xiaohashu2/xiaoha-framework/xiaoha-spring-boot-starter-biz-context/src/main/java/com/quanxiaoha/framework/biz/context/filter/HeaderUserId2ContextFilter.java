package com.quanxiaoha.framework.biz.context.filter;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * 提取请求头中的用户 ID 保存到上下文中，以方便后续使用
 * */
@Component
@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        //从请求头中获取用户id
        String userId = request.getHeader(GlobalConstants.USER_ID);

        log.info("HeaderUserId2ContextFilter,用户id为：{}",userId);

        //判断userId是否存在且为有效数字（网关透传的"null"字符串会导致后续Long.parseLong抛出异常）
        if(StringUtils.isBlank(userId) || !StringUtils.isNumeric(userId)){
            //不存在或非数字，直接放行
            filterChain.doFilter(request,response);
            return;
        }

        //存在，将userId存入threadLoacl
        log.info("=============设置userId到threadlocal中，用户id为：{}",userId);
        LoginUserContextHolder.setUserId(userId);

        try {
            //将请求和响应传递给过滤链的下一个过滤器
            filterChain.doFilter(request,response);
        }finally {
            //最后删除threadLocal，防止内存泄露
            LoginUserContextHolder.remove();
            log.info("=============删除threadlocal，用户id为：{}",userId);
        }
    }
}
