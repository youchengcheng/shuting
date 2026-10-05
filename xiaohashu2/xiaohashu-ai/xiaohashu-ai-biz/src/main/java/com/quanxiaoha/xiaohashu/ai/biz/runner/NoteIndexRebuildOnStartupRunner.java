package com.quanxiaoha.xiaohashu.ai.biz.runner;

import com.quanxiaoha.xiaohashu.ai.biz.service.NoteIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时全量重建笔记索引（可选）
 *
 * <p>默认关闭，需要时设置 {@code xiaohashu.ai.index.rebuild-on-startup=true}。
 * 注意：全量重建会遍历所有公开笔记并逐篇调用 embedding，数据量大时启动会很慢，
 * 建议只在首次部署或索引损坏时打开。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "xiaohashu.ai.index", name = "rebuild-on-startup", havingValue = "true")
public class NoteIndexRebuildOnStartupRunner implements ApplicationRunner {

    @Resource
    private NoteIndexService noteIndexService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("## 启动时全量重建笔记索引开始");
        try {
            noteIndexService.rebuildAll();
        } catch (Exception e) {
            log.error("## 启动时全量重建笔记索引失败", e);
        }
    }
}