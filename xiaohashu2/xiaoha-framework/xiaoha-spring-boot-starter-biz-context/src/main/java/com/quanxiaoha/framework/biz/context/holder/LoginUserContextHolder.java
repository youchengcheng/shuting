package com.quanxiaoha.framework.biz.context.holder;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.quanxiaoha.framework.common.constant.GlobalConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@lombok.extern.slf4j.Slf4j
public class LoginUserContextHolder {

    //初始化一个threadLocal变量
    private static final ThreadLocal<Map<String,Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL =
            TransmittableThreadLocal.withInitial(HashMap::new);

    //设置用户id
    public static void setUserId(Object value){
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID,value);
    }

    //获取用户id
    public static Long getUserId(){
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if(Objects.isNull(value)){
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.warn("ThreadLocal 中 userId 格式非法: {}", value);
            return null;
        }
    }

    //删除 threadLocal
    public static void remove(){
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }

}
