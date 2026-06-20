package org.example.backend_springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_batch")
public class ImportBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchUuid;
    private String fileName;
    private String filePath;
    private String answerFileName;
    private String answerFilePath;
    private String fileHash;
    private Long fileSize;
    private String subject;
    private String grade;
    private String importMode;
    private String status;
    private Integer totalCount;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private Integer needsReviewCount;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
