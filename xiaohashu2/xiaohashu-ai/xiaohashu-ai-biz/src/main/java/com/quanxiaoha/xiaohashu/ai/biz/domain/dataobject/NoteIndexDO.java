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
 * 笔记检索索引镜像
 *
 * <p>向量库里存的是 chunk，这里存的是「某篇笔记在向量库里有哪些 chunk」，
 * 有了它才能精准地按 noteId 删除 / 覆盖向量，不用去扫 pgvector 的 metadata。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_note_index")
public class NoteIndexDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 笔记 ID（唯一）
     */
    private Long noteId;

    private Long creatorId;

    private String title;

    private String topicName;

    /**
     * 正文 UUID
     */
    private String contentUuid;

    /**
     * 内容指纹（标题 + 正文），用于判断是否需要重建
     */
    private String contentHash;

    /**
     * 该笔记被切成了多少个 chunk
     */
    private Integer chunkTotal;

    /**
     * 0：已删除 1：已索引
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}