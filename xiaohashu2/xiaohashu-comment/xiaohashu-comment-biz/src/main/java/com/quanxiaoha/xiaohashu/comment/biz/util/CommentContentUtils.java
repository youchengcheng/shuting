package com.quanxiaoha.xiaohashu.comment.biz.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/*
 * 评论内容标识工具类
 * */
public class CommentContentUtils {

    /*
     * 根据评论 ID 生成固定的内容标识
     * */
    public static String contentUuid(Long commentId) {
        // 使用固定前缀和评论 ID 生成 UUID，重复投递时复用同一个内容标识
        return UUID.nameUUIDFromBytes(("xiaohashu-comment:" + commentId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
