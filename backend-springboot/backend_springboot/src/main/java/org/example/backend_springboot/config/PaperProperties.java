package org.example.backend_springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "paper")
public class PaperProperties {

    private Upload upload = new Upload();
    private Export export = new Export();

    @Data
    public static class Upload {
        private String baseDir;
    }

    @Data
    public static class Export {
        private String baseDir;
    }
}
