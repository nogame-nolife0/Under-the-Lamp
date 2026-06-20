package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaperSmartPreviewRequest {

    @NotBlank(message = "请描述组卷需求")
    private String query;

    private String subject;
    private String grade;
    private BigDecimal totalScore;
}
