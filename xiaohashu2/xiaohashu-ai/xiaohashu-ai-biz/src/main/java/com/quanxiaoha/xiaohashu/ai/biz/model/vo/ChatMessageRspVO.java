package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRspVO {

    /**
     * user / assistant
     */
    private String role;

    private String content;

    /**
     * 本轮引用的笔记 ID
     */
    private List<String> noteRefs;

    private LocalDateTime createTime;
}