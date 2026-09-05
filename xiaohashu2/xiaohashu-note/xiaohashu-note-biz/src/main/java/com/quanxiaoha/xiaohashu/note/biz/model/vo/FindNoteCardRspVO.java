package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记卡片（列表页）响应 VO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteCardRspVO {

    /**
     * 笔记 ID
     */
    private Long id;

    /**
     * 笔记类型：0-图文，1-视频
     */
    private Integer type;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 封面图（图文笔记取第一张图）
     */
    private String cover;

    /**
     * 视频链接
     */
    private String videoUri;

    /**
     * 发布者用户 ID
     */
    private Long creatorId;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 点赞总数
     */
    private Long likeTotal;

    /**
     * 笔记可见性：0-公开，1-仅自己可见
     */
    private Integer visible;
}
