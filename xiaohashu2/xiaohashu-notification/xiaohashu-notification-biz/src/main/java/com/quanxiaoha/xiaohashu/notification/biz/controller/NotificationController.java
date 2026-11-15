package com.quanxiaoha.xiaohashu.notification.biz.controller;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.notification.biz.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.notification.biz.util.NotificationAuthUtils;
import com.quanxiaoha.xiaohashu.notification.biz.model.dto.NotificationSessionDTO;
import com.quanxiaoha.xiaohashu.notification.biz.model.vo.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 * 通知
 * */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationAuthUtils notificationAuthUtils;

    @Resource
    private ObjectProvider<NotificationStreamHub> notificationStreams;

    /*
     * 通知列表分页查询
     * */
    @PostMapping("/list")
    public Response<FindNotificationPageRspVO> list(@Validated @RequestBody FindNotificationPageListReqVO findNotificationPageListReqVO,
                                                  HttpServletRequest request) {
        // 从统一登录工具取得当前用户，接口不接受客户端指定通知接收者
        NotificationSessionDTO notificationSessionDTO = notificationAuthUtils.authenticate(request);
        return Response.success(notificationService.list(notificationSessionDTO.getUserId(),
                findNotificationPageListReqVO.getCategory(), findNotificationPageListReqVO.getCursor(),
                findNotificationPageListReqVO.getSnapshotSeq(), findNotificationPageListReqVO.getPageSize()));
    }

    /*
     * 查询通知未读数量
     * */
    @GetMapping("/summary")
    public Response<FindNotificationSummaryRspVO> summary(HttpServletRequest request) {
        // 摘要只查询当前登录用户的未读数量和版本
        return Response.success(notificationService.summary(notificationAuthUtils.authenticate(request).getUserId()));
    }

    /*
     * 将单条通知标为已读
     * */
    @PostMapping("/read")
    public Response<ReadNotificationRspVO> read(@Validated @RequestBody ReadNotificationReqVO readNotificationReqVO,
                                               HttpServletRequest request) {
        // 服务端校验通知归属后更新已读状态
        return Response.success(notificationService.markRead(notificationAuthUtils.authenticate(request).getUserId(),
                readNotificationReqVO.getNotificationId()));
    }

    /*
     * 将快照内的分类通知全部标为已读
     * */
    @PostMapping("/read-all")
    public Response<ReadAllNotificationRspVO> readAll(@Validated @RequestBody ReadAllNotificationReqVO readAllNotificationReqVO,
                                                     HttpServletRequest request) {
        // 传递客户端已加载的快照，避免将刚到达的新通知一并标为已读
        return Response.success(notificationService.markAllRead(notificationAuthUtils.authenticate(request).getUserId(),
                readAllNotificationReqVO.getCategory(), readAllNotificationReqVO.getSnapshotSeq()));
    }

    /*
     * 批量查询通知已读状态
     * */
    @PostMapping("/read-state")
    public Response<FindNotificationReadStateRspVO> readState(@Validated @RequestBody FindNotificationReadStateReqVO findNotificationReadStateReqVO,
                                                             HttpServletRequest request) {
        // 按当前用户过滤批量 ID，不能读取其他账号的通知状态
        return Response.success(notificationService.readState(notificationAuthUtils.authenticate(request).getUserId(),
                findNotificationReadStateReqVO.getNotificationIds()));
    }

    /*
     * 建立通知实时推送连接
     * */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response) {
        // 从统一登录工具取得当前用户，接口不接受客户端指定通知接收者
        NotificationSessionDTO notificationSessionDTO = notificationAuthUtils.authenticate(request);
        // 未启用时不会创建 SSE 组件，通过 ObjectProvider 获取可选实现
        NotificationStreamHub notificationStreamHub = notificationStreams.getIfAvailable();
        if (notificationStreamHub == null) {
            throw new BizException(ResponseCodeEnum.SERVICE_NOT_ENABLED);
        }

        SseEmitter sseEmitter = notificationStreamHub.connect(notificationSessionDTO.getUserId(), notificationSessionDTO.getToken());
        // 禁止代理缓存和缓冲，使变化提示及时到达客户端
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return sseEmitter;
    }

}
