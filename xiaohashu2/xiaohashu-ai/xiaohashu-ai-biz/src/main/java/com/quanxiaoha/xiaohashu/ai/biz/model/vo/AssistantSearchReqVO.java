package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 笔记检索助手 - 提问请求
 */
@Data
public class AssistantSearchReqVO {

    /**
     * 用户的自然语言需求，例如「推荐几篇适合新手的露营装备清单笔记」
     */
    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "提问内容不能超过 500 字")
    private String query;

    /**
     * 最多返回几篇笔记，不传用配置默认值
     */
    private Integer topN;
}