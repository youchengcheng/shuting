package com.quanxiaoha.xiaohashu.distributed.id.generator.api;

import com.quanxiaoha.xiaohashu.distributed.id.generator.constant.ApiConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface DistributedIdGeneratorFeignApi {

    /*
    * 前缀
    * */
    String PREFIX = "/id";

    @RequestMapping(value = PREFIX + "/segment/get/{key}")
    String getSegmentId(@PathVariable("key") String key);

    @RequestMapping(value = PREFIX + "/snowflake/get/{key}")
    String getSnowflakeId(@PathVariable("key") String key);

}
