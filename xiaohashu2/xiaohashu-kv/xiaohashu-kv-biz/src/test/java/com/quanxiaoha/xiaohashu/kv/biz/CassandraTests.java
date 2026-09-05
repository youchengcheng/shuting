package com.quanxiaoha.xiaohashu.kv.biz;

import com.quanxiaoha.framework.common.util.JsonUtils;
import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.NoteContentDO;
import com.quanxiaoha.xiaohashu.kv.biz.domain.repository.NoteContentRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Slf4j
public class CassandraTests {

    @Resource
    private NoteContentRepository noteContentRepository;

    //新增
    @Test
    void testInsert(){
        NoteContentDO contentDO = NoteContentDO.builder()
                .id(UUID.randomUUID())
                .content("测试笔记插入")
                .build();
        noteContentRepository.save(contentDO);
    }

    //修改
    @Test
    void testUpdate() {
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.fromString("adf1c28b-b491-4f82-992d-888f0de440b9"))
                .content("代码测试笔记内容更新")
                .build();

        noteContentRepository.save(nodeContent);
    }

    //查看
    @Test
    void testSelect() {
        Optional<NoteContentDO> optional =
                noteContentRepository.findById(UUID
                        .fromString("adf1c28b-b491-4f82-992d-888f0de440b9"));
        optional.ifPresent(noteContentDO ->
                        log.info("查询结果：{}", JsonUtils.toJsonString(noteContentDO)));
    }

    //删除
    @Test
    void testDelete() {
        noteContentRepository.deleteById(UUID.fromString("adf1c28b-b491-4f82-992d-888f0de440b9"));
    }



}
