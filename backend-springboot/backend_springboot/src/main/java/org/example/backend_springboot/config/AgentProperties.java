package org.example.backend_springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.service")
public class AgentProperties {

    private String baseUrl = "http://localhost:8001";
    private int connectTimeout = 10000;
    private int readTimeout = 120000;
}
