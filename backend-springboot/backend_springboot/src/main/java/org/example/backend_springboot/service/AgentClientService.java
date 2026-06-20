package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.agent.EmbedBatchRequest;
import org.example.backend_springboot.dto.agent.EmbedBatchResponse;
import org.example.backend_springboot.dto.agent.ParseWordRequest;
import org.example.backend_springboot.dto.agent.ParseWordResponse;
import org.example.backend_springboot.dto.agent.RagSearchRequest;
import org.example.backend_springboot.dto.agent.RagSearchResponse;

public interface AgentClientService {

    ParseWordResponse parseWord(ParseWordRequest request);

    RagSearchResponse ragSearch(RagSearchRequest request);

    EmbedBatchResponse embedBatch(EmbedBatchRequest request);
}
