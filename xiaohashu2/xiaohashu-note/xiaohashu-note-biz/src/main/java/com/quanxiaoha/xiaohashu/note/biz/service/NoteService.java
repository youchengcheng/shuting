package com.quanxiaoha.xiaohashu.note.biz.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.*;

/*
* 笔记业务
* */
public interface NoteService {

    /*
    * 笔记发布
    * */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    /*
    * 根据笔记id查看笔记
    * */
    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    /*
    * 笔记更新
    * */
    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    /*
    * 删除本地笔记缓存
    * */
    void deleteNoteLocalCache(Long noteId);

    /*
    * 删除笔记
    * */
    Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

    /*
    * 笔记仅自己可见
    * */
    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO);

    /*
    * 笔记置顶
    * */
    Response<?> topNote(TopNoteReqVO topNoteReqVO);

    /*
    * 笔记点赞
    * */
    Response<?> lickNote(LikeNoteReqVO likeNoteReqVO);

    /*
    * 取消点赞笔记
    * */
    Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO);

    /**
     * 收藏笔记
     */
    Response<?> collectNote(CollectNoteReqVO collectNoteReqVO);

    /**
     * 取消收藏笔记
     */
    Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO);

    /**
     * 获取是否点赞、收藏数据
     */
    Response<FindNoteIsLikedAndCollectedRspVO> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReqVO findNoteIsLikedAndCollectedReqVO);

}
