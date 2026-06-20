package org.example.backend_springboot.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate agentRestTemplate(RestTemplateBuilder builder, AgentProperties agentProperties) {
        return builder
                .connectTimeout(Duration.ofMillis(agentProperties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(agentProperties.getReadTimeout()))
                .build();
    }
}
