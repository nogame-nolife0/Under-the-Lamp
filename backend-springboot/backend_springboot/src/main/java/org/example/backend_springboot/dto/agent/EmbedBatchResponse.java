package org.example.backend_springboot.dto.agent;

import lombok.Data;

import java.util.List;

@Data
public class EmbedBatchResponse {

    private Integer successCount;
    private List<Long> failedIds;
}
