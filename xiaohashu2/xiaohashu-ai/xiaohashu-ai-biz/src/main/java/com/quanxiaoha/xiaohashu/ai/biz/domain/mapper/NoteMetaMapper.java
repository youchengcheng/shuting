package com.quanxiaoha.xiaohashu.ai.biz.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteMetaDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 笔记元数据 Mapper（只读）
 */
@Mapper
public interface NoteMetaMapper extends BaseMapper<NoteMetaDO> {

    /**
     * 可被检索的笔记条件：正常展示 + 公开 + 有正文
     */
    default LambdaQueryWrapper<NoteMetaDO> indexableWrapper() {
        return Wrappers.<NoteMetaDO>lambdaQuery()
                .eq(NoteMetaDO::getStatus, 1)
                .eq(NoteMetaDO::getVisible, 0)
                .eq(NoteMetaDO::getIsContentEmpty, Boolean.FALSE)
                .isNotNull(NoteMetaDO::getContentUuid)
                .orderByAsc(NoteMetaDO::getId);
    }

    /**
     * 分页扫描可检索笔记（全量重建用）
     */
    default List<NoteMetaDO> selectIndexablePage(long offset, long limit) {
        return selectList(indexableWrapper().last("limit " + offset + "," + limit));
    }

    /**
     * 可检索笔记总数
     */
    default Long countIndexable() {
        return selectCount(indexableWrapper());
    }

    /**
     * 查询单篇笔记的元数据（不限制状态，由业务层判断）
     */
    default NoteMetaDO selectNoteById(Long noteId) {
        return selectById(noteId);
    }
}