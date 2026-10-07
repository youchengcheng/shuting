package com.quanxiaoha.xiaohashu.ai.biz.constant;

/**
 * AI 模块 Redis Key 常量
 *
 * <p>仅缓存「列表 / 摘要」类数据；会话详细消息走 MySQL，不缓存。</p>
 */
public class RedisKeyConstants {

    /**
     * 用户历史会话列表 KEY 前缀
     * 完整 key 形如：ai:chat:list:{userId}
     * 仅存储会话摘要（chatUuid / title / updateTime），不存储会话消息内容
     */
    private static final String AI_CHAT_LIST_KEY = "ai:chat:list:";

    /**
     * 用户历史会话列表缓存有效期（秒）：1 小时
     */
    public static final long AI_CHAT_LIST_TTL_SECONDS = 3600L;

    /**
     * 构建完整的用户历史会话列表 KEY
     */
    public static String buildChatListKey(Long userId) {
        return AI_CHAT_LIST_KEY + userId;
    }
}
