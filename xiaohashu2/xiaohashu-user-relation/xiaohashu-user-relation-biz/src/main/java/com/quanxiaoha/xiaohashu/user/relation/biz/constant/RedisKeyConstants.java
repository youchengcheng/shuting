package com.quanxiaoha.xiaohashu.user.relation.biz.constant;

/**
 * TODO
 */
public class RedisKeyConstants {

    /**
     * 关注列表 KEY 前缀
     */
    private static final String USER_FOLLOWING_KEY_PREFIX = "following:";

    /*
    * 粉丝列表 key 前缀
    * */
    private static  final String USER_FANS_KEY_PREFUX = "fans:";

    /**
     * 构建关注列表完整的 KEY
     * @param userId
     * @return
     */
    public static String buildUserFollowingKey(Long userId) {
        return USER_FOLLOWING_KEY_PREFIX + userId;
    }

    /*
    * 构建粉丝列表完整的key
    * */
    public static String buildUserFansKey(Long userId){
        return USER_FANS_KEY_PREFUX + userId;
    }

}
