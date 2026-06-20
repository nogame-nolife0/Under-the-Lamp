package org.example.backend_springboot.dto.agent;

import lombok.Data;

@Data
public class ParseWordSummaryDTO {

    private Integer total;
    private Integer highConfidence;
    private Integer needsReview;
}
