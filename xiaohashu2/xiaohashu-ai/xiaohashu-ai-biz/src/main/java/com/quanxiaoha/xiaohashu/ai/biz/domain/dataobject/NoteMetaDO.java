package com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记元数据（只读）
 *
 * <p>与 note 模块共用同一个业务库 xiaohashu，AI 模块只做只读查询，
 * 不在这里写任何笔记数据，避免绕过 note 服务的业务校验。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_note")
public class NoteMetaDO {

    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容是否为空
     */
    private Boolean isContentEmpty;

    /**
     * 发布者 ID
     */
    private Long creatorId;

    private Long topicId;

    private String topicName;

    /**
     * 0：图文 1：视频
     */
    private Integer type;

    /**
     * 图片链接，逗号分隔
     */
    private String imgUris;

    private String videoUri;

    /**
     * 0：公开 1：仅自己可见
     */
    private Integer visible;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 0：待审核 1：正常 2：已删除 3：已下架
     */
    private Integer status;

    /**
     * 正文在 Cassandra 中的 UUID
     */
    private String contentUuid;
}