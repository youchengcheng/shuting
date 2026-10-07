package com.quanxiaoha.xiaohashu.ai.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.xiaohashu.ai.biz.constant.AiConstants;
import com.quanxiaoha.xiaohashu.ai.biz.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.ChatDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.dataobject.ChatMessageDO;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.ChatMapper;
import com.quanxiaoha.xiaohashu.ai.biz.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.xiaohashu.ai.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.ai.biz.model.dto.AssistantAnswerDTO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.AssistantSearchRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatMessageRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatReqVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.ChatRspVO;
import com.quanxiaoha.xiaohashu.ai.biz.model.vo.NoteRefVO;
import com.quanxiaoha.xiaohashu.ai.biz.service.AssistantService;
import com.quanxiaoha.xiaohashu.ai.biz.service.ChatService;
import com.quanxiaoha.xiaohashu.ai.biz.util.AiStringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 对话服务实现
 *
 * <p>会话消息全部落 MySQL（t_ai_chat / t_ai_chat_message），
 * 多轮上下文取最近若干条拼进 prompt，避免上下文无限增长。</p>
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    /**
     * 参与上下文的历史消息条数
     */
    private static final int HISTORY_SIZE = 6;

    /**
     * 单条历史消息最大字符数
     */
    private static final int HISTORY_MESSAGE_MAX_CHARS = 300;

    /**
     * 历史会话标题最大字符数：仅截取用户首轮提问前 15 个字符作为标题
     */
    private static final int CHAT_TITLE_MAX_CHARS = 15;

    @Resource
    private ChatMapper chatMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private AssistantService assistantService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public AssistantSearchRspVO chat(Long userId, ChatReqVO reqVO) {
        ChatDO chat = resolveChat(userId, reqVO.getChatUuid(), reqVO.getQuery());
        String historyText = buildHistoryText(chat.getUuid());

        saveMessage(chat.getUuid(), userId, AiConstants.ROLE_USER, reqVO.getQuery(), null);

        AssistantAnswerDTO answer = assistantService.answer(reqVO.getQuery(), historyText);

        saveMessage(chat.getUuid(), userId, AiConstants.ROLE_ASSISTANT, answer.getAnswer(), toNoteIds(answer.getNotes()));
        touchChat(chat.getId(), reqVO.getQuery());

        return AssistantSearchRspVO.builder()
                .answer(answer.getAnswer())
                .notes(answer.getNotes())
                .chatUuid(chat.getUuid())
                .fromNotes(answer.getFromNotes())
                .build();
    }

    @Override
    public Flux<String> chatStream(Long userId, ChatReqVO reqVO) {
        ChatDO chat = resolveChat(userId, reqVO.getChatUuid(), reqVO.getQuery());
        String historyText = buildHistoryText(chat.getUuid());
        saveMessage(chat.getUuid(), userId, AiConstants.ROLE_USER, reqVO.getQuery(), null);

        StringBuilder buffer = new StringBuilder();
        return assistantService.answerStream(reqVO.getQuery(), historyText)
                .doOnNext(buffer::append)
                .doOnComplete(() -> {
                    try {
                        saveMessage(chat.getUuid(), userId, AiConstants.ROLE_ASSISTANT, buffer.toString(), null);
                        touchChat(chat.getId(), reqVO.getQuery());
                    } catch (Exception e) {
                        log.error("## 流式回答落库失败, chatUuid: {}", chat.getUuid(), e);
                    }
                })
                .onErrorResume(e -> Flux.just("[出错了] " + resolveErrorMessage(e)));
    }

    @Override
    public List<ChatRspVO> listChats(Long userId) {
        // 列表数据只走 Redis 缓存（仅存摘要，不存会话消息内容）
        String key = RedisKeyConstants.buildChatListKey(userId);
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("## 读取历史会话列表缓存失败, key: {}", key, e);
        }
        if (AiStringUtils.isNotBlank(cached)) {
            try {
                return JSONUtil.toList(cached, ChatRspVO.class);
            } catch (Exception e) {
                log.warn("## 历史会话列表缓存反序列化失败, key: {}, 退回数据库查询", key, e);
            }
        }

        // 缓存未命中：查 MySQL，回写 Redis
        List<ChatDO> chats = chatMapper.selectList(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUserId, userId)
                .orderByDesc(ChatDO::getUpdateTime)
                .last("limit 50"));
        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }
        List<ChatRspVO> result = chats.stream()
                .map(chat -> ChatRspVO.builder()
                        .chatUuid(chat.getUuid())
                        .title(chat.getTitle())
                        .updateTime(chat.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result),
                    Duration.ofSeconds(RedisKeyConstants.AI_CHAT_LIST_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("## 写入历史会话列表缓存失败, key: {}", key, e);
        }
        return result;
    }

    /**
     * 失效某用户的历史会话列表缓存
     * <p>新增会话 / 更新会话时间 / 删除会话后调用</p>
     */
    private void evictChatListCache(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyConstants.buildChatListKey(userId));
        } catch (Exception e) {
            log.warn("## 失效历史会话列表缓存失败, userId: {}", userId, e);
        }
    }

    @Override
    public List<ChatMessageRspVO> listMessages(Long userId, String chatUuid) {
        requireOwnedChat(userId, chatUuid);
        List<ChatMessageDO> messages = chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getChatUuid, chatUuid)
                .orderByAsc(ChatMessageDO::getId)
                .last("limit 100"));
        if (CollUtil.isEmpty(messages)) {
            return Collections.emptyList();
        }
        return messages.stream()
                .map(message -> ChatMessageRspVO.builder()
                        .role(message.getRole())
                        .content(message.getContent())
                        .noteRefs(parseNoteIds(message.getNoteRefs()))
                        .createTime(message.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteChat(Long userId, String chatUuid) {
        ChatDO chat = requireOwnedChat(userId, chatUuid);
        chatMessageMapper.delete(Wrappers.<ChatMessageDO>lambdaQuery().eq(ChatMessageDO::getChatUuid, chatUuid));
        chatMapper.deleteById(chat.getId());
        // 删除会话后失效该用户的历史会话列表缓存
        evictChatListCache(userId);
    }

    private ChatDO resolveChat(Long userId, String chatUuid, String query) {
        if (AiStringUtils.isNotBlank(chatUuid)) {
            return requireOwnedChat(userId, chatUuid);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatDO chat = ChatDO.builder()
                .uuid(UUID.randomUUID().toString())
                .userId(userId)
                .title(AiStringUtils.truncate(query, CHAT_TITLE_MAX_CHARS))
                .createTime(now)
                .updateTime(now)
                .build();
        if (chatMapper.insert(chat) <= 0) {
            throw new BizException(ResponseCodeEnum.CHAT_CREATE_FAIL);
        }
        // 新建会话后失效该用户的历史会话列表缓存
        evictChatListCache(userId);
        return chat;
    }

    private ChatDO requireOwnedChat(Long userId, String chatUuid) {
        ChatDO chat = chatMapper.selectOne(Wrappers.<ChatDO>lambdaQuery()
                .eq(ChatDO::getUuid, chatUuid)
                .eq(ChatDO::getUserId, userId)
                .last("limit 1"));
        if (chat == null) {
            throw new BizException(ResponseCodeEnum.CHAT_NOT_EXISTED);
        }
        return chat;
    }

    private void saveMessage(String chatUuid, Long userId, String role, String content, List<String> noteIds) {
        if (AiStringUtils.isBlank(content)) {
            return;
        }
        chatMessageMapper.insert(ChatMessageDO.builder()
                .chatUuid(chatUuid)
                .userId(userId)
                .role(role)
                .content(content)
                .noteRefs(CollUtil.isEmpty(noteIds) ? null : JSONUtil.toJsonStr(noteIds))
                .createTime(LocalDateTime.now())
                .build());
    }

    private void touchChat(Long chatId, String query) {
        ChatDO chat = chatMapper.selectById(chatId);
        if (chat == null) {
            return;
        }
        ChatDO update = ChatDO.builder()
                .id(chatId)
                .updateTime(LocalDateTime.now())
                .build();
        if (AiStringUtils.isBlank(chat.getTitle())) {
            update.setTitle(AiStringUtils.truncate(query, CHAT_TITLE_MAX_CHARS));
        }
        chatMapper.updateById(update);
        // 会话时间被刷新（列表按 updateTime 倒序），失效该用户历史会话列表缓存
        evictChatListCache(chat.getUserId());
    }

    private String buildHistoryText(String chatUuid) {
        List<ChatMessageDO> messages = chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getChatUuid, chatUuid)
                .orderByDesc(ChatMessageDO::getId)
                .last("limit " + HISTORY_SIZE));
        if (CollUtil.isEmpty(messages)) {
            return null;
        }
        Collections.reverse(messages);
        StringBuilder sb = new StringBuilder();
        for (ChatMessageDO message : messages) {
            String role = AiConstants.ROLE_USER.equals(message.getRole()) ? "用户" : "助手";
            sb.append(role).append("：")
                    .append(AiStringUtils.truncate(message.getContent(), HISTORY_MESSAGE_MAX_CHARS))
                    .append('\n');
        }
        return sb.toString();
    }

    private List<String> toNoteIds(List<NoteRefVO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return Collections.emptyList();
        }
        return notes.stream().map(NoteRefVO::getNoteId).collect(Collectors.toList());
    }

    private List<String> parseNoteIds(String noteRefs) {
        if (AiStringUtils.isBlank(noteRefs)) {
            return Collections.emptyList();
        }
        try {
            return new ArrayList<>(JSONUtil.parseArray(noteRefs).toList(String.class));
        } catch (Exception e) {
            log.warn("## 解析 noteRefs 失败: {}", noteRefs);
            return Collections.emptyList();
        }
    }

    private String resolveErrorMessage(Throwable e) {
        if (e instanceof BizException) {
            return ((BizException) e).getErrorMessage();
        }
        return ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage();
    }
}
