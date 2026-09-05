package com.quanxiaoha.ai.robot;

import com.quanxiaoha.ai.robot.domain.dos.ChatDO;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMapper;
import jakarta.annotation.Resource;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
class MybatisPlusTests {

    @Resource
    private ChatMapper chatMapper;

    /**
     * 添加数据
     */
    @Test
    void testInsert() {
        Integer a= 10;
        Class<? extends Integer> aClass = a.getClass();
        System.out.println(aClass);
    }

}

