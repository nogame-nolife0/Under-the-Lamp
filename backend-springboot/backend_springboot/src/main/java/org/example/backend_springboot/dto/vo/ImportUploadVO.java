package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.util.List;

@Data
public class ImportUploadVO {

    private String batchUuid;
    private String status;
    private String fileName;
    private String answerFileName;
    private String importMode;
    private String subject;
    private String grade;
    private Integer totalCount;
    private Integer needsReviewCount;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private List<ImportItemVO> items;
}
