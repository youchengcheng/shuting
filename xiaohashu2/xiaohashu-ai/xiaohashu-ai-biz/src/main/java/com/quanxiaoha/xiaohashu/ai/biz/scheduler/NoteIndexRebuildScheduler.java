package com.quanxiaoha.xiaohashu.ai.biz.scheduler;

import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 笔记索引全量重建兜底任务
 *
 * <p>MQ 增量同步可能丢消息，这里每天跑一次全量重建，保证索引最终一致。
 * 默认关闭（xiaohashu.ai.index.rebuild-enabled=true 才开启）。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "xiaohashu.ai.index", name = "rebuild-enabled", havingValue = "true")
public class NoteIndexRebuildScheduler {

    @Resource
    private NoteIndexService noteIndexService;

    @Scheduled(cron = "${xiaohashu.ai.index.rebuild-cron:0 30 3 * * ?}")
    public void rebuild() {
        log.info("## 开始执行笔记索引每日全量重建");
        try {
            noteIndexService.rebuildAll();
        } catch (Exception e) {
            log.error("## 笔记索引每日全量重建失败", e);
        }
    }
}