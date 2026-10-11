package com.quanxiaoha.xiaohashu.search.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* 搜索用户出参
* */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchUserRspVO {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 小哈书ID
     */
    private String xiaohashuId;

    /**
     * 笔记发布总数
     */
    private Integer noteTotal;

    /**
     * 粉丝总数
     */
    private String fansTotal;

    /**
     * 昵称：关键词高亮
     */
    private String highlightNickname;

    /**
     * 关注关系状态：0-未关注 1-已关注 2-互相关注
     */
    private Integer followStatus;
}
