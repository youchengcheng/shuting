package com.quanxiaoha.xiaohashu.ai.biz.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}