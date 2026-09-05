package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:17
 * @version: v1.0.0
 * @description: 查询笔记详情 响应VO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteDetailRspVO {

    /**
     * 笔记 ID
     */
    private Long id;

    /**
     * 笔记类型：0-图文笔记
     */
    private Integer type;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 笔记内容
     */
    private String content;

    /**
     * 图片 URI 集合
     */
    private List<String> imgUris;

    /**
     * 话题 ID
     */
    private Long topicId;

    /**
     * 话题名称
     */
    private String topicName;

    /**
     * 发布者用户 ID
     */
    private Long creatorId;

    /**
     * 发布者昵称
     */
    private String creatorName;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 视频链接
     */
    private String videoUri;

    /**
     * 编辑时间
     */
    private LocalDateTime updateTime;

    /**
     * 笔记是否可见：0 公开， 1 自己可见
     */
    private Integer visible;

    /**
     * 点赞总数
     */
    private Long likeTotal;

    /**
     * 收藏总数
     */
    private Long collectTotal;

    /**
     * 评论总数
     */
    private Long commentTotal;

}
