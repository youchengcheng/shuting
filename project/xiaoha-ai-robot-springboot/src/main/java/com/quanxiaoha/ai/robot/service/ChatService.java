package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.vo.chat.*;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;

/*
* 对话
* */
public interface ChatService {

    /*
    * 新建会话
    * */
    Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO);

    /**
     * 查询历史消息
     */
    PageResponse<FindChatHistoryMessagePageListRspVO> findChatHistoryMessagePageList(
            FindChatHistoryMessagePageListReqVO findChatHistoryMessagePageListReqVO);

    /**
     * 查询历史对话
     */
    PageResponse<FindChatHistoryPageListRspVO> findChatHistoryPageList(
            FindChatHistoryPageListReqVO findChatHistoryPageListReqVO);

    /**
     * 重命名对话摘要
     */
    Response<?> renameChatSummary(RenameChatReqVO renameChatReqVO);

    /**
     * 删除对话
     */
    Response<?> deleteChat(DeleteChatReqVO deleteChatReqVO);

}
