package com.quanxiaoha.xiaohashu.ai.biz.util;

import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeTools {

    private static final ZoneId CST = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss");

    @Tool(description = "获取当前准确的日期和时间（中国时区 Asia/Shanghai，UTC+8）。当用户询问现在几点、今天几号、星期几等与当前时间相关的问题时，必须调用此工具获取真实时间，不要凭记忆编造。")
    public String getCurrentDateTime() {
        return LocalDateTime.now(CST).format(FORMATTER) + "（中国标准时间 UTC+8）";
    }
}