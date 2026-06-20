package org.example.backend_springboot.dto.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RagSearchResponse {

    private List<Long> questionIds;
    private Integer matchedCount;
    private Map<String, Object> composeCondition;
}
