package com.quanxiaoha.xiaohashu.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/*
* 笔记类型枚举类
* */
@Getter
@AllArgsConstructor
public enum NoteTypeEnum {
    IMAGE_TEXT(0,"图文"),
    VIDEO(1,"视频")
    ;
    private final Integer code;
    private final String description;

    /*
    * 类型是否有效
    * Valid:有效
    * */
    public static boolean isValid(Integer code){
        //values就是该枚举类类型的数组，数组里面存放了该枚举类中所有的枚举常量
        //noteTypeEnum枚举常量
        //noteTypeEnum.getCode()获取枚举常量值
        for (NoteTypeEnum noteTypeEnum : NoteTypeEnum.values()) {
            if(Objects.equals(code,noteTypeEnum.getCode())){
                return true;
            }
        }
        return false;
    }

    /*
    * 根据类型code 获取对应的枚举
    * */
    public static NoteTypeEnum valueOf(Integer code){
        for (NoteTypeEnum noteTypeEnum : NoteTypeEnum.values()) {
            if (Objects.equals(code,noteTypeEnum.getCode())){
                return noteTypeEnum;
            }
        }
        return null;
    }

}
