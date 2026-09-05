package com.quanxiaoha.xiaohashu.search.service.impl;

import com.quanxiaoha.xiaohashu.search.service.ExtDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExtDictServiceImpl implements ExtDictService {

    @Value("${elasticsearch.hotUpdateExtDict}")
    private String hotUpdateExtDict;

    /*
     * 获取热更新词典
     * */
    @Override
    public ResponseEntity<String> getHotUpdateExtDict() {
        try {
            //获取文件路径
            Path path = Paths.get(hotUpdateExtDict);
            long lastModifiedTime = Files.getLastModifiedTime(path).toMillis();

            //生成ETag
            String fileContent;
            try (var lines = Files.lines(path)) {
                fileContent = lines.collect(Collectors.joining("\n"));
            }

            //设置响应头
            HttpHeaders headers = new HttpHeaders();

            //设置内容类型为 UTF-8
            headers.setContentType(MediaType.valueOf("text/plain;charset=UTF-8"));

            //返回文件内容和HTTP头部
            return ResponseEntity.ok()
                    .headers(headers)
                    .lastModified(lastModifiedTime)
                    .body(fileContent);
        }catch (Exception e){
            log.error("获取热更新词典异常", e);
            return ResponseEntity.notFound().build();
        }
    }
}
