package com.quanxiaoha.xiaohashu.notification.biz.domain.mapper;

import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationDO;
import com.quanxiaoha.xiaohashu.notification.biz.domain.dataobject.NotificationUserStateDO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知数据查询及更新
 */
@Mapper
public interface NotificationDOMapper {

    /*
     * 初始化用户通知状态并获取排他锁
     * */
    int ensureState(@Param("id") Long recipientId);

    /*
     * 锁定用户通知状态
     * */
    NotificationUserStateDO lockState(@Param("id") Long recipientId);

    /*
     * 查询用户通知状态
     * */
    NotificationUserStateDO state(@Param("id") Long recipientId);

    /*
     * 批量查询用户通知状态
     * */
    List<NotificationUserStateDO> states(@Param("ids") List<Long> ids);

    /*
     * 根据业务键查询通知
     * */
    NotificationDO byBusinessKey(@Param("recipientId") Long recipientId, @Param("businessKey") String businessKey);

    /*
     * 根据通知 ID 查询通知
     * */
    NotificationDO byId(@Param("recipientId") Long recipientId, @Param("id") Long id);

    /*
     * 新增通知记录
     * */
    int insert(NotificationDO record);

    /*
     * 推进最新通知序号和版本
     * */
    int advance(@Param("id") Long recipientId, @Param("seq") long seq);

    /*
     * 更新通知已读时间
     * */
    int markRead(@Param("recipientId") Long recipientId, @Param("id") Long id);

    /*
     * 递增通知状态版本
     * */
    int bumpRevision(@Param("id") Long recipientId);

    /*
     * 更新分类已读水位
     * */
    int setWatermark(@Param("id") Long recipientId, @Param("category") String category, @Param("seq") long seq);

    /*
     * 查询快照内的通知分页
     * */
    List<NotificationDO> page(@Param("recipientId") Long recipientId, @Param("category") String category,
                                  @Param("snapshot") long snapshot, @Param("before") long before, @Param("limit") int limit);

    /*
     * 统计分类未读数量
     * */
    long unread(@Param("recipientId") Long recipientId, @Param("category") String category, @Param("watermark") long watermark);

    /*
     * 批量查询通知
     * */
    List<NotificationDO> byIds(@Param("recipientId") Long recipientId, @Param("ids") List<Long> ids);

    /*
     * 隔离非法互动事件
     * */
    int isolate(@Param("source") String source, @Param("identity") String identity,
                @Param("payload") String payload, @Param("reason") String reason);
}
