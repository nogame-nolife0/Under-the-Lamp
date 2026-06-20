package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaperSmartComposeRequest {

    @NotBlank(message = "试卷名称不能为空")
    private String title;

    @NotBlank(message = "请描述组卷需求")
    private String query;

    private String paperType = "EXAM";
    private String subject;
    private String grade;
    private Integer durationMinutes;
    private BigDecimal totalScore;
}
