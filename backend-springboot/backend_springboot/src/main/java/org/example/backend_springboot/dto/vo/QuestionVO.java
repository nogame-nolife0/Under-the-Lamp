package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuestionVO {

    private Long id;
    private String stem;
    private List<String> options;
    private String answer;
    private String analysis;
    private String questionType;
    private String difficulty;
    private String subject;
    private String grade;
    private String chapter;
    private List<String> knowledgePoints;
    private BigDecimal scoreDefault;
    private String status;
    private String embedStatus;
    private List<QuestionImageVO> images;
}
