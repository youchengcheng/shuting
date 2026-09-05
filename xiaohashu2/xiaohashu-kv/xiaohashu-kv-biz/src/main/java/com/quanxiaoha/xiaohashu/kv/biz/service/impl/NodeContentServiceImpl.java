package com.quanxiaoha.xiaohashu.kv.biz.service.impl;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.NoteContentDO;
import com.quanxiaoha.xiaohashu.kv.biz.domain.repository.NoteContentRepository;
import com.quanxiaoha.xiaohashu.kv.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.kv.biz.service.NoteContentService;
import com.quanxiaoha.xiaohashu.kv.dto.rep.AddNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/*
 * 笔记内容存储业务
 * */
@Slf4j
@Service
public class NodeContentServiceImpl implements NoteContentService {

    @Resource
    private NoteContentRepository noteContentRepository;

    /*
    * 新增笔记
    * */
    @Override
    public Response<?> AddNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {

        //获取前端传递的数据
        String uuid = addNoteContentReqDTO.getUuid();
        String content = addNoteContentReqDTO.getContent();

        //构建实体类，用于存储
        NoteContentDO contentDO = NoteContentDO.builder()
                .id(UUID.fromString(uuid))
                .content(content)
                .build();

        //添加到Cassandra
        noteContentRepository.save(contentDO);

        return Response.success();
    }

    /*
     * 查询笔记
     * */
    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {

        //获取前端传递过来的笔记id
        String uuid = findNoteContentReqDTO.getUuid();

        //根据笔记id进行查询
        Optional<NoteContentDO> noteContentDO = noteContentRepository.findById(UUID.fromString(uuid));

        //判断查询结果是否为空，为空抛出异常
        if(!noteContentDO.isPresent()){
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_FOUND);
        }

        //不为空，构建实体类返回给前端
        NoteContentDO contentDO = noteContentDO.get();//获取笔记内容
        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                .noteId(contentDO.getId())
                .content(contentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    /*
     * 删除笔记
     * */
    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {

        //获取笔记id
        String uuid = deleteNoteContentReqDTO.getUuid();

        //删除笔记
        noteContentRepository.deleteById(UUID.fromString(uuid));

        return Response.success();
    }
}
