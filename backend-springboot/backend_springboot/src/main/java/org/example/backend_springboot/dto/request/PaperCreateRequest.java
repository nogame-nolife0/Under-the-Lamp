package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class PaperCreateRequest {

    @NotBlank(message = "试卷名称不能为空")
    private String title;
    private String paperType = "EXAM";
    private String subject;
    private String grade;
    private Integer durationMinutes;
    private String composeMode = "MANUAL";
    private Map<String, Object> composeCondition;

    @NotEmpty(message = "请至少选择一道题目")
    private List<PaperQuestionItem> questions;

    @Data
    public static class PaperQuestionItem {
        private Long questionId;
        private Integer sortOrder;
        private BigDecimal score;
    }
}
