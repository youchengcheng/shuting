package com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_chat_message")
public class ChatMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chatUuid;

    private Long userId;

    /**
     * user / assistant
     */
    private String role;

    private String content;

    /**
     * 本轮回答引用的笔记 ID，JSON 数组字符串
     */
    private String noteRefs;

    private LocalDateTime createTime;
}