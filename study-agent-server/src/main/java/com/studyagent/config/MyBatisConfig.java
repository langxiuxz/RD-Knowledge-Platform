package com.studyagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {
        "com.studyagent.auth.mapper",
        "com.studyagent.knowledge.mapper",
        "com.studyagent.conversation.mapper",
        "com.studyagent.document.mapper",
        "com.studyagent.dashboard.mapper",
        "com.studyagent.internal.mapper",
        "com.studyagent.learning.mapper",
        "com.studyagent.plan.mapper",
        "com.studyagent.quiz.mapper",
        "com.studyagent.wrong.mapper"
})
public class MyBatisConfig {
}
