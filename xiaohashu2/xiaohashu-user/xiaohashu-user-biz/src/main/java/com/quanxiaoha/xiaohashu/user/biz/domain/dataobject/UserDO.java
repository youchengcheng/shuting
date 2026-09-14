package com.quanxiaoha.xiaohashu.user.biz.domain.dataobject;

import cn.hutool.core.date.DateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO {
    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 小红书号(唯一标识, 注册时由分布式 ID 生成器自动生成)
     */
    private String xiaohashuId;

    /**
     * 密码(加密存储)
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像链接
     */
    private String avatar;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 个人主页背景图链接
     */
    private String backgroundImg;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 性别(0：女 1：男)
     */
    private Integer sex;

    /**
     * 状态(0：启用 1：禁用)
     */
    private Integer status;

    /**
     * 个人简介
     */
    private String introduction;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除(0：未删除 1：已删除)
     */
    private Boolean isDeleted;
}