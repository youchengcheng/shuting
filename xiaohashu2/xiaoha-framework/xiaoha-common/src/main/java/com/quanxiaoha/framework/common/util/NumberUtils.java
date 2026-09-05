package com.quanxiaoha.framework.common.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

/*
* 数字工具类
* */
public class NumberUtils {

    /*
    * 数字转换字符串
    * */
    public static String formatNumberString(long number){
        if(number < 10000){
            //小于一万
            return String.valueOf(number);
        } else if (number >= 10000 && number < 100000000) {
            //大于一万，小于一亿
            double result = number / 10000.0;
            //保留小数后一位
            DecimalFormat df = new DecimalFormat("#.#");
            //禁用四舍五入
            df.setRoundingMode(RoundingMode.DOWN);
            String formatted = df.format(result);
            return formatted + "万";
        }else {
            //大于一亿
            return "9999万";
        }
    }

    public static void main(String[] args) {
        // 测试
        System.out.println(formatNumberString(1000));         // 1000
        System.out.println(formatNumberString(11130));        // 1.1万
        System.out.println(formatNumberString(26719300));     // 2671.9万
        System.out.println(formatNumberString(10000000));    // 1000万
        System.out.println(formatNumberString(999999));       // 99.9万
        System.out.println(formatNumberString(150000000));    // 超过一亿，展示9999万
        System.out.println(formatNumberString(99999));        // 9.9万
    }
}
