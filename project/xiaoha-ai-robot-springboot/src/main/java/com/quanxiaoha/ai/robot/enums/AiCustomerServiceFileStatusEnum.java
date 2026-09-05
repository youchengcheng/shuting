package com.quanxiaoha.ai.robot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 客服 Markdown 文件状态
 **/
@Getter
@AllArgsConstructor
public enum AiCustomerServiceFileStatusEnum {

    UPLOADING(0, "上传中"),
    PENDING(1, "上传成功,待处理"),
    VECTORIZING(2, "向量化中"),
    COMPLETED(3, "已完成"),
    FAILED(4, "失败");;

    private Integer code;
    private String description;

    /*
    * 根据 code 获取枚举
    * */
    public static AiCustomerServiceFileStatusEnum codeOf(Integer code) {
        if(code == null) {
            return null;
        }
        for (AiCustomerServiceFileStatusEnum serviceMdStatusEnum : AiCustomerServiceFileStatusEnum.values()) {
            if(serviceMdStatusEnum.getCode().equals(code)) {
                return serviceMdStatusEnum;
            }
        }
        return null;
    }

}

