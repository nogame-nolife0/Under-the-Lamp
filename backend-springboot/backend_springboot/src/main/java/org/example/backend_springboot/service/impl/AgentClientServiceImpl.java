package org.example.backend_springboot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.config.AgentProperties;
import org.example.backend_springboot.dto.agent.AgentApiResult;
import org.example.backend_springboot.dto.agent.EmbedBatchRequest;
import org.example.backend_springboot.dto.agent.EmbedBatchResponse;
import org.example.backend_springboot.dto.agent.ParseWordRequest;
import org.example.backend_springboot.dto.agent.ParseWordResponse;
import org.example.backend_springboot.dto.agent.RagSearchRequest;
import org.example.backend_springboot.dto.agent.RagSearchResponse;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.service.AgentClientService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class AgentClientServiceImpl implements AgentClientService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AgentClientServiceImpl(AgentProperties agentProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(agentProperties.getConnectTimeout());
        requestFactory.setReadTimeout(agentProperties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ParseWordResponse parseWord(ParseWordRequest request) {
        return postAgent("/api/v1/parse-word", request, ParseWordResponse.class, "Agent 解析失败");
    }

    @Override
    public RagSearchResponse ragSearch(RagSearchRequest request) {
        return postAgent("/api/v1/rag/search", request, RagSearchResponse.class, "RAG 检索失败");
    }

    @Override
    public EmbedBatchResponse embedBatch(EmbedBatchRequest request) {
        return postAgent("/api/v1/embed/batch", request, EmbedBatchResponse.class, "向量入库失败");
    }

    private <T> T postAgent(String uri, Object request, Class<T> dataType, String defaultError) {
        try {
            log.info("调用 Agent {}, 请求体: {}", uri, objectMapper.writeValueAsString(request));

            AgentApiResult<T> body = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<AgentApiResult<T>>() {
                    });

            if (body == null) {
                throw new BusinessException(BizCode.AGENT_PARSE_FAILED, "Agent 返回为空");
            }
            if (body.getCode() == null || body.getCode() != 200) {
                throw new BusinessException(
                        body.getCode() != null ? body.getCode() : BizCode.AGENT_PARSE_FAILED.getCode(),
                        body.getMsg() != null ? body.getMsg() : defaultError
                );
            }
            if (body.getData() == null) {
                throw new BusinessException(BizCode.AGENT_PARSE_FAILED, defaultError + "：返回 data 为空");
            }
            return objectMapper.convertValue(body.getData(), dataType);
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(BizCode.AGENT_PARSE_FAILED,
                    "Agent 接口异常: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new BusinessException(BizCode.AGENT_TIMEOUT, "Agent 服务连接失败，请确认 python run.py 已启动");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(BizCode.AGENT_PARSE_FAILED, "调用 Agent 失败: " + ex.getMessage());
        }
    }
}
