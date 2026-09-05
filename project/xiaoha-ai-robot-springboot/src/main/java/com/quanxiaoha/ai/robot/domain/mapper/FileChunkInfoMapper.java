package com.quanxiaoha.ai.robot.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanxiaoha.ai.robot.domain.dos.FileChunkInfoDO;

import java.util.List;

/**
 * 分片信息表
 **/
public interface FileChunkInfoMapper extends BaseMapper<FileChunkInfoDO> {

    /*
    * 根据文件 MD5 值查询所有已上传的分片
    * */
    default List<FileChunkInfoDO> selectChunkedList(String fileMd5) {
        return selectList(
                Wrappers.<FileChunkInfoDO>lambdaQuery()
                        .eq(FileChunkInfoDO::getFileMd5,fileMd5)
                        .orderByDesc(FileChunkInfoDO::getCreateTime)
        );
    }

    /*
    * 查询指定分片是否已被上传
    * */
    default Long selectCountByMd5AndChunkNum(String fileMd5,Integer chunkNum) {
        return selectCount(Wrappers.<FileChunkInfoDO>lambdaQuery()
                .eq(FileChunkInfoDO::getFileMd5,fileMd5)
                .eq(FileChunkInfoDO::getChunkNumber,chunkNum));
    }

    /*
    * 根据文件 MD5 删除记录
    * */
    default int deleteByMd5(String fileMd5) {
        return delete(Wrappers.<FileChunkInfoDO>lambdaQuery()
                .eq(FileChunkInfoDO::getFileMd5,fileMd5));
    }
}

