package com.quanxiaoha.xiaohashu.ai.biz.enums;

import com.quanxiaoha.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 模块响应异常码
 */
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("AI-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AI-10001", "参数错误"),
    AI_SERVICE_ERROR("AI-10002", "AI 服务调用失败，请稍后重试"),
    AI_RESPONSE_PARSE_ERROR("AI-10003", "AI 返回内容解析失败"),

    // ----------- 笔记相关 -----------
    NOTE_NOT_FOUND("AI-20000", "笔记不存在或已删除"),
    NOTE_CONTENT_EMPTY("AI-20001", "该笔记没有正文内容，无法进行分析"),
    NOTE_ANALYSIS_FAIL("AI-20002", "笔记分析失败，请稍后重试"),

    // ----------- 检索助手相关 -----------
    INDEX_NOT_READY("AI-30000", "笔记检索索引尚未就绪，请先执行全量重建"),
    INDEX_REBUILD_FAIL("AI-30001", "笔记索引重建失败"),
    ASSISTANT_NO_RESULT("AI-30002", "没有找到与您需求匹配的笔记，换个说法再试试吧"),
    ASSISTANT_FAIL("AI-30003", "AI 助手处理失败，请稍后重试"),

    // ----------- 对话相关 -----------
    CHAT_NOT_EXISTED("AI-40000", "此对话不存在"),
    CHAT_CREATE_FAIL("AI-40001", "创建对话失败"),
    ;

    private final String errorCode;

    private final String errorMessage;
}