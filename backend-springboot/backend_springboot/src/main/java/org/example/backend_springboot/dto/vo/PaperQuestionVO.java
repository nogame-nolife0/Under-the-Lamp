package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperQuestionVO {

    private Long questionId;
    private Integer sortOrder;
    private BigDecimal score;
    private String stem;
    private String answer;
    private String analysis;
    private List<String> options;
    private String questionType;
    private String imagesJson;
    private List<QuestionImageVO> images;
}
