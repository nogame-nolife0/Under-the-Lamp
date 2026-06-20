package org.example.backend_springboot.dto.vo;

import lombok.Data;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ImportItemVO {

    private Long id;
    private Long batchId;
    private Integer seqNo;
    private String stemRaw;
    private String answerRaw;
    private String stemHtml;
    private String answerHtml;
    private String analysisHtml;
    private List<String> options;
    private String questionType;
    private String difficulty;
    private String subject;
    private String grade;
    private String chapter;
    private List<String> knowledgePoints;
    private BigDecimal confidenceScore;
    private List<String> warnings;
    private List<ParseWordImageDTO> images;
    private String status;
    private Long questionId;
}
