package com.quanxiaoha.ai.robot.model.vo.customerService;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 文件检查
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckFileReqVO {

    @NotBlank(message = "文件 MD5 不能为空")
    private String fileMd5;

}

