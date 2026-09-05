package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 个人主页笔记分页查询请求 VO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindProfileNotePageListReqVO {

    /**
     * 查询类型：1-发布的笔记，2-点赞的笔记，3-收藏的笔记
     */
    private Integer type;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 页码
     */
    private Integer pageNo;
}
