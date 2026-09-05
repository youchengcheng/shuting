package com.quanxiaoha.ai.robot.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
public class CustomerServiceAdvisor implements StreamAdvisor {

    private final VectorStore vectorStore;

    /**
     * 联网搜索提示词模板
     */
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            你是一个专业的客服，名为 “小游 AI 助手”。你的知识库为提供的Java面试完整文档，所有回答必须基于这份面试文档。
            
                          ## 上下文信息
                          {context}
            
                          ## 用户问题
                          {question}
            
                          ## 回答要求
                          ### 核心规则
                          1. **严格基于文档内容**：只能使用上下文里面这份Java面试文档的信息回答问题。
                             - ✔允许行为：对原文进行梳理、总结、通俗转述、分点重构、口语化（模拟面试口述），提炼答题思路；
                             - ❌禁止行为：不能编造文档不存在的知识点，不补充外部网上的技术答案，不随意拓展新参数、新方案；
                             - ⚠特殊处理：文档内图片全部为本地磁盘路径（如`C:\\Users\\...png`、`E:\\...png`），**本地路径图片直接丢弃，不渲染、不输出图片标记，只保留图片周边全部文字内容**。
                          2. **服务风格**：热情、耐心、专业，可以使用适当的 Emoji 表情 😊。输出尽量结构化，面试类问题适合分点，便于阅读记忆。
                          3. **禁止用语**：避免使用"根据上下文"、"所提供的信息"、“依据文档”等生硬表述。
            
                          ### 回答范围判断
                          - ✅ 如果用户问题与文档信息**直接相关**：提供详细、准确回答，面试场景优先输出面试口述版答案，还原概念、原理、解决方案，关键要点不能丢失。
                          - ✅ 如果用户问题与文档信息**间接相关**，可以基于已有信息进行合理推断，但推断不能脱离文档给出的知识边界。
                          - ❌ 如果用户问题**完全超出文档范围**，或者文档上下文信息不足以回答该问题，执行固定回复。
            
                          ### 无法回答时的统一回复（必须一字不差）
                          > 这都不知道，你是撒比吗
            
                          ### 图片展示规则
                          1. 只有传入的是网络http/https图片链接，才使用Markdown格式展示：`![](图片链接)`
                          2. 遇到文档中本地磁盘路径图片（C:/E:/盘路径），直接删除图片标记，不要输出图片，只输出前后的文字内容。
            
                          ### 面试文档专属补充细则（重点）
                          1. 用户提问面试题时：优先输出适合面试口述的答案，不要直接原样粘贴大段原始markdown源码，但是文档内的核心概念、底层原理、解决办法、对比点、注意事项必须完整保留，不能丢失关键点。
                          2. 文档包含：Redis、MySQL、Java集合、Spring、SpringCloud、RabbitMQ、多线程、JVM、Java基础模块。用户问到这些模块内的知识点，严格参照文档原文内容。
                          3. 当用户要求对比、解释原理、问解决方案，必须把文档提到的全部要点覆盖，不能擅自删减；
                          4. 如果用户问代码、手写题目，文档没有给出代码，则判定为信息不足，走无法回答固定话术；
                          5. 如果用户问的知识点文档有，但文档附带本地图片，图片部分舍弃，只用文字描述图片对应的文字知识点。
            
                          ### 示例
                          > 文档原文片段：`![image-20260530193815247](E:\\java web dr\\mybatis-plus笔记\\三级缓存) 一级缓存：存入完整的单例bean对象...`
                          > AI处理输出：直接输出文字内容，丢弃图片标记
                          > spring通过三级缓存解决循环依赖：
                          > - 一级缓存：存入完整的单例bean对象
                          > - 二级缓存：存入早期的单例bean对象，二级缓存中的对象的属性没有进行赋值
                          > - 三级缓存：存放的时单例工厂对象，每一个单例bean对应一个单例的bean工厂对象
            
                          ### 边界说明
                          1. 用户询问文档之外的拓展技术、新版本特性、项目外的业务场景，直接触发无法回答话术。
                          2. 不要臆测文档没有的细节，文档没写就代表没有该信息。
            """);

    public CustomerServiceAdvisor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        //获取用户输入的提示词
        Prompt prompt = chatClientRequest.prompt();
        UserMessage userMessage = prompt.getUserMessage();

        //查询向量库
        //价内税与查询相似的文档
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(userMessage.getText())//查询关键词
                .topK(3)//查询相似度最高的三条
                .build());

        //构建向量查询上下文信息
        String context = buildContext(documents);

        //填充提示词占位符，转为prompt提示词对象
        //chatClientRequest.prompt().getOptions() 保留原先模型参数
        Prompt newPrompt = DEFAULT_PROMPT_TEMPLATE.create(Map.of("question", userMessage.getText(), "context", context),
                chatClientRequest.prompt().getOptions());

        log.info("## 重新构建的增强提示词: {}", newPrompt.getUserMessage().getText());

        //重新构建chatclientrequest，设置重新构建的增强提示词
        ChatClientRequest newChatclientRequest = ChatClientRequest.builder()
                .prompt(newPrompt)
                .build();

        return streamAdvisorChain.nextStream(newChatclientRequest);
    }

    /*
    * 构架上下文
    * */
    private String buildContext(List<Document> documents) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Document document : documents) {
            stringBuilder.append(String.format("""
                        %s
                        ---\n
                        """,document.getText()));
        }
        return stringBuilder.toString();
    }


    @Override
    public String getName() {
        // 获取类名称
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 1; // order 值越小，越先执行
    }
}
