package com.quanxiaoha.xiaohashu.note.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.TopicDO;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TopicDOMapper {

    /*
    * 根据主键id查询话题名称
    * */
    String selectNameByPrimaryKey(Long id);

    int deleteByPrimaryKey(Long id);

    int insert(TopicDO record);

    int insertSelective(TopicDO record);

    TopicDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TopicDO record);

    int updateByPrimaryKey(TopicDO record);

    /**
     * 根据关键词模糊查询话题
     */
    List<TopicDO> selectByKeyword(@Param("keyword") String keyword);
}
