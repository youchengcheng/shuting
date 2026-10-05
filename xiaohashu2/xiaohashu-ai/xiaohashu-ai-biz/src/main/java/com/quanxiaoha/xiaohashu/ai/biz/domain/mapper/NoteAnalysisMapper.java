package com.quanxiaoha.xiaohashu.ai.biz.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.NoteAnalysisDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记解读结果 Mapper
 */
@Mapper
public interface NoteAnalysisMapper extends BaseMapper<NoteAnalysisDO> {
}