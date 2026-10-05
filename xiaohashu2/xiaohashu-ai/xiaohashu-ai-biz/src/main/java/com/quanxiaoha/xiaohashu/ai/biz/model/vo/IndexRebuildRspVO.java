package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引全量重建结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRebuildRspVO {

    /**
     * 扫描到的可检索笔记总数
     */
    private long total;

    /**
     * 本次真正重建的笔记数
     */
    private long indexed;

    /**
     * 跳过的笔记数（内容未变化）
     */
    private long skipped;

    /**
     * 失败的笔记数
     */
    private long failed;

    /**
     * 耗时（毫秒）
     */
    private long costMs;
}