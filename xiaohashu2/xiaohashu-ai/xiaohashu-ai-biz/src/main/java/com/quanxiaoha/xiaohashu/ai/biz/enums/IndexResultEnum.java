package com.quanxiaoha.xiaohashu.ai.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单篇笔记建索引的结果
 */
@Getter
@AllArgsConstructor
public enum IndexResultEnum {

    FAILED(0, "建索引失败"),
    INDEXED(1, "已建索引"),
    SKIPPED(2, "内容未变化或不可检索，已跳过"),
    ;

    private final Integer code;

    private final String desc;
}