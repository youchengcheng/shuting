package com.quanxiaoha.xiaohashu.kv.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论内容
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindCommentContentRspDTO {

    /**
     * 评论内容 UUID
     */
    private String contentId;

    /**
     * 评论内容
     */
    private String content;

}
