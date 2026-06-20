package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaperVO {

    private Long id;
    private String title;
    private String paperType;
    private BigDecimal totalScore;
    private Integer durationMinutes;
    private String subject;
    private String grade;
    private String composeMode;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<PaperQuestionVO> questions;
}
