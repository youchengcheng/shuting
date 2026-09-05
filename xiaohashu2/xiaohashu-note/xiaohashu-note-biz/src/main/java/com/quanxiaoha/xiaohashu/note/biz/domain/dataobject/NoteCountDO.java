package com.quanxiaoha.xiaohashu.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记计数（t_note_count 查询用，非独立表映射）
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteCountDO {

    /**
     * 笔记 ID
     */
    private Long noteId;

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
