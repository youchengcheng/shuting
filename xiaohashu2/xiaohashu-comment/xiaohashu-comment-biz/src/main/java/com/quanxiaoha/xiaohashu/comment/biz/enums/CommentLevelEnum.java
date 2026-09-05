package com.quanxiaoha.xiaohashu.comment.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 评论级别
 **/
@Getter
@AllArgsConstructor
public enum CommentLevelEnum {
    // 一级评论
    ONE(1),
    // 二级评论
    TWO(2),
    ;

    private final Integer code;

    /**
     * 根据类型 code 获取对应的枚举
     */
    public static CommentLevelEnum valueOf(Integer code) {
        for (CommentLevelEnum CommentLevelEnum : CommentLevelEnum.values()) {
            if(Objects.equals(CommentLevelEnum.getCode(),code)) {
                return CommentLevelEnum;
            }
        }
        return null;
    }

}

