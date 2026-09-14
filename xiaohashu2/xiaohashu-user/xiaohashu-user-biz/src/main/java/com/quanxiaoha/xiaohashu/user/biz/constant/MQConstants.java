package com.quanxiaoha.xiaohashu.user.biz.constant;


public interface MQConstants {


    /**
     * Topic 主题：延迟双删 Redis 用户缓存
     */
    String TOPIC_DELAY_DELETE_USER_REDIS_CACHE = "DelayDeleteUserRedisCacheTopic";

    /**
     * Topic 主题：用户资料变更（昵称、头像、简介等）
     * 生产者：本服务
     * 消费者：本服务（广播模式，清本地缓存）、note 服务（集群模式，清已发布笔记列表缓存）
     * 注意：Topic 名由生产者和消费者共同约定，必须与 note 服务 MQConstants 中的同名常量保持一致
     */
    String TOPIC_USER_PROFILE_CHANGED = "UserProfileChangedTopic";

}

