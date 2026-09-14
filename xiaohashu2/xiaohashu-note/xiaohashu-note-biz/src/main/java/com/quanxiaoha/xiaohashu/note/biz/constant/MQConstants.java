package com.quanxiaoha.xiaohashu.note.biz.constant;

public interface MQConstants {

    /**
     * Topic 主题：删除笔记本地缓存
     */
    String TOPIC_DELETE_NOTE_LOCAL_CACHE = "DeleteNoteLocalCacheTopic";

    /**
     * Topic 主题：延迟双删 Redis 笔记缓存
     */
    String TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE = "DelayDeleteNoteRedisCacheTopic";

    /**
     * Topic: 点赞、取消点赞共用一个
     */
    String TOPIC_LIKE_OR_UNLIKE = "LikeUnlikeTopic";

    /**
     * 点赞标签
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞
     */
    String TAG_UNLIKE = "Unlike";

    /**
     * Topic: 计数 - 笔记点赞数
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * Topic: 收藏、取消收藏共用一个
     */
    String TOPIC_COLLECT_OR_UN_COLLECT = "CollectUnCollectTopic";


    /**
     * Tag 标签：收藏
     */
    String TAG_COLLECT = "Collect";

    /**
     * Tag 标签：取消收藏
     */
    String TAG_UN_COLLECT = "UnCollect";

    /**
     * Topic: 计数 - 笔记收藏数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";

    /**
     * Topic: 笔记操作（发布、删除）
     */
    String TOPIC_NOTE_OPERATE = "NoteOperateTopic";

    /**
     * Tag 标签：笔记发布
     */
    String TAG_NOTE_PUBLISH = "publishNote";

    /**
     * Tag 标签：笔记删除
     */
    String TAG_NOTE_DELETE = "deleteNote";

    /**
     * Topic 主题：延迟双删 Redis 已发布笔记列表缓存
     */
    String TOPIC_DELAY_DELETE_PUBLISHED_NOTE_LIST_REDIS_CACHE = "DelayDeletePublishedNoteListRedisCacheTopic";

    /**
     * Topic 主题：用户资料变更（昵称、头像等）
     * 生产者：user 服务
     * 消费者：本服务（集群模式，删除已发布笔记列表缓存）
     * 注意：Topic 名由生产者和消费者共同约定，必须与 user 服务 MQConstants 中的同名常量保持一致
     */
    String TOPIC_USER_PROFILE_CHANGED = "UserProfileChangedTopic";

}
