package com.mianshitong.project.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initJsonTypeHandler() {
        // Reuse Spring's ObjectMapper so JSON fields support Java time and global settings.
        JacksonTypeHandler.setObjectMapper(objectMapper.copy());
    }
}
