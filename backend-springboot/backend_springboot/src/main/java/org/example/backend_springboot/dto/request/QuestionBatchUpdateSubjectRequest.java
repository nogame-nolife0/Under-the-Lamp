package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QuestionBatchUpdateSubjectRequest {

    @NotEmpty(message = "请至少选择一道题目")
    private List<Long> ids;

    @NotBlank(message = "请填写课程名称")
    private String subject;
}
