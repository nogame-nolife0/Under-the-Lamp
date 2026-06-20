package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.example.backend_springboot.dto.agent.EmbedBatchItemDTO;
import org.example.backend_springboot.dto.agent.EmbedBatchRequest;
import org.example.backend_springboot.dto.agent.EmbedBatchResponse;
import org.example.backend_springboot.dto.vo.QuestionEmbedSyncVO;
import org.example.backend_springboot.entity.Question;
import org.example.backend_springboot.mapper.QuestionMapper;
import org.example.backend_springboot.service.AgentClientService;
import org.example.backend_springboot.service.QuestionEmbedService;
import org.example.backend_springboot.util.JsonUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class QuestionEmbedServiceImpl implements QuestionEmbedService {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final QuestionMapper questionMapper;
    private final AgentClientService agentClientService;

    public QuestionEmbedServiceImpl(QuestionMapper questionMapper,
                                      AgentClientService agentClientService) {
        this.questionMapper = questionMapper;
        this.agentClientService = agentClientService;
    }

    @Override
    @Async("taskExecutor")
    public void embedQuestionsAsync(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }
        try {
            syncQuestionIds(questionIds);
        } catch (Exception ex) {
            log.warn("异步向量同步失败: {}", ex.getMessage());
        }
    }

    @Override
    public QuestionEmbedSyncVO syncPendingQuestions(int batchSize) {
        int size = batchSize > 0 ? Math.min(batchSize, 200) : DEFAULT_BATCH_SIZE;
        List<Question> pending = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, "ACTIVE")
                .in(Question::getEmbedStatus, "PENDING", "FAILED")
                .orderByAsc(Question::getId)
                .last("LIMIT " + size));

        if (pending.isEmpty()) {
            QuestionEmbedSyncVO vo = new QuestionEmbedSyncVO();
            vo.setSyncedCount(0);
            vo.setFailedCount(0);
            vo.setPendingCount(0);
            vo.setMessage("没有待同步的题目");
            return vo;
        }

        EmbedBatchResponse response = syncQuestions(pending);
        int failed = response.getFailedIds() != null ? response.getFailedIds().size() : 0;
        int synced = response.getSuccessCount() != null ? response.getSuccessCount() : 0;

        long remaining = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, "ACTIVE")
                .in(Question::getEmbedStatus, "PENDING", "FAILED"));

        QuestionEmbedSyncVO vo = new QuestionEmbedSyncVO();
        vo.setSyncedCount(synced);
        vo.setFailedCount(failed);
        vo.setPendingCount((int) remaining);
        vo.setMessage("已同步 " + synced + " 道题，失败 " + failed + " 道，剩余待同步 " + remaining + " 道");
        return vo;
    }

    private void syncQuestionIds(List<Long> questionIds) {
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        if (questions.isEmpty()) {
            return;
        }
        syncQuestions(questions);
    }

    private EmbedBatchResponse syncQuestions(List<Question> questions) {
        EmbedBatchRequest request = new EmbedBatchRequest();
        List<EmbedBatchItemDTO> items = new ArrayList<>();
        for (Question question : questions) {
            EmbedBatchItemDTO item = new EmbedBatchItemDTO();
            item.setQuestionId(question.getId());
            item.setStem(question.getStem());
            item.setSubject(question.getSubject());
            item.setGrade(question.getGrade());
            item.setQuestionType(question.getQuestionType());
            item.setDifficulty(question.getDifficulty());
            item.setChapter(question.getChapter());
            item.setKnowledgePoints(JsonUtils.fromJson(
                    question.getKnowledgePoints(), new TypeReference<List<String>>() {
                    }));
            items.add(item);
        }
        request.setItems(items);

        EmbedBatchResponse response = agentClientService.embedBatch(request);
        List<Long> failedIds = response.getFailedIds() != null ? response.getFailedIds() : List.of();
        LocalDateTime now = LocalDateTime.now();
        for (Question question : questions) {
            Question update = new Question();
            update.setId(question.getId());
            if (failedIds.contains(question.getId())) {
                update.setEmbedStatus("FAILED");
            } else {
                update.setEmbedStatus("SYNCED");
                update.setEmbedSyncedAt(now);
            }
            questionMapper.updateById(update);
        }
        return response;
    }
}
