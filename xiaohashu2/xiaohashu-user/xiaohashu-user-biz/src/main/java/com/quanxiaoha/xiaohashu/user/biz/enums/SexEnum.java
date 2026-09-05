package com.quanxiaoha.xiaohashu.user.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/*
* 性别枚举类
* */
@Getter
@AllArgsConstructor
public enum SexEnum {
    WOMAN(0),
    MAN(1);

    private final Integer value;

    /*
    * 性别校验
    * */
    public static boolean isValid(Integer value){
        //SexEnum.values()获取当前枚举类中的所有枚举属性，使用增强for循环一一比对，相同时返回true,否则返回false
        for (SexEnum loginTypeEnum : SexEnum.values()) {
            if(Objects.equals(value,loginTypeEnum.getValue())){
                return true;
            }
        }
        return false;
    }

}
