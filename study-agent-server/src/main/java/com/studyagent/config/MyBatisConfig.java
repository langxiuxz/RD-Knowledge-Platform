package com.studyagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {
        "com.studyagent.auth.mapper",
        "com.studyagent.knowledge.mapper",
        "com.studyagent.conversation.mapper"
})
public class MyBatisConfig {
}
