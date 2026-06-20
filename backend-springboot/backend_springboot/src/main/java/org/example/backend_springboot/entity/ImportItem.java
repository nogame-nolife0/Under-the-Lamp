package org.example.backend_springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("import_item")
public class ImportItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Integer seqNo;
    private String stemRaw;
    private String answerRaw;
    private String stemHtml;
    private String answerHtml;
    private String analysisHtml;
    private String optionsJson;
    private String questionType;
    private String difficulty;
    private String subject;
    private String grade;
    private String chapter;
    private String knowledgePoints;
    private BigDecimal confidenceScore;
    private String agentMeta;
    private String warnings;
    private String imagesJson;
    private String status;
    private Long questionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
