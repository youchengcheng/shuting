package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题列表查询请求 VO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindTopicListReqVO {

    /**
     * 关键词
     */
    private String keyword;
}
