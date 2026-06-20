package org.example.backend_springboot.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class QuestionUpdateRequest {

    private String stem;
    private String answer;
    private String analysis;
    private List<String> options;
    private String questionType;
    private String difficulty;
    private String subject;
    private String chapter;
}
