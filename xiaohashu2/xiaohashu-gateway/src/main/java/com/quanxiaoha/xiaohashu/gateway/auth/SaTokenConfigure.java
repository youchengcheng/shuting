package com.quanxiaoha.xiaohashu.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [Sa-Token 权限认证] 配置类
 **/
@Configuration
@Slf4j
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    log.info("==================> SaReactorFilter, Path: {}", SaHolder.getRequest().getRequestPath());
                    // 放行 CORS 预检请求（OPTIONS），交由网关 globalcors 处理
                    if (SaHolder.getRequest().getMethod().equalsIgnoreCase("OPTIONS")) {
                        return;
                    }
                    // 登录校验
                    SaRouter.match("/**") // 拦截所有路由
                            .notMatch("/auth/login") // 排除登录接口
                            .notMatch("/auth/verification/code/send") // 排除验证码发送接口
                            .notMatch("/user/register") // 排除用户注册接口（注册无需登录）
                            .notMatch("/user/profile") // 用户主页资料（未登录时可浏览他人主页）
                            .notMatch("/note/channel/list") // 频道列表
                            .notMatch("/note/discover/note/list") // 发现页笔记列表
                            .notMatch("/note/profile/note/list") // 个人主页笔记列表
                            .notMatch("/note/topic/list") // 话题列表
                            .notMatch("/note/detail") // 笔记详情（未登录可浏览公开笔记）
                            .notMatch("/comment/list") // 评论列表（未登录可查看）
                            .notMatch("/comment/child/list") // 二级评论列表（未登录可查看）
                            .notMatch("/relation/following/list") // 关注列表（未登录可查看）
                            .notMatch("/relation/fans/list") // 粉丝列表（未登录可查看）
                            .notMatch("/search/note") // 笔记搜索（未登录可搜索公开笔记）
                            .notMatch("/search/user") // 用户搜索（未登录可搜索公开用户）

                            .check(r -> StpUtil.checkLogin()) // 校验是否登录
                    ;

                    // 权限认证 -- 不同模块, 校验不同权限
                    SaRouter.match("/auth/logout", r -> StpUtil.checkLogin());
//                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkRole("admin"));
                    // SaRouter.match("/admin/**", r -> StpUtil.checkPermission("admin"));
                    // SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                    // SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));

                    // 更多匹配 ...  */
                })
                //异常处理(只负责抛出异常)
                .setError(e->{
                    //判断属于哪一种异常
                    if(e instanceof NotLoginException){
                        //未登录异常
                        throw new NotLoginException(e.getMessage(),null,null);
                    }else if (e instanceof NotPermissionException || e instanceof NotRoleException){
                        //权限不够，角色不对异常
                        throw new NotPermissionException(e.getMessage());
                    }else {
                        //其他异常统一处理
                        throw new RuntimeException(e.getMessage());
                    }
                })
                ;
    }
}
