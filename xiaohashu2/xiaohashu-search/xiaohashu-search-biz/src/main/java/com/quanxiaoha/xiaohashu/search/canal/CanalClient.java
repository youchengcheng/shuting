package com.quanxiaoha.xiaohashu.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Objects;

@Component
@Slf4j
public class CanalClient implements DisposableBean {

    @Resource
    private CanalProperties canalProperties;

    private CanalConnector canalConnector;

    /*
    * 实例化canal连接对象
    * */
    @Bean
    public CanalConnector getCanalConnector(){
        //canal 连接地址
        String address = canalProperties.getAddress();
        String[] addressArr = address.split(":");
        //IP地址
        String host = addressArr[0];

        //端口
        int port = Integer.parseInt(addressArr[1]);

        //创建一个 canalConnector 实例，连接到指定的canal服务端
        canalConnector = CanalConnectors.newClusterConnector(
                Collections.singletonList(new InetSocketAddress(host, port)),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());

        //连接到canal服务端
        canalConnector.connect();

        //订阅canal重点数据的变化，指定要监听的数据库的表
        canalConnector.subscribe(canalProperties.getSubscribe());

        //回滚canal 消费者的定点，回滚到上次提交的消费位置
        canalConnector.rollback();
        return canalConnector;

    }


    /*
    * 销毁bean
    * */
    @Override
    public void destroy() throws Exception {
        if (Objects.nonNull(canalConnector)) {
            // 断开 canalConnector 与 Canal 服务的连接
            canalConnector.disconnect();
        }
    }
}
