package com.quanxiaoha.xiaohashu.user.relation.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 关注关系状态
 */
@Getter
@AllArgsConstructor
public enum FollowStatusEnum {
    // 未关注
    NOT_FOLLOWED(0),
    // 已关注（单向：我关注了 TA）
    FOLLOWED(1),
    // 互相关注
    MUTUAL_FOLLOW(2),
    ;

    private final Integer code;

    /**
     * 根据 code 获取对应的枚举
     */
    public static FollowStatusEnum valueOf(Integer code) {
        for (FollowStatusEnum e : FollowStatusEnum.values()) {
            if (Objects.equals(code, e.getCode())) {
                return e;
            }
        }
        return null;
    }
}
