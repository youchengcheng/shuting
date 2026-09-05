package com.quanxiaoha.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
* AI 客服 Markdown 问答文件存储
* */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_ai_customer_service_file_storage")
public class AiCustomerServiceFileStorageDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileMd5;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
