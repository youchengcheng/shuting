package com.quanxiaoha.framework.common.util;

import java.util.regex.Pattern;

/*
* 参数条件校验
* */
public class ParamUtils {
    //阻止其他人构建本工具类对象，将无参构造设为私有
    private ParamUtils(){
    }

    //==================校验昵称========================
    //定义昵称的长度范围
    private static final int NICK_NAME_MIN_LENGTH = 2;
    private static final int NICK_NAME_MAX_LENGTH = 24;
    //定义特殊字符的正则表达式
    private static final String NICK_NAME_REGEX = "[!@#$%^&*(),.?\":{}|<>]";
    /*
    * 昵称校验
    * */
    public static boolean checkNickname(String nickname){
        //检查长度
        if(nickname.length() < NICK_NAME_MIN_LENGTH || nickname.length() > NICK_NAME_MAX_LENGTH){
            return false;
        }
        //检查是否有特殊字符
        Pattern pattern = Pattern.compile(NICK_NAME_REGEX);
        //find()发现nickname含有一个特殊字符串就返回false,因为原本发现又符合的是返回true，
        //但是我们这里是不能有特殊字符串，于是发现就取反返回false
        return !pattern.matcher(nickname).find();
    }


    //==================校验小哈书号========================
    //定义id的长度范围
    private static final int ID_MIN_LENGTH = 6;
    private static final int ID_MAX_LENGTH = 15;
    //定义正则表达式
    private static final String ID_REGEX = "^[a-zA-Z0-9_]+$";
    /*
    * 小哈书id校验
    * */
    public static boolean checkXiaohashuId(String xiaohashuId){
        //检查长度
        if(xiaohashuId.length() < ID_MIN_LENGTH || xiaohashuId.length() > ID_MAX_LENGTH){
            return false;
        }
        //检查格式
        Pattern pattern = Pattern.compile(ID_REGEX);
        //matches()是完全匹配，也就是xiaohashuId要完全符合正则表达式
        return pattern.matcher(xiaohashuId).matches();
    }


    /*
    * 字符串长度校验（描述文本）
    * */
    public static boolean checkLength(String str,int length){
        //检查长度
        if(str.isEmpty() || str.length() > length){
            return false;
        }
        return true;
    }
















}
