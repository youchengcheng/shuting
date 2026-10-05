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
 * 笔记 AI 解读结果
 *
 * <p>同一篇笔记内容没有变化时，直接复用历史解读结果，省钱又提速。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_note_analysis")
public class NoteAnalysisDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Long userId;

    private String title;

    /**
     * 内容指纹，内容变了则缓存失效
     */
    private String contentHash;

    /**
     * AI 解读结果（Markdown）
     */
    private String result;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}