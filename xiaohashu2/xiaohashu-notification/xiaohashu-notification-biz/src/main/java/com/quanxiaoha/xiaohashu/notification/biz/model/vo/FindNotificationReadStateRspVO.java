package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 通知已读状态查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNotificationReadStateRspVO {

    /**
     * 通知列表
     */
    private List<FindNotificationReadItemRspVO> items;

    /**
     * 通知状态版本
     */
    private String revision;

    /**
     * 各分类全部已读水位
     */
    private Map<String, String> readAllSeq;

}
