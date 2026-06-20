package org.example.backend_springboot.dto.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ParseWordItemDTO {

    private Integer seqNo;
    private String questionLabel;
    private String stemRaw;
    private String answerRaw;
    private String analysisRaw;
    private List<String> options;
    private String questionType;
    private String difficulty;
    private String chapter;
    private List<String> knowledgePoints;
    private BigDecimal confidenceScore;
    private List<String> warnings;
    private List<ParseWordImageDTO> images;
    private Map<String, Object> agentMeta;
}
