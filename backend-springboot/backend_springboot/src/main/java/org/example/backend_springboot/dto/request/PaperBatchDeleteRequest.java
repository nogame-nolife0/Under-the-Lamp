package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PaperBatchDeleteRequest {

    @NotEmpty(message = "请至少选择一份试卷")
    private List<Long> ids;
}
