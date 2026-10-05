package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求
 */
@Data
public class ChatReqVO {

    /**
     * 会话 UUID；不传则新建会话
     */
    private String chatUuid;

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "提问内容不能超过 500 字")
    private String query;

    /**
     * 最多引用几篇笔记
     */
    private Integer topN;
}