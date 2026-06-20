package org.example.backend_springboot.dto.agent;

import lombok.Data;

import java.util.List;

@Data
public class ParseWordResponse {

    private String batchId;
    private List<ParseWordItemDTO> items;
    private List<ParseWordImageDTO> documentImages;
    private ParseWordSummaryDTO parseSummary;
}
