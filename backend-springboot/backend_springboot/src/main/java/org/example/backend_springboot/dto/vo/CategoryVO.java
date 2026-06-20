package org.example.backend_springboot.dto.vo;

import lombok.Data;

@Data
public class CategoryVO {

    private Long id;
    private String name;
    private String type;
    private Long parentId;
    private Integer sortOrder;
}
