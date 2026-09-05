package com.quanxiaoha.xiaohashu.count.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/*
* 关注/取关 类型
* */
@Getter
@AllArgsConstructor
public enum FollowUnfollowTypeEnum {
    //关注
    follow(1),
    //取关
    unfollow(0)
    ;

    private final Integer code;

    //FollowUnfollowTypeEnum.values()拿到枚举数组
    public static FollowUnfollowTypeEnum valueOf(Integer code){
        for (FollowUnfollowTypeEnum followUnfollowTypeEnum : FollowUnfollowTypeEnum.values()) {
            if(Objects.equals(code,followUnfollowTypeEnum.getCode())){
                return followUnfollowTypeEnum;
            }
        }
        return null;
    }

}
