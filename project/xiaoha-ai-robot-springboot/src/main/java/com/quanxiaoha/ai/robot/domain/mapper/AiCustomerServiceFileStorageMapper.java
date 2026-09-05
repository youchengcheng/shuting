package com.quanxiaoha.ai.robot.domain.mapper;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanxiaoha.ai.robot.domain.dos.AiCustomerServiceFileStorageDO;

import java.time.LocalDate;
import java.util.Objects;

public interface AiCustomerServiceFileStorageMapper extends BaseMapper<AiCustomerServiceFileStorageDO> {

    /*
    * 分页查询
    * */
    default Page<AiCustomerServiceFileStorageDO> selectPageList(Long current, Long size, String fileName, LocalDate startDate, LocalDate endTime) {
        //分页对象
        Page<AiCustomerServiceFileStorageDO> page = new Page<>(current, size);

        //分页条件
        //Objects.nonNull(startDate) 条件
        //AiCustomerServiceMdStorageDO::getCreateTime 要比较的数据库字段
        //startDate 目标字段
        LambdaQueryWrapper<AiCustomerServiceFileStorageDO> wrapper = Wrappers.<AiCustomerServiceFileStorageDO>lambdaQuery()
                .like(StringUtils.isNotBlank(fileName), AiCustomerServiceFileStorageDO::getFileName,fileName) //like模糊查询
                .ge(Objects.nonNull(startDate), AiCustomerServiceFileStorageDO::getCreateTime,startDate) //大于等于starttime
                .le(Objects.nonNull(endTime), AiCustomerServiceFileStorageDO::getCreateTime,endTime) //小于等于 endtime
                .orderByDesc(AiCustomerServiceFileStorageDO::getCreateTime);//按创建时间升序排列

        //开始分页查询
        return selectPage(page,wrapper);

    }

    /*
    * 根据文件 MD5 值查询
    * */
    default AiCustomerServiceFileStorageDO selectByMd5(String fileMd5){
        return selectOne(Wrappers.<AiCustomerServiceFileStorageDO>lambdaQuery()
                .eq(AiCustomerServiceFileStorageDO::getFileMd5,fileMd5));
    }

    /*
    * 已上传分片数 +1
    * */
    default int incrementUploadedChunks(Long id) {
        return update(Wrappers.<AiCustomerServiceFileStorageDO>lambdaUpdate()
                .eq(AiCustomerServiceFileStorageDO::getId,id)
                .setSql("uploaded_chunks = uploaded_chunks + 1" ));
    }

}
