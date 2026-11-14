package com.quanxiaoha.xiaohashu.notification.biz.controller;

import com.quanxiaoha.framework.biz.context.holder.LoginUserContextHolder;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.notification.biz.config.NotificationSettings;
import com.quanxiaoha.xiaohashu.notification.biz.model.NotificationResponses.*;
import com.quanxiaoha.xiaohashu.notification.biz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/notification")
public class NotificationController {
    private final NotificationService service;
    private final NotificationTokenValidator tokens;
    private final NotificationSettings settings;
    private final ObjectProvider<NotificationStreamHub> streams;

    public NotificationController(NotificationService service, NotificationTokenValidator tokens,
                                  NotificationSettings settings, ObjectProvider<NotificationStreamHub> streams) {
        this.service = service;
        this.tokens = tokens;
        this.settings = settings;
        this.streams = streams;
    }

    @PostMapping("/list")
    public Response<Page> list(@RequestBody ListRequest body, HttpServletRequest request) {
        Session session = authenticate(request);
        return Response.success(service.list(session.userId, body.category(), body.cursor(), body.snapshotSeq(), body.pageSize()));
    }

    @GetMapping("/summary")
    public Response<Summary> summary(HttpServletRequest request) {
        return Response.success(service.summary(authenticate(request).userId));
    }

    @PostMapping("/read")
    public Response<ReadResult> read(@RequestBody ReadRequest body, HttpServletRequest request) {
        return Response.success(service.markRead(authenticate(request).userId, body.notificationId()));
    }

    @PostMapping("/read-all")
    public Response<ReadAllResult> readAll(@RequestBody ReadAllRequest body, HttpServletRequest request) {
        return Response.success(service.markAllRead(authenticate(request).userId, body.category(), body.snapshotSeq()));
    }

    @PostMapping("/read-state")
    public Response<ReadState> readState(@RequestBody ReadStateRequest body, HttpServletRequest request) {
        return Response.success(service.readState(authenticate(request).userId, body.notificationIds()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response) {
        Session session = authenticate(request);
        NotificationStreamHub hub = streams.getIfAvailable();
        if (hub == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "通知服务尚未启用");
        SseEmitter emitter = hub.connect(session.userId, session.token);
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return emitter;
    }

    private Session authenticate(HttpServletRequest request) {
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null || userId <= 0 || headers.size() != 1) throw loginRequired();
        String header = headers.get(0);
        if (!header.startsWith("Bearer ")) throw loginRequired();
        String token = header.substring(7);
        if (token.isBlank() || token.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == ',')) throw loginRequired();
        // The gateway identity alone is insufficient if this service port is called directly.
        tokens.require(token, userId);
        if (!settings.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "通知服务尚未启用");
        return new Session(userId, token);
    }

    private static ResponseStatusException loginRequired() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"); }
    private record Session(long userId, String token) { }
    public record ListRequest(String category, String cursor, String snapshotSeq, Integer pageSize) { }
    public record ReadRequest(String notificationId) { }
    public record ReadAllRequest(String category, String snapshotSeq) { }
    public record ReadStateRequest(List<String> notificationIds) { }
}
