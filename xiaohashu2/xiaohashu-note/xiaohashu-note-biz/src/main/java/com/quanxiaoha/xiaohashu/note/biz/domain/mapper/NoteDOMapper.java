package com.quanxiaoha.xiaohashu.note.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    NoteDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    /*
    * 笔记仅自己可见
    * */
    int updateVisibleOnlyMe(NoteDO noteDO);

    /*
    * 笔记置顶
    * */
    int updateIsTop(NoteDO noteDO);

    /*
    * 判断笔记是否存在
    * */
    int selectCountByNoteId(Long noteId);

    /**
     * 查询笔记的发布者用户 ID
     */
    Long selectCreatorIdByNoteId(Long noteId);

    /**
     * 分页查询公开笔记（发现页）
     */
    List<NoteDO> selectDiscoverPage(@Param("channelId") Long channelId,
                                    @Param("offset") long offset,
                                    @Param("limit") long limit);

    /**
     * 统计公开笔记总数（发现页）
     */
    long countDiscover(@Param("channelId") Long channelId);

    /**
     * 分页查询用户笔记列表（个人主页：1-发布的笔记，2-点赞的笔记，3-收藏的笔记）
     */
    List<NoteDO> selectProfilePage(@Param("type") Integer type,
                                   @Param("userId") Long userId,
                                   @Param("currentUserId") Long currentUserId,
                                   @Param("offset") long offset,
                                   @Param("limit") long limit);

    /**
     * 统计用户笔记总数（个人主页）
     */
    long countProfile(@Param("type") Integer type,
                      @Param("userId") Long userId,
                      @Param("currentUserId") Long currentUserId);

    /**
     * 批量查询笔记计数（点赞/收藏/评论数）
     */
    List<com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCountDO> selectCountByNoteIds(@Param("noteIds") List<Long> noteIds);


    /**
     * 查询个人主页已发布笔记列表
     */
    List<NoteDO> selectPublishedNoteListByUserIdAndCursor(@Param("creatorId") Long creatorId,
                                                          @Param("cursor") Long cursor);
}
