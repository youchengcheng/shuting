package com.quanxiaoha.xiaohashu.notification.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知内容预览
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationPreviewRspVO {

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 笔记封面
     */
    private String cover;

    /**
     * 通知摘要
     */
    private String summary;

    /**
     * 评论图片
     */
    private String imageUrl;

}
