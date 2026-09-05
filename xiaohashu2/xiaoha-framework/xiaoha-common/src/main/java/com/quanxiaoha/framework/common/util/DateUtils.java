package com.quanxiaoha.framework.common.util;

import com.quanxiaoha.framework.common.constant.DateConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/*
* 日期工具类
* */

public class DateUtils {

    /*
     * toInstant:指定时区
     * toEpochMilli：转为毫秒值
     * */
    public static Long localDateTime2Timestamp(LocalDateTime localDateTime){
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /*
    * LocalDateTime 转 String 字符串
    * */
    public static String localDateTime2String(LocalDateTime time){
        return time.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }

    /*
    * 转为友好的时间字符串
    * */
    public static String formatRelativeTime(LocalDateTime dateTime){

        //当前时间
        LocalDateTime now = LocalDateTime.now();

        //计算与当前时间的差距
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long horsDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);


        if(daysDiff < 1) {//判断，如果是今天
            if(horsDiff < 1){//如果是几分钟前
                return minutesDiff + "分钟前";
            }else {
                return horsDiff + "小时前";//如果是几个小时前
            }
        } else if (daysDiff == 1) {//如果是昨日
            return "昨天" + dateTime.format(DateConstants.DATE_FORMAT_H_M);
        } else if (daysDiff > 7) {//如果是最近一周
            return daysDiff + "天前";
        } else if (dateTime.getYear() == now.getYear()) {//如果是今年
            return dateTime.format(DateConstants.DATE_FORMAT_M_D);
        }else {//如果是去年或者更早
            return dateTime.format(DateConstants.DATE_FORMAT_Y_M_D);
        }
    }

    public static void main(String[] args) {
        // 测试示例
        LocalDateTime dateTime1 = LocalDateTime.now().minusMinutes(10); // 10分钟前
        LocalDateTime dateTime2 = LocalDateTime.now().minusHours(3); // 3小时前
        LocalDateTime dateTime3 = LocalDateTime.now().minusDays(1).minusHours(5); // 昨天 20:12
        LocalDateTime dateTime4 = LocalDateTime.now().minusDays(2); // 2天前
        LocalDateTime dateTime5 = LocalDateTime.now().minusDays(10); // 11-06
        LocalDateTime dateTime6 = LocalDateTime.of(2023, 12, 1, 12, 30, 0); // 2023-12-01

        System.out.println(formatRelativeTime(dateTime1)); // 输出 "10分钟前"
        System.out.println(formatRelativeTime(dateTime2)); // 输出 "3小时前"
        System.out.println(formatRelativeTime(dateTime3)); // 输出 "昨天 20:12"
        System.out.println(formatRelativeTime(dateTime4)); // 输出 "2天前"
        System.out.println(formatRelativeTime(dateTime5)); // 输出 "11-06"
        System.out.println(formatRelativeTime(dateTime6)); // 输出 "2023-12-01"
    }

    /**
     * 计算年龄
     */
    public static int calculateAge(LocalDate birthDate) {
        // 获取当前日期
        LocalDate currentDate = LocalDate.now();

        // 计算出生日期到当前日期的 Period 对象
        Period period = Period.between(birthDate, currentDate);

        // 返回完整的年份（即年龄）
        return period.getYears();
    }
}
