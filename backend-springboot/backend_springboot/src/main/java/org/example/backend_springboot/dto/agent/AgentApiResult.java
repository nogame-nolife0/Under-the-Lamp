package org.example.backend_springboot.dto.agent;

import lombok.Data;

@Data
public class AgentApiResult<T> {

    private Integer code;
    private String msg;
    private T data;
}
