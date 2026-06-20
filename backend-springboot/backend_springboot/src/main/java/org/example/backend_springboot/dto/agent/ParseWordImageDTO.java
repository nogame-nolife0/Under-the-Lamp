package org.example.backend_springboot.dto.agent;

import lombok.Data;

@Data
public class ParseWordImageDTO {

    private Integer index;
    private String placeholder;
    private String position;
    private String filePath;
    private String displayPath;
    private String scope;
    private String url;
}
