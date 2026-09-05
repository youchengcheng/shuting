package com.quanxiaoha.ai.robot.reader;

import cn.hutool.core.collection.CollUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/*
* markdown文件读取
* */
@Component
public class MarkdownReader {

    /*
    * 读取 Markdown 文件为文档集合
    * */
    public List<Document> loadMarkdown(Resource resource, Map<String,Object> metadates) {
        //markdownDocumentReader 阅读器配置类
        MarkdownDocumentReaderConfig.Builder configBuilder = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)//遇到水平线---创建新文档
                .withIncludeCodeBlock(false)//排除代码块---代码块单独生成文档
                .withIncludeBlockquote(false);//排除块引用---块引用生成单独文档

        //添加自定义元数据，如文件名称
        if(CollUtil.isNotEmpty(metadates)) {
            configBuilder.withAdditionalMetadata(metadates);
        }

        //新建markdownDocumentReader 阅读器
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, configBuilder.build());

        //读取并转换为document 文档集合
        return reader.get();
    }

}
