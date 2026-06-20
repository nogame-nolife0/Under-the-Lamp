package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.vo.QuestionEmbedSyncVO;

import java.util.List;

public interface QuestionEmbedService {

    void embedQuestionsAsync(List<Long> questionIds);

    QuestionEmbedSyncVO syncPendingQuestions(int batchSize);
}
