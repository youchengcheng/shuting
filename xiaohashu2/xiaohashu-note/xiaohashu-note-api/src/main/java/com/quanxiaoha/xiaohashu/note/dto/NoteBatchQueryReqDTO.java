package com.quanxiaoha.xiaohashu.note.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量查询笔记请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteBatchQueryReqDTO {

    /**
     * 查询 ID 集合
     */
    private List<Long> ids;

    /**
     * 当前访问者 ID
     */
    private Long viewerId;

}
