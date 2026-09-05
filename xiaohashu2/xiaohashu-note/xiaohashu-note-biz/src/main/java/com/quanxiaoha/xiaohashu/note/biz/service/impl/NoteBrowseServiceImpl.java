package com.quanxiaoha.xiaohashu.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.ChannelDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteCountDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.TopicDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.ChannelDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.TopicDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindChannelRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindDiscoverNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindNoteCardRspVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindProfileNotePageListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicListReqVO;
import com.quanxiaoha.xiaohashu.note.biz.model.vo.FindTopicRspVO;
import com.quanxiaoha.xiaohashu.note.biz.rpc.UserRpcService;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteBrowseService;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记浏览业务实现
 **/
@Service
@Slf4j
public class NoteBrowseServiceImpl implements NoteBrowseService {

    /**
     * 每页展示的笔记数
     */
    private static final long PAGE_SIZE = 10L;

    @Resource
    private ChannelDOMapper channelDOMapper;

    @Resource
    private TopicDOMapper topicDOMapper;

    @Resource
    private NoteDOMapper noteDOMapper;

    @Resource
    private UserRpcService userRpcService;

    @Override
    public Response<List<FindChannelRspVO>> listChannels() {
        List<ChannelDO> channels = channelDOMapper.selectAll();

        List<FindChannelRspVO> resultVOS = Lists.newArrayList();
        if (CollUtil.isNotEmpty(channels)) {
            for (ChannelDO channel : channels) {
                resultVOS.add(FindChannelRspVO.builder()
                        .id(channel.getId())
                        .name(channel.getName())
                        .build());
            }
        }
        return Response.success(resultVOS);
    }

    @Override
    public PageResponse<FindNoteCardRspVO> findDiscoverNotePageList(FindDiscoverNotePageListReqVO reqVO) {
        int pageNo = resolvePageNo(reqVO.getPageNo());
        long offset = (pageNo - 1) * PAGE_SIZE;

        Long channelId = reqVO.getChannelId();
        if (Objects.nonNull(channelId) && channelId <= 0) {
            channelId = null;
        }

        List<NoteDO> notes = noteDOMapper.selectDiscoverPage(channelId, offset, PAGE_SIZE);
        long totalCount = noteDOMapper.countDiscover(channelId);

        List<FindNoteCardRspVO> cardVOS = buildCardVOS(notes);
        return PageResponse.success(cardVOS, pageNo, totalCount, PAGE_SIZE);
    }

    @Override
    public PageResponse<FindNoteCardRspVO> findProfileNotePageList(FindProfileNotePageListReqVO reqVO) {
        int pageNo = resolvePageNo(reqVO.getPageNo());

        Integer type = reqVO.getType();
        Long userId = reqVO.getUserId();
        // 类型或用户 ID 不合法，直接返回空数据
        if (Objects.isNull(type) || type < 1 || type > 3 || Objects.isNull(userId)) {
            return PageResponse.success(Lists.newArrayList(), pageNo, 0, PAGE_SIZE);
        }

        long offset = (pageNo - 1) * PAGE_SIZE;
        // 当前登录用户 ID（未登录时为 null，用于过滤“仅自己可见”的笔记）
        Long currentUserId = LoginUserContextHolder.getUserId();

        List<NoteDO> notes = noteDOMapper.selectProfilePage(type, userId, currentUserId, offset, PAGE_SIZE);
        long totalCount = noteDOMapper.countProfile(type, userId, currentUserId);

        List<FindNoteCardRspVO> cardVOS = buildCardVOS(notes);
        return PageResponse.success(cardVOS, pageNo, totalCount, PAGE_SIZE);
    }

    @Override
    public Response<List<FindTopicRspVO>> findTopicList(FindTopicListReqVO reqVO) {
        String keyword = Objects.isNull(reqVO) ? null : reqVO.getKeyword();

        List<TopicDO> topics = topicDOMapper.selectByKeyword(keyword);

        List<FindTopicRspVO> resultVOS = Lists.newArrayList();
        if (CollUtil.isNotEmpty(topics)) {
            for (TopicDO topic : topics) {
                resultVOS.add(FindTopicRspVO.builder()
                        .id(topic.getId())
                        .name(topic.getName())
                        .build());
            }
        }
        return Response.success(resultVOS);
    }

    /**
     * 将笔记 DO 列表组装为卡片 VO 列表（补全发布者昵称头像、点赞数）
     */
    private List<FindNoteCardRspVO> buildCardVOS(List<NoteDO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return Lists.newArrayList();
        }

        // 批量查询发布者信息
        List<Long> creatorIds = notes.stream()
                .map(NoteDO::getCreatorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, FindUserByIdRspDTO> userMap = userRpcService.findByIds(creatorIds);

        // 批量查询笔记计数
        List<Long> noteIds = notes.stream().map(NoteDO::getId).toList();
        Map<Long, NoteCountDO> countMap = noteDOMapper.selectCountByNoteIds(noteIds).stream()
                .collect(Collectors.toMap(NoteCountDO::getNoteId, Function.identity()));

        List<FindNoteCardRspVO> cardVOS = Lists.newArrayList();
        for (NoteDO note : notes) {
            FindNoteCardRspVO.FindNoteCardRspVOBuilder builder = FindNoteCardRspVO.builder()
                    .id(note.getId())
                    .type(note.getType())
                    .title(note.getTitle())
                    .videoUri(note.getVideoUri())
                    .creatorId(note.getCreatorId())
                    .visible(note.getVisible());

            // 封面：图文笔记取第一张图
            if (Objects.equals(note.getType(), 0) && StringUtils.isNotBlank(note.getImgUris())) {
                builder.cover(note.getImgUris().split(",")[0]);
            }

            FindUserByIdRspDTO user = userMap.get(note.getCreatorId());
            if (Objects.nonNull(user)) {
                builder.nickname(user.getNickName());
                builder.avatar(user.getAvatar());
            } else {
                builder.nickname(StringUtils.EMPTY);
            }

            NoteCountDO noteCountDO = countMap.get(note.getId());
            builder.likeTotal(Objects.isNull(noteCountDO) || Objects.isNull(noteCountDO.getLikeTotal())
                    ? 0L : noteCountDO.getLikeTotal());

            cardVOS.add(builder.build());
        }
        return cardVOS;
    }

    /**
     * 解析页码
     */
    private int resolvePageNo(Integer pageNo) {
        return (Objects.isNull(pageNo) || pageNo < 1) ? 1 : pageNo;
    }
}
