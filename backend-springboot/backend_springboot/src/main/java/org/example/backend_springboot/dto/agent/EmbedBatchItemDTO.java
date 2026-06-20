package org.example.backend_springboot.dto.agent;

import lombok.Data;

import java.util.List;

@Data
public class EmbedBatchItemDTO {

    private Long questionId;
    private String stem;
    private String subject;
    private String grade;
    private String questionType;
    private String difficulty;
    private String chapter;
    private List<String> knowledgePoints;
}
