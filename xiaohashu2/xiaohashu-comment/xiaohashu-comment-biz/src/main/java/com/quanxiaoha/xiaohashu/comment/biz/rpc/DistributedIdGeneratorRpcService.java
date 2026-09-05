package com.quanxiaoha.xiaohashu.comment.biz.rpc;

import com.quanxiaoha.xiaohashu.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/*
* 分布式id服务
* */
@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /*
    * 生成评论id
    * */
    public String generateCommentId(){
        return distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-comment-id");
    }

}
