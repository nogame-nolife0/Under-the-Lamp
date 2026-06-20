package org.example.backend_springboot.dto.agent;

import lombok.Data;

@Data
public class RagSearchRequest {

    private String query;
    private String subject;
    private String grade;
    private Integer limit;
}
