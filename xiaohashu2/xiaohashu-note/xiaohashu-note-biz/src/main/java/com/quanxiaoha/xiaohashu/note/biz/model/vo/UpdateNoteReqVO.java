package com.quanxiaoha.xiaohashu.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateNoteReqVO {

    /**
     * 笔记 ID
     */
    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

    /**
     * 笔记类型：0-图文 1-视频
     */
    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    /**
     * 图片链接集合
     */
    private List<String> imgUris;

    /**
     * 视频链接
     */
    private String videoUri;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 笔记内容
     */
    private String content;

    /**
     * 话题 ID
     */
    private Long topicId;
}
