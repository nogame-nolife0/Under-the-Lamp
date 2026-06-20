package org.example.backend_springboot.dto.vo;

import lombok.Data;

import java.util.List;

@Data
public class PaperDeleteVO {

    private int deletedCount;
    private int skippedCount;
    private List<Long> skippedIds;
    private String message;
}
