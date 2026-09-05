package com.quanxiaoha.ai.robot.utils;

import org.apache.commons.lang3.StringUtils;

/*
* 字符串工具类
* */
public class StringUtil {

    public static String truncate(String message,int maxLenght) {
        //判空
        if(StringUtils.isBlank(message)) {
            return "";
        }

        //去除首尾空格
        String newMessage = StringUtils.trim(message);

        //判断文本长度是否小于等于最大长度
        if(newMessage.length() <= maxLenght) {
            return newMessage;
        }

        //截取指定长度
        return newMessage.substring(0,maxLenght);

    }

}
