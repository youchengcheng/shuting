package com.quanxiaoha.xiaohashu.notification.biz.mapper;

import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationRecord;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationUserState;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NotificationMapper {
    String COLUMNS = "id,business_key,recipient_id,actor_id,type,category,recipient_role,note_id,comment_id,reply_comment_id,occurred_at,seq,read_at,created_at";

    // A duplicate UPDATE takes an exclusive lock directly; INSERT IGNORE's shared lock can deadlock on FOR UPDATE upgrade.
    @Insert("INSERT INTO t_notification_user_state(recipient_id,last_seq,revision,like_collection_read_seq,comment_reply_read_seq,follow_read_seq) VALUES(#{id},0,0,0,0,0) ON DUPLICATE KEY UPDATE recipient_id=recipient_id")
    int ensureState(@Param("id") Long recipientId);

    @Select("SELECT * FROM t_notification_user_state WHERE recipient_id=#{id} FOR UPDATE")
    NotificationUserState lockState(@Param("id") Long recipientId);

    @Select("SELECT * FROM t_notification_user_state WHERE recipient_id=#{id}")
    NotificationUserState state(@Param("id") Long recipientId);

    @Select({"<script>", "SELECT * FROM t_notification_user_state WHERE recipient_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>", "</script>"})
    List<NotificationUserState> states(@Param("ids") List<Long> ids);

    @Select("SELECT " + COLUMNS + " FROM t_notification WHERE recipient_id=#{recipientId} AND business_key=#{businessKey}")
    NotificationRecord byBusinessKey(@Param("recipientId") Long recipientId, @Param("businessKey") String businessKey);

    @Select("SELECT " + COLUMNS + " FROM t_notification WHERE recipient_id=#{recipientId} AND id=#{id}")
    NotificationRecord byId(@Param("recipientId") Long recipientId, @Param("id") Long id);

    @Insert("INSERT INTO t_notification(business_key,recipient_id,actor_id,type,category,recipient_role,note_id,comment_id,reply_comment_id,occurred_at,seq,created_at) VALUES(#{businessKey},#{recipientId},#{actorId},#{type},#{category},#{recipientRole},#{noteId},#{commentId},#{replyCommentId},#{occurredAt},#{seq},NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationRecord record);

    @Update("UPDATE t_notification_user_state SET last_seq=#{seq},revision=revision+1 WHERE recipient_id=#{id}")
    int advance(@Param("id") Long recipientId, @Param("seq") long seq);

    @Update("UPDATE t_notification SET read_at=NOW() WHERE recipient_id=#{recipientId} AND id=#{id} AND read_at IS NULL")
    int markRead(@Param("recipientId") Long recipientId, @Param("id") Long id);

    @Update("UPDATE t_notification_user_state SET revision=revision+1 WHERE recipient_id=#{id}")
    int bumpRevision(@Param("id") Long recipientId);

    @Update({"<script>", "UPDATE t_notification_user_state SET revision=revision+1,",
            "<choose><when test=\"category == 'LIKE_COLLECTION'\">like_collection_read_seq</when><when test=\"category == 'COMMENT_REPLY'\">comment_reply_read_seq</when><otherwise>follow_read_seq</otherwise></choose>",
            "=#{seq} WHERE recipient_id=#{id}", "</script>"})
    int setWatermark(@Param("id") Long recipientId, @Param("category") String category, @Param("seq") long seq);

    @Select("SELECT " + COLUMNS + " FROM t_notification WHERE recipient_id=#{recipientId} AND category=#{category} AND seq <= #{snapshot} AND (#{before}=0 OR seq < #{before}) ORDER BY seq DESC LIMIT #{limit}")
    List<NotificationRecord> page(@Param("recipientId") Long recipientId, @Param("category") String category,
                                  @Param("snapshot") long snapshot, @Param("before") long before, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_notification WHERE recipient_id=#{recipientId} AND category=#{category} AND read_at IS NULL AND seq > #{watermark}")
    long unread(@Param("recipientId") Long recipientId, @Param("category") String category, @Param("watermark") long watermark);

    @Select({"<script>", "SELECT " + COLUMNS + " FROM t_notification WHERE recipient_id=#{recipientId} AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>", "</script>"})
    List<NotificationRecord> byIds(@Param("recipientId") Long recipientId, @Param("ids") List<Long> ids);

    @Insert("INSERT IGNORE INTO t_notification_event_error(source,event_identity,payload,reason,created_at) VALUES(#{source},#{identity},#{payload},#{reason},NOW())")
    int isolate(@Param("source") String source, @Param("identity") String identity,
                @Param("payload") String payload, @Param("reason") String reason);
}
