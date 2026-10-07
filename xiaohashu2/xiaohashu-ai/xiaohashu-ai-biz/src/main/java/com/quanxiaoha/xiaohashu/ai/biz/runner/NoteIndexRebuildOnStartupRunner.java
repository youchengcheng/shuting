package com.quanxiaoha.xiaohashu.ai.biz.runner;

import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 启动时自动补齐笔记索引（默认开启）
 *
 * <p>笔记发布 / 更新 / 删除时会通过 MQ 增量同步索引，但下面两种情况会漏：
 * 1) AI 服务没在跑（消息被跳过）、2) AI 模块首次部署，历史笔记从来没建过索引。
 * 所以启动后异步跑一次增量扫描，把所有「可检索但还没进向量库」的笔记补上。</p>
 *
 * <p>重建本身是增量的：内容 MD5 没变会直接跳过，不会重复消耗 embedding 额度，
 * 因此这里默认开启；如需关闭设置 {@code xiaohashu.ai.index.rebuild-on-startup=false}。</p>
 *
 * <p>扫描在后台线程执行，不阻塞应用启动。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "xiaohashu.ai.index", name = "rebuild-on-startup", havingValue = "true", matchIfMissing = true)
public class NoteIndexRebuildOnStartupRunner implements ApplicationRunner {

    @Resource
    private NoteIndexService noteIndexService;

    @Resource(name = "aiTaskExecutor")
    private ThreadPoolTaskExecutor aiTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        aiTaskExecutor.execute(() -> {
            log.info("## 启动时增量补齐笔记索引开始");
            try {
                noteIndexService.rebuildAll();
            } catch (Exception e) {
                log.error("## 启动时增量补齐笔记索引失败（不影响应用启动，可稍后手动重建）", e);
            }
        });
    }
}