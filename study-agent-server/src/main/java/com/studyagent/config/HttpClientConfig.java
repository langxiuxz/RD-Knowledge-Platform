package com.studyagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient aiRestClient(RestClient.Builder builder, AppProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.ai().connectTimeout());
        requestFactory.setReadTimeout(properties.ai().readTimeout());
        return builder.baseUrl(properties.ai().baseUrl())
                .defaultHeader("X-Internal-Token", properties.internal().token())
                .requestFactory(requestFactory)
                .build();
    }
}
