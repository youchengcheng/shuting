package com.quanxiaoha.xiaohashu.comment.biz.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
* 热度计算
* */
public class HeatCalculator {
    //热度计算的权重配置 ------点赞70%，回复30%
    private static final double LIKE_WEIGHT = 0.7;
    private static final double REPLY_WEIGHT = 0.3;

    public static BigDecimal calculateHeat(long likeCount,long replyCount){
        //点赞数权重70%，被回复数权重30%
        BigDecimal likeWeight = new BigDecimal(LIKE_WEIGHT);
        BigDecimal replyWeight = new BigDecimal(REPLY_WEIGHT);

        //转换点赞数和回复数为bigdecimal
        BigDecimal likeCountBD = new BigDecimal(likeCount);
        BigDecimal replyCountBD = new BigDecimal(replyCount);

        //计算热度
        BigDecimal heat = likeCountBD.multiply(likeWeight).add(replyCountBD.multiply(replyWeight));

        //四舍五入保留两位小数
        return heat.setScale(2, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) {
        int likeCount = 150;    // 点赞数
        int replyCount = 10;    // 被回复数

        // 计算热度
        BigDecimal heat = calculateHeat(likeCount, replyCount);

        // 输出热度值
        System.out.println("Calculated Heat: " + heat);
    }

}
