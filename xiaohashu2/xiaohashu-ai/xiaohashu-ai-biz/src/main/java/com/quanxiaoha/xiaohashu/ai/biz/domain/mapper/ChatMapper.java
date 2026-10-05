package com.quanxiaoha.xiaohashu.ai.biz.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.ChatDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper
 */
@Mapper
public interface ChatMapper extends BaseMapper<ChatDO> {
}