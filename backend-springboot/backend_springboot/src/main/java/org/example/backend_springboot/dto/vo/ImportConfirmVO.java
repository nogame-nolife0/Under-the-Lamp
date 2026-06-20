package org.example.backend_springboot.dto.vo;

import lombok.Data;

@Data
public class ImportConfirmVO {

    private String batchUuid;
    private String status;
    private Integer importedCount;
    private Integer skippedCount;
}
