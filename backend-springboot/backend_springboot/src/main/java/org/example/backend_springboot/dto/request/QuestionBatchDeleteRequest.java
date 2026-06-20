package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QuestionBatchDeleteRequest {

    @NotEmpty(message = "请至少选择一道题目")
    private List<Long> ids;
}
