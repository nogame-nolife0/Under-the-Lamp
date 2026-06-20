package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PaperSmartPreviewVO {

    private List<PaperQuestionVO> questions;
    private Integer matchedCount;
    private Integer ragMatchedCount;
    private Map<String, Object> composeCondition;
    private String message;
}
