package com.quanxiaoha.ai.robot.controller;


import com.google.common.collect.Lists;
import com.quanxiaoha.ai.robot.advisor.CustomerServiceAdvisor;
import com.quanxiaoha.ai.robot.aspect.ApiOperationLog;
import com.quanxiaoha.ai.robot.model.vo.chat.AIResponse;
import com.quanxiaoha.ai.robot.model.vo.customerService.*;
import com.quanxiaoha.ai.robot.service.CustomerService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 客服
 **/
@RestController
@RequestMapping("/customer-service")
@Slf4j
public class AiCustomerServiceController {

    @Resource
    private CustomerService customerService;
    @Resource
    private VectorStore vectorStore;
    @Resource
    private ChatClient chatClient;

    @Value("${customer-service.model}")
    private String model;
    @Value("${customer-service.temperature}")
    private Double temperature;

//    /**
//     * 问答 MD 文件上传
//     */
//    @PostMapping(value = "/md/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public Response<?> uploadMarkdownFile(@RequestPart(value = "file", required = false) MultipartFile file) {
//        return customerService.uploadMarkdownFile(file);
//    }

    /*
    * 检查文件是否存在
    * */
    @PostMapping("/file/check")
    @ApiOperationLog(description = "检查文件是否存在")
    public Response<CheckFileRspVO> checkFile(@RequestBody @Validated CheckFileReqVO checkFileReqVO) {
        return customerService.checkFile(checkFileReqVO);
    }

    /*
    * 文件分片上传
    * */
    @PostMapping("/file/upload-chunk")
    @ApiOperationLog(description = "文件分片上传")
    public Response<?> uploadChunk(@ModelAttribute UploadChunkReqVO uploadChunkReqVO) {
        return customerService.uploadChunk(uploadChunkReqVO);
    }

    /*
     * 文件分片合并
     * */
    @PostMapping("/file/merge-chunk")
    @ApiOperationLog(description = "文件分片合并")
    public Response<?> mergeChunk(@RequestBody @Validated MergeChunkReqVO mergeChunkReqVO) {
        return customerService.mergeChunk(mergeChunkReqVO);
    }

    /*
    * 删除 Markdown 问答文件
    * */
    @ApiOperationLog(description = "删除 Markdown 问答文件")
    @PostMapping("/md/delete")
    public Response<?> deleteMarkdownFile(@RequestBody @Validated DeleteMarkdownFileReqVO deleteMarkdownFileReqVO) {
        return customerService.deleteMarkdownFile(deleteMarkdownFileReqVO);
    }

    /*
    * Markdown 问答文件分页查询
    * */
    @PostMapping({"/md/list", "/file/list"})
    @ApiOperationLog(description = "Markdown 问答文件分页查询")
    public PageResponse<FindMarkdownFilePageListRspVO> findMarkdownFilePageList(@RequestBody @Validated FindMarkdownFilePageListReqVO findMarkdownFilePageListReqVO) {
        return customerService.findMarkdownFilePageList(findMarkdownFilePageListReqVO);
    }

    /*
    * 修改  Markdown 问答文件信息
    * */
    @ApiOperationLog(description = "修改  Markdown 问答文件信息")
    @PostMapping("/md/update")
    public Response<?> updateMarkdownFile(@RequestBody @Validated UpdateMarkdownFileReqVO updateMarkdownFileReqVO){
        return customerService.updateMarkdownFile(updateMarkdownFileReqVO);
    }



    /*
    * 流式对话
    * */
    @ApiOperationLog(description = "AI 智能客服对话")
    @PostMapping(value = {"/chat/completion", "/completion"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponse> chat(@RequestBody @Validated AiCustomerServiceChatReqVO aiCustomerServiceChatReqVO) {
        //用户消息
        String userMessage = aiCustomerServiceChatReqVO.getMessage();

        //动态设置调用模型名称，温度值
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient
                .prompt()
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .user(userMessage);//用户提示词

        //advisor集合
        List<Advisor> advisors = Lists.newArrayList();
        //检索向量库，组合增强提示词
        advisors.add(new CustomerServiceAdvisor(vectorStore));

        //应用advisor集合
        chatClientRequestSpec.advisors(advisors);

        //流式输出
        return chatClientRequestSpec.stream()
                .content()
                .mapNotNull(text -> AIResponse.builder().v(text).build());

    }

}

