package com.quanxiaoha.xiaohashu.note.biz.service.impl;

import com.quanxiaoha.xiaohashu.note.biz.domain.dataobject.NoteDO;
import com.quanxiaoha.xiaohashu.note.biz.domain.mapper.NoteDOMapper;
import com.quanxiaoha.xiaohashu.note.biz.service.NoteInternalQueryService;
import com.quanxiaoha.xiaohashu.note.biz.util.NoteAccessUtils;
import com.quanxiaoha.xiaohashu.note.dto.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

/*
 * 笔记内部查询服务实现
 * */
@Service
public class NoteInternalQueryServiceImpl implements NoteInternalQueryService {

    @Resource
    private NoteDOMapper noteDOMapper;

    /*
     * 批量查询元数据
     * */
    @Override
    public List<NoteMetadataDTO> metadata(NoteBatchQueryReqDTO noteBatchQueryReqDTO) {
        // 批量读取笔记元数据并判断访问权限，不返回笔记正文或图片内容
        return find(noteBatchQueryReqDTO).stream().map(note -> NoteMetadataDTO.builder()
                .id(note.getId())
                .creatorId(note.getCreatorId())
                .status(note.getStatus())
                .visible(note.getVisible())
                .accessible(NoteAccessUtils.canRead(note, noteBatchQueryReqDTO.getViewerId()))
                .build()).toList();
    }

    /*
     * 批量查询预览信息
     * */
    @Override
    public List<NotePreviewDTO> previews(NoteBatchQueryReqDTO noteBatchQueryReqDTO) {
        // 查询笔记并按访问者权限组装预览，私密或失效笔记不返回标题和封面
        return find(noteBatchQueryReqDTO).stream().map(note -> {
            // 私密笔记仅向有权限的用户返回预览
            boolean readable = NoteAccessUtils.canRead(note, noteBatchQueryReqDTO.getViewerId());
            String cover = null;
            if (readable && note.getImgUris() != null && !note.getImgUris().isBlank()) {
                // 使用笔记图片列表中的第一张作为通知封面
                cover = note.getImgUris().split(",", 2)[0];
            }
            return NotePreviewDTO.builder()
                    .id(note.getId())
                    .accessible(readable)
                    .title(readable ? note.getTitle() : null)
                    .cover(cover)
                    .build();
        }).toList();
    }

    /*
     * 过滤查询 ID 并批量查询数据
     * */
    private List<NoteDO> find(NoteBatchQueryReqDTO noteBatchQueryReqDTO) {
        // 限制单次查询数量，防止内部预览接口一次查询过多笔记
        if (noteBatchQueryReqDTO == null || noteBatchQueryReqDTO.getIds() == null || noteBatchQueryReqDTO.getIds().size() > 100) {
            throw new IllegalArgumentException("笔记查询参数缺失或 ID 数量超过 100 条");
        }
        // 过滤无效 ID 并去重，过滤后为空时不访问数据库
        List<Long> ids = noteBatchQueryReqDTO.getIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        return ids.isEmpty() ? Collections.emptyList() : noteDOMapper.selectByIds(ids);
    }

}
