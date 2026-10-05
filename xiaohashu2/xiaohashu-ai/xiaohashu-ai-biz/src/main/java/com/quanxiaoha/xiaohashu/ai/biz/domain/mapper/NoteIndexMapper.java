package com.quanxiaoha.xiaohashu.ai.biz.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteIndexDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记索引镜像 Mapper
 */
@Mapper
public interface NoteIndexMapper extends BaseMapper<NoteIndexDO> {

    /**
     * 按 noteId 查询索引记录
     */
    default NoteIndexDO selectByNoteId(Long noteId) {
        LambdaQueryWrapper<NoteIndexDO> wrapper = Wrappers.<NoteIndexDO>lambdaQuery()
                .eq(NoteIndexDO::getNoteId, noteId)
                .last("limit 1");
        return selectOne(wrapper);
    }
}