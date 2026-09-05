package com.quanxiaoha.xiaohashu.note.biz.rpc;


import com.quanxiaoha.xiaohashu.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /*
    * 雪花算法生成id
    * */
    public String getSnowflakeId(){
        return distributedIdGeneratorFeignApi.getSnowflakeId("test");
    }

}
