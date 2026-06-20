package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.util.Map;

@Data
public class PaperSmartComposeVO {

    private Long paperId;
    private PaperVO paper;
    private Integer matchedCount;
    private Map<String, Object> composeCondition;
    private String message;
}
