package com.quanxiaoha.xiaohashu.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
* 笔记点赞
* */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteLikeDO {
    private Long id;

    private Long userId;

    private Long noteId;

    private LocalDateTime createTime;

    private Long status;

}