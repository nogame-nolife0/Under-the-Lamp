package org.example.backend_springboot.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class PaperSmartAlternativeRequest {

    private String subject;
    private String questionType;
    private String chapter;
    private String keywords;
    private List<Long> excludeIds;
    private Integer limit = 10;
}
