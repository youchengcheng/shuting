package com.quanxiaoha.xiaohashu.kv.biz.domain.repository;

import com.quanxiaoha.xiaohashu.kv.biz.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;


/*
* CassandraRepository <操作的实体类，主键类型>，操作的实体类：就是操作那张表
* */
public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {

}
