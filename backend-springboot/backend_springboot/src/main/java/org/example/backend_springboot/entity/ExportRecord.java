package org.example.backend_springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("export_record")
public class ExportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String exportType;
    private String fileFormat;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
