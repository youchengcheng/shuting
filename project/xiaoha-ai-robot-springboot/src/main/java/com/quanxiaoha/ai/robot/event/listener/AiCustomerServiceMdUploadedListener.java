package com.quanxiaoha.ai.robot.event.listener;

import com.quanxiaoha.ai.robot.domain.dos.AiCustomerServiceFileStorageDO;
import com.quanxiaoha.ai.robot.domain.mapper.AiCustomerServiceFileStorageMapper;
import com.quanxiaoha.ai.robot.enums.AiCustomerServiceFileStatusEnum;
import com.quanxiaoha.ai.robot.event.AiCustomerServiceMdUploadedEvent;
import com.quanxiaoha.ai.robot.reader.MarkdownReader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AiCustomerServiceMdUploadedListener {

    @Resource
    private MarkdownReader markdownReader;
    @Resource
    private VectorStore vectorStore;
    @Resource
    private AiCustomerServiceFileStorageMapper aiCustomerServiceMdStorageMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    /*
    * markdown 文件向量化
    * */
    @EventListener
    @Async("eventTaskExecutor")//使用自定义的线程池
    public void vectorizing(AiCustomerServiceMdUploadedEvent event) {
        log.info("## AiCustomerServiceMdUploadedEvent: {}", event);

        //文件存储表主键id
        Long id = event.getId();
        //markdown文件存储路径
        String filePath = event.getFilePath();
        //元数据
        Map<String, Object> metadatas = event.getMetadatas();

        //更新存储文件的处理状态为“向量化中”
        aiCustomerServiceMdStorageMapper.updateById(AiCustomerServiceFileStorageDO.builder()
                        .id(id)
                        .status(AiCustomerServiceFileStatusEnum.VECTORIZING.getCode())
                        .updateTime(LocalDateTime.now())
                        .build());

        //编程式事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                //读取文件
                FileSystemResource resource = new FileSystemResource(filePath);

                //解析为document集合
                List<Document> documents = markdownReader.loadMarkdown(resource, metadatas);
                log.info("## documents: {}", documents);

                //向量化：并存储入库
                for (Document document : documents) {
                    //防止重复添加相同文档到苹果vector中
                    //从向量数据中，查询当前文档
                    List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                            .query(document.getText())
                            .topK(1) //查询一条最高得分的
                            .build());

                    //判断：如果结果不为空，并且得分打印0.99，则表示文档较高机率重复，直接跳过
                    if (!results.isEmpty() && results.get(0).getScore() > 0.99) {
                        continue;
                    }

                    //通过向量模型，将文档向量化存储到pgvector中
                    vectorStore.add(List.of(document));
                }
                //更新存储文件的处理状态为"已完成”
                aiCustomerServiceMdStorageMapper.updateById(AiCustomerServiceFileStorageDO.builder()
                        .id(id)
                        .status(AiCustomerServiceFileStatusEnum.COMPLETED.getCode())
                        .updateTime(LocalDateTime.now())
                        .build());

                return true;
            } catch (Exception e) {
                log.error("## Markdown 文件向量化失败: {}", event, e);
                status.setRollbackOnly();//标记事务回滚
                return false;
            }

        }));

        //若事务执行失败，更新存储文件的处理状态为“失败”
        if(!isSuccess) {
            aiCustomerServiceMdStorageMapper.updateById(AiCustomerServiceFileStorageDO.builder()
                            .id(id)
                            .status(AiCustomerServiceFileStatusEnum.FAILED.getCode())
                            .updateTime(LocalDateTime.now())
                            .build());
        }
    }
}
