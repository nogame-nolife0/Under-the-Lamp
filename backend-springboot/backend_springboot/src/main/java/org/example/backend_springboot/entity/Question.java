package org.example.backend_springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String stem;
    private String optionsJson;
    private String answer;
    private String analysis;
    private String questionType;
    private String difficulty;
    private String subject;
    private String grade;
    private String chapter;
    private String knowledgePoints;
    private BigDecimal scoreDefault;
    private Long sourceBatchId;
    private Long sourceItemId;
    private BigDecimal confidenceSnapshot;
    private String imagesJson;
    private String embedStatus;
    private LocalDateTime embedSyncedAt;
    private String status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
