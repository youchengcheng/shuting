package com.quanxiaoha.xiaohashu.user.relation.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/*
* 校验是否关注：Lua 脚本返回结果
* */
@Getter
@AllArgsConstructor
public enum FollowCheckResultEnum {
    // 关注列表缓存不存在，需回源数据库
    CACHE_NOT_EXIST(-1L),
    // 未关注
    NOT_FOLLOWED(0L),
    // 已关注
    FOLLOWED(1L),
    ;

    private final Long code;

    /**
     * 根据 code 获取对应的枚举
     */
    public static FollowCheckResultEnum valueOf(Long code) {
        for (FollowCheckResultEnum followCheckResultEnum : FollowCheckResultEnum.values()) {
            if (Objects.equals(code, followCheckResultEnum.getCode())) {
                return followCheckResultEnum;
            }
        }
        return null;
    }
}
