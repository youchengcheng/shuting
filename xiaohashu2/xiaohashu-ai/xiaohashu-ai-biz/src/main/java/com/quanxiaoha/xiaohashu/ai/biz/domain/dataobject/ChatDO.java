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
 * AI 对话会话
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_chat")
public class ChatDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 UUID（对外暴露，避免自增 ID 泄露）
     */
    private String uuid;

    /**
     * 归属用户
     */
    private Long userId;

    /**
     * 会话标题（默认取首轮提问）
     */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}