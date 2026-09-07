package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * 个人主页 - 已发布笔记列表
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindPublishedNoteListRspVO {

    /**
     * 笔记分页数据
     */
    private List<NoteItemRspVO> notes;

    /**
     * 下一页的游标
     */
    private Long nextCursor;

}
