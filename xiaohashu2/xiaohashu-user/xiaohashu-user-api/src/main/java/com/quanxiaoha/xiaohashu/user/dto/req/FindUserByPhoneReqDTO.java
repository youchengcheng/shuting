package com.quanxiaoha.xiaohashu.user.dto.req;

import com.quanxiaoha.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
 * 根据手机号查询用户信息（入参）
 * */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserByPhoneReqDTO {

    /*
     * 手机号
     * */
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;
}
