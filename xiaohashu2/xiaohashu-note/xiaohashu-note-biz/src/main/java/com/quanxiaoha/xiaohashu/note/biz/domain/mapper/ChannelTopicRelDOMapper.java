package com.quanxiaoha.xiaohashu.note.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.ChannelTopicRelDO;
import org.apache.ibatis.annotations.Param;

public interface ChannelTopicRelDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(ChannelTopicRelDO record);

    int insertSelective(ChannelTopicRelDO record);

    ChannelTopicRelDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(ChannelTopicRelDO record);

    int updateByPrimaryKey(ChannelTopicRelDO record);

    ChannelTopicRelDO selectByChannelIdAndTopicId(@Param("channelId") Long channelId,
                                                  @Param("topicId") Long topicId);
}
