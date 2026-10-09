package com.quanxiaoha.xiaohashu.ai.biz.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量关键词提取器
 *
 * <p>不依赖分词库，通过 n-gram + 停用词过滤从用户提问中提取核心关键词，
 * 用于对向量召回结果做关键词硬过滤，过滤掉"话题沾边但不含核心关键词"的噪声笔记。</p>
 */
public final class KeywordExtractor {

    private KeywordExtractor() {
    }

    /**
     * 中文停用词表（常见虚词、疑问词、动词）
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "有", "和", "与", "或", "及", "等", "也", "都", "就",
            "要", "会", "能", "可以", "可能", "应该", "需要", "想要", "希望", "请", "请问",
            "帮", "帮忙", "看看", "一下", "一些", "什么", "怎么", "怎样", "如何", "为什么",
            "哪里", "哪个", "哪些", "多少", "几", "吗", "呢", "吧", "啊", "哦", "呀", "嘛",
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "这个", "那个", "这些", "那些",
            "求", "推荐", "介绍", "分享", "说", "讲", "问", "想", "觉得", "认为", "知道", "了解",
            "有没有", "有没有人", "大家", "各位", "大佬", "大神", "朋友", "家人们"
    ));

    /**
     * 匹配连续的中文字符（2 字以上）和英文单词
     */
    private static final Pattern CHINESE_RUN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[a-zA-Z]{2,}");

    /**
     * 从用户提问中提取关键词
     *
     * @param query 用户提问
     * @return 关键词列表（去重，保留出现顺序），如果提取不到则返回空列表
     */
    public static List<String> extract(String query) {
        if (AiStringUtils.isBlank(query)) {
            return new ArrayList<>();
        }

        Set<String> keywords = new LinkedHashSet<>();

        // 1. 提取连续中文片段
        Matcher chineseMatcher = CHINESE_RUN.matcher(query);
        while (chineseMatcher.find()) {
            String run = chineseMatcher.group();
            // 对较长的中文片段做 2-gram 和 3-gram 切分
            if (run.length() <= 4) {
                // 短片段直接作为关键词
                addIfNotStopWord(keywords, run);
            } else {
                // 长片段：提取 2-gram 和 3-gram
                for (int n = 2; n <= Math.min(3, run.length()); n++) {
                    for (int i = 0; i + n <= run.length(); i++) {
                        String gram = run.substring(i, i + n);
                        addIfNotStopWord(keywords, gram);
                    }
                }
            }
        }

        // 2. 提取英文单词（品牌名、型号等）
        Matcher englishMatcher = ENGLISH_WORD.matcher(query);
        while (englishMatcher.find()) {
            keywords.add(englishMatcher.group().toLowerCase());
        }

        // 3. 提取数字（型号、年份等）
        Pattern numberPattern = Pattern.compile("\\d{2,}");
        Matcher numberMatcher = numberPattern.matcher(query);
        while (numberMatcher.find()) {
            keywords.add(numberMatcher.group());
        }

        return new ArrayList<>(keywords);
    }

    private static void addIfNotStopWord(Set<String> keywords, String word) {
        if (word == null || word.length() < 2) {
            return;
        }
        if (STOP_WORDS.contains(word)) {
            return;
        }
        keywords.add(word);
    }
}
