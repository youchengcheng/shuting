package com.quanxiaoha.xiaohashu.ai.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRspVO {

    private String chatUuid;

    private String title;

    private LocalDateTime updateTime;
}