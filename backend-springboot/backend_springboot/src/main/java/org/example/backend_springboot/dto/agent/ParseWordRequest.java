package org.example.backend_springboot.dto.agent;

import lombok.Data;

@Data
public class ParseWordRequest {

    private String batchId;
    private String filePath;
    private String subject;
    private String grade;
    /** STEM=仅题干, ANSWER=仅答案, AUTO=自动（合一卷） */
    private String parseMode;
    private ParseWordHintsDTO hints;
}
