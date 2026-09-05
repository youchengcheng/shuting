package com.quanxiaoha.ai.robot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanxiaoha.ai.robot.domain.dos.ChatDO;
import com.quanxiaoha.ai.robot.domain.dos.ChatMessageDO;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.ai.robot.enums.ResponseCodeEnum;
import com.quanxiaoha.ai.robot.exception.BizException;
import com.quanxiaoha.ai.robot.model.vo.chat.*;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMapper;
import com.quanxiaoha.ai.robot.service.ChatService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import com.quanxiaoha.ai.robot.utils.StringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
* 会话实现类
* */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private ChatMapper chatMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    /*
    * 新建会话
    * */
    @Override
    public Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO) {
        //用户发送的消息
        String message = newChatReqVO.getMessage();

        //生成对话uuid
        String uuid = UUID.randomUUID().toString();

        //截取用户发送的消息，作为对话摘要
        String summery = StringUtil.truncate(message, 20);

        //存储会话记录到数据库中
        chatMapper.insert(ChatDO.builder()
                .uuid(uuid)
                .summary(summery)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build()
        );

        //将摘要，uuid 返回给前端
        return Response.success(NewChatRspVO.builder()
                .summary(summery)
                .uuid(uuid)
                .build()
        );
    }

    /*
    * 分页查询历史对话
    * */
    @Override
    public PageResponse<FindChatHistoryMessagePageListRspVO> findChatHistoryMessagePageList(
            FindChatHistoryMessagePageListReqVO findChatHistoryMessagePageListReqVO) {
        //获取当前页，以及每页需要展示的数据数量
        Long current = findChatHistoryMessagePageListReqVO.getCurrent();
        Long size = findChatHistoryMessagePageListReqVO.getSize();
        String chatId = findChatHistoryMessagePageListReqVO.getChatId();

        //执行分页查询
        Page<ChatMessageDO> chatMessageDOPage = chatMessageMapper.selectPageList(current, size, chatId);

        List<ChatMessageDO> chatMessageDOS = chatMessageDOPage.getRecords();
        //DO转VO
        List<FindChatHistoryMessagePageListRspVO> collect = null;
        if(CollUtil.isNotEmpty(chatMessageDOS)) {
             collect = chatMessageDOS.stream()
                    .map(chatMessageDO -> FindChatHistoryMessagePageListRspVO.builder()
                            .id(chatMessageDO.getId())
                            .chatId(chatMessageDO.getChatUuid())
                            .content(chatMessageDO.getContent())
                            .role(chatMessageDO.getRole())
                            .reasoning(chatMessageDO.getReasoningContent())
                            .createTime(chatMessageDO.getCreateTime())
                            .build())
                    .sorted(Comparator.comparing(FindChatHistoryMessagePageListRspVO::getCreateTime))
                    .collect(Collectors.toList());
        }


        return PageResponse.success(chatMessageDOPage,collect);
    }

    /*
    * 查询历史对话
    * */
    @Override
    public PageResponse<FindChatHistoryPageListRspVO> findChatHistoryPageList(
            FindChatHistoryPageListReqVO findChatHistoryPageListReqVO) {
        //获取当前页，以及每页需要展示的数据数量
        Long current = findChatHistoryPageListReqVO.getCurrent();
        Long size = findChatHistoryPageListReqVO.getSize();

        //执行分页查询
        Page<ChatDO> chatDOPage = chatMapper.selectPageList(current, size);

        //获取查询结果
        List<ChatDO> records = chatDOPage.getRecords();

        //DO转VO
        List<FindChatHistoryPageListRspVO> vos = null;
        if (CollUtil.isNotEmpty(records)) {
            vos = records.stream()
                    .map(chatDO -> FindChatHistoryPageListRspVO.builder() // 构建返参 VO
                            .id(chatDO.getId())
                            .uuid(chatDO.getUuid())
                            .summary(chatDO.getSummary())
                            .updateTime(chatDO.getUpdateTime())
                            .build())
                    .collect(Collectors.toList());
        }

        return PageResponse.success(chatDOPage,vos);
    }

    /**
     * 重命名对话摘要
     */
    @Override
    public Response<?> renameChatSummary(RenameChatReqVO renameChatReqVO) {
        //对话id
        Long id = renameChatReqVO.getId();
        //摘要
        String summary = renameChatReqVO.getSummary();

        //根据主键id更新摘要
        chatMapper.updateById(ChatDO.builder()
                .id(id)
                .summary(summary)
                .updateTime(LocalDateTime.now())
                .build());

        return Response.success();
    }

    /**
     * 删除对话
     */
    @Override
    public Response<?> deleteChat(DeleteChatReqVO deleteChatReqVO) {
        //对话uuid
        String uuid = deleteChatReqVO.getUuid();

        Response<Object> response = transactionTemplate.execute(status -> {
            try {
                //删除对话
                int count = chatMapper.delete(Wrappers.<ChatDO>lambdaQuery()
                        .eq(ChatDO::getUuid, uuid));

                //如果删除影响的行数为0，说明删除的会话不存在，抛出异常
                if (count == 0) {
                    throw new BizException(ResponseCodeEnum.CHAT_NOT_EXISTED);
                }

                //批量删除对话下的所有消息
                chatMessageMapper.delete(Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getChatUuid, uuid));
                return Response.success();
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("", e);
                return Response.fail();
            }
        });
        return response;
    }
}
