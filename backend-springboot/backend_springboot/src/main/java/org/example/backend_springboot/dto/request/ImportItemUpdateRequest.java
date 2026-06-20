package org.example.backend_springboot.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ImportItemUpdateRequest {

    private String stemHtml;
    private String answerHtml;
    private String analysisHtml;
    private List<String> options;
    private String questionType;
    private String difficulty;
    private String chapter;
}
