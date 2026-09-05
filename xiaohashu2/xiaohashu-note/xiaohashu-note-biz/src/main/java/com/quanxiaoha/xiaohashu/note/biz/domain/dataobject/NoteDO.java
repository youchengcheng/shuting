package com.quanxiaoha.xiaohashu.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteDO {
    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容是否为空(0：不为空 1：空)
     */
    private Boolean isContentEmpty;

    /**
     * 发布者 ID
     */
    private Long creatorId;

    /**
     * 话题 ID
     */
    private Long topicId;

    /**
     * 话题名称
     */
    private String topicName;

    /**
     * 是否置顶(0：未置顶 1：置顶)
     */
    private Boolean isTop;

    /**
     * 类型(0：图文 1：视频)
     */
    private Integer type;

    /**
     * 笔记图片链接(逗号隔开)
     */
    private String imgUris;

    /**
     * 视频链接
     */
    private String videoUri;

    /**
     * 可见范围(0：公开,所有人可见 1：仅对自己可见)
     */
    private Integer visible;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 状态(0：待审核 1：正常展示 2：被删除(逻辑删除) 3：被下架)
     */
    private Integer status;

    /**
     * 笔记内容关联字段（对应 Cassandra 存储的笔记内容 UUID）
     */
    private String contentUuid;
}