package com.quanxiaoha.xiaohashu.ai.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记操作消息
 *
 * <p>字段与 note 模块的 NoteOperateMqDTO 保持一致，但 AI 模块不直接 import 对方的类，
 * 避免模块间产生编译期耦合（消息格式由 MQ 协议约定）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteOperateMessage {

    /**
     * 发布者 ID
     */
    private Long creatorId;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 0：删除 1：发布 2：更新
     */
    private Integer type;
}