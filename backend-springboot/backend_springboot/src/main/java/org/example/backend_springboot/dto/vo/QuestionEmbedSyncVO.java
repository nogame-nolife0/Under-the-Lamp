package org.example.backend_springboot.dto.vo;

import lombok.Data;

@Data
public class QuestionEmbedSyncVO {

    private Integer syncedCount;
    private Integer failedCount;
    private Integer pendingCount;
    private String message;
}
