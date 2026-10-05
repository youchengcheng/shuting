package com.quanxiaoha.xiaohashu.ai.biz.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * AI 模块字符串工具
 */
public final class AiStringUtils {

    private AiStringUtils() {
    }

    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * 按最大长度截断字符串，避免超长文本把 prompt 撑爆
     */
    public static String truncate(String str, int maxChars) {
        if (str == null) {
            return null;
        }
        if (maxChars <= 0 || str.length() <= maxChars) {
            return str;
        }
        return str.substring(0, maxChars) + "...(内容过长已截断)";
    }

    /**
     * 计算内容指纹，用于判断笔记是否需要重新建索引
     */
    public static String md5(String str) {
        return str == null ? null : DigestUtil.md5Hex(str);
    }

    /**
     * 从大模型返回的文本中提取第一段 JSON（对象或数组）
     *
     * <p>大模型经常会在 JSON 外面包一层 ```json 代码块或说明文字，这里做一次兜底提取。</p>
     */
    public static String extractJson(String text) {
        if (isBlank(text)) {
            return null;
        }
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start;
        char open;
        char close;
        if (objStart < 0 && arrStart < 0) {
            return null;
        }
        if (objStart < 0) {
            start = arrStart;
        } else if (arrStart < 0) {
            start = objStart;
        } else {
            start = Math.min(objStart, arrStart);
        }
        if (text.charAt(start) == '{') {
            open = '{';
            close = '}';
        } else {
            open = '[';
            close = ']';
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}