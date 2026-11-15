package com.quanxiaoha.xiaohashu.note.biz.util;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;

import java.util.Objects;

/*
 * 笔记访问权限工具类
 * */
public class NoteAccessUtils {

    /*
     * 判断当前用户是否可查看笔记
     * */
    public static boolean canRead(NoteDO note, Long viewerId) {
        // 笔记须为正常状态；公开笔记允许访问，私密笔记仅允许作者本人访问
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), 0)
                    || (Objects.equals(note.getVisible(), 1) && viewerId != null && Objects.equals(note.getCreatorId(), viewerId)));
    }
}
