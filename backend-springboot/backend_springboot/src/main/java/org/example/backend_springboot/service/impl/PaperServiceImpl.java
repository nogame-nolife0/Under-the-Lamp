package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.common.PageResult;
import org.example.backend_springboot.dto.agent.RagSearchRequest;
import org.example.backend_springboot.dto.agent.RagSearchResponse;
import org.example.backend_springboot.dto.request.PaperBatchDeleteRequest;
import org.example.backend_springboot.dto.request.PaperCreateRequest;
import org.example.backend_springboot.dto.request.PaperSmartAlternativeRequest;
import org.example.backend_springboot.dto.request.PaperSmartComposeRequest;
import org.example.backend_springboot.dto.request.PaperSmartPreviewRequest;
import org.example.backend_springboot.dto.vo.PaperDeleteVO;
import org.example.backend_springboot.dto.vo.PaperQuestionVO;
import org.example.backend_springboot.dto.vo.PaperSmartComposeVO;
import org.example.backend_springboot.dto.vo.PaperSmartPreviewVO;
import org.example.backend_springboot.dto.vo.PaperVO;
import org.example.backend_springboot.dto.vo.QuestionVO;
import org.example.backend_springboot.entity.ExportRecord;
import org.example.backend_springboot.entity.Paper;
import org.example.backend_springboot.entity.PaperQuestion;
import org.example.backend_springboot.entity.Question;
import org.example.backend_springboot.entity.SysUser;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.ExportRecordMapper;
import org.example.backend_springboot.mapper.PaperMapper;
import org.example.backend_springboot.mapper.PaperQuestionMapper;
import org.example.backend_springboot.mapper.QuestionMapper;
import org.example.backend_springboot.mapper.SysUserMapper;
import org.example.backend_springboot.service.AgentClientService;
import org.example.backend_springboot.service.ImageAssetService;
import org.example.backend_springboot.service.PaperService;
import org.example.backend_springboot.service.QuestionService;
import org.example.backend_springboot.util.AuthContext;
import org.example.backend_springboot.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaperServiceImpl implements PaperService {

    private static final List<String> QUESTION_TYPE_ORDER = List.of(
            "SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE", "FILL_BLANK",
            "SHORT_ANSWER", "CALCULATION", "ESSAY", "UNKNOWN"
    );

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final ExportRecordMapper exportRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final AgentClientService agentClientService;
    private final QuestionService questionService;
    private final ImageAssetService imageAssetService;

    public PaperServiceImpl(PaperMapper paperMapper,
                            PaperQuestionMapper paperQuestionMapper,
                            QuestionMapper questionMapper,
                            ExportRecordMapper exportRecordMapper,
                            SysUserMapper sysUserMapper,
                            AgentClientService agentClientService,
                            QuestionService questionService,
                            ImageAssetService imageAssetService) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
        this.exportRecordMapper = exportRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.agentClientService = agentClientService;
        this.questionService = questionService;
        this.imageAssetService = imageAssetService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPaper(PaperCreateRequest request) {
        List<PaperCreateRequest.PaperQuestionItem> items = request.getQuestions();
        Map<Long, Question> questionMap = loadActiveQuestions(items);

        BigDecimal totalScore = BigDecimal.ZERO;
        for (PaperCreateRequest.PaperQuestionItem item : items) {
            Question question = questionMap.get(item.getQuestionId());
            BigDecimal score = item.getScore() != null ? item.getScore()
                    : (question.getScoreDefault() != null ? question.getScoreDefault() : BigDecimal.ONE);
            totalScore = totalScore.add(score);
        }

        Paper paper = new Paper();
        paper.setTitle(request.getTitle());
        paper.setPaperType(request.getPaperType() != null ? request.getPaperType() : "EXAM");
        paper.setSubject(request.getSubject());
        paper.setGrade(request.getGrade());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setComposeMode(request.getComposeMode() != null ? request.getComposeMode() : "MANUAL");
        if (request.getComposeCondition() != null && !request.getComposeCondition().isEmpty()) {
            paper.setComposeCondition(JsonUtils.toJson(request.getComposeCondition()));
        }
        paper.setTotalScore(totalScore);
        paper.setStatus("DRAFT");
        paper.setCreatedBy(resolveCurrentUsername());
        paper.setCreatedAt(LocalDateTime.now());
        paper.setUpdatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int order = 1;
        for (PaperCreateRequest.PaperQuestionItem item : items) {
            Question question = questionMap.get(item.getQuestionId());
            BigDecimal score = item.getScore() != null ? item.getScore()
                    : (question.getScoreDefault() != null ? question.getScoreDefault() : BigDecimal.ONE);

            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(item.getQuestionId());
            pq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            pq.setScore(score);
            pq.setCreatedAt(LocalDateTime.now());
            paperQuestionMapper.insert(pq);
            order++;
        }

        return paper.getId();
    }

    @Override
    public PaperVO getById(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "试卷不存在");
        }
        return toVO(paper, true);
    }

    @Override
    public PageResult<PaperVO> pagePapers(Long pageNum, Long pageSize, String keyword) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Paper::getTitle, keyword);
        }
        wrapper.orderByDesc(Paper::getCreatedAt);

        Page<Paper> page = paperMapper.selectPage(new Page<>(current, size), wrapper);
        List<PaperVO> list = page.getRecords().stream()
                .map(p -> toVO(p, false))
                .toList();
        return new PageResult<>(list, page.getTotal(), current, size);
    }

    @Override
    public PaperSmartPreviewVO smartPreview(PaperSmartPreviewRequest request) {
        SmartSearchResult searchResult = resolveSmartQuestions(
                request.getQuery(),
                request.getSubject(),
                request.getGrade(),
                request.getTotalScore());
        PaperSmartPreviewVO vo = new PaperSmartPreviewVO();
        vo.setQuestions(searchResult.questions());
        vo.setMatchedCount(searchResult.questionIds().size());
        vo.setRagMatchedCount(searchResult.ragMatchedCount());
        vo.setComposeCondition(searchResult.composeCondition());
        vo.setMessage(searchResult.message());
        return vo;
    }

    @Override
    public List<QuestionVO> smartAlternatives(PaperSmartAlternativeRequest request) {
        int limit = request.getLimit() != null ? Math.max(1, Math.min(request.getLimit(), 20)) : 10;
        Map<String, Object> condition = new HashMap<>();
        condition.put("subject", request.getSubject());
        condition.put("questionType", request.getQuestionType());
        condition.put("chapter", request.getChapter());
        condition.put("keywords", request.getKeywords());

        List<Long> excludeIds = request.getExcludeIds() != null ? request.getExcludeIds() : List.of();
        List<Long> ids = searchFallbackQuestions(condition, limit, excludeIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return questionService.listByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperSmartComposeVO smartCompose(PaperSmartComposeRequest request) {
        SmartSearchResult searchResult = resolveSmartQuestions(
                request.getQuery(),
                request.getSubject(),
                request.getGrade(),
                request.getTotalScore());

        List<PaperCreateRequest.PaperQuestionItem> items = new ArrayList<>();
        for (PaperQuestionVO question : searchResult.questions()) {
            PaperCreateRequest.PaperQuestionItem item = new PaperCreateRequest.PaperQuestionItem();
            item.setQuestionId(question.getQuestionId());
            item.setSortOrder(question.getSortOrder());
            item.setScore(question.getScore());
            items.add(item);
        }

        PaperCreateRequest createRequest = new PaperCreateRequest();
        createRequest.setTitle(request.getTitle());
        createRequest.setPaperType(request.getPaperType());
        Map<String, Object> composeCondition = searchResult.composeCondition();
        createRequest.setSubject(firstNotBlank(request.getSubject(), stringValue(composeCondition.get("subject"))));
        createRequest.setGrade(firstNotBlank(request.getGrade(), stringValue(composeCondition.get("grade"))));
        createRequest.setDurationMinutes(request.getDurationMinutes());
        createRequest.setComposeMode("SMART");
        createRequest.setComposeCondition(composeCondition);
        createRequest.setQuestions(items);

        Long paperId = createPaper(createRequest);

        PaperSmartComposeVO vo = new PaperSmartComposeVO();
        vo.setPaperId(paperId);
        vo.setPaper(getById(paperId));
        vo.setMatchedCount(searchResult.questionIds().size());
        vo.setComposeCondition(composeCondition);
        vo.setMessage(searchResult.message());
        return vo;
    }

    private SmartSearchResult resolveSmartQuestions(String query,
                                                    String subject,
                                                    String grade,
                                                    BigDecimal totalScore) {
        RagSearchRequest ragRequest = new RagSearchRequest();
        ragRequest.setQuery(query);
        ragRequest.setSubject(firstNotBlank(subject, null));
        ragRequest.setGrade(firstNotBlank(grade, null));

        RagSearchResponse ragResponse = agentClientService.ragSearch(ragRequest);
        Map<String, Object> composeCondition = ragResponse.getComposeCondition() != null
                ? new HashMap<>(ragResponse.getComposeCondition()) : new HashMap<>();

        Map<String, Integer> typeCounts = parseTypeCounts(composeCondition);
        int requestedCount = typeCounts != null
                ? typeCounts.values().stream().mapToInt(Integer::intValue).sum()
                : resolveRequestedCount(composeCondition);
        int ragMatchedCount = ragResponse.getMatchedCount() != null ? ragResponse.getMatchedCount() : 0;
        int maxPerChapter = resolveMaxPerChapter(composeCondition);

        List<Long> ragPool = ragResponse.getQuestionIds() != null
                ? new ArrayList<>(ragResponse.getQuestionIds()) : new ArrayList<>();
        Map<Long, Question> questionMap = loadQuestionMap(ragPool);

        List<Long> questionIds;
        if (typeCounts != null) {
            questionIds = resolveByTypeCounts(typeCounts, composeCondition, ragPool, questionMap, maxPerChapter);
            composeCondition.put("typeCounts", typeCounts);
            composeCondition.put("count", requestedCount);
        } else {
            questionIds = resolveSingleTypeQuestions(
                    composeCondition, ragPool, questionMap, requestedCount, maxPerChapter);
        }

        if (questionIds.isEmpty()) {
            throw new BusinessException(BizCode.QUESTION_NOT_ENOUGH,
                    "未找到匹配题目，请先导入题库并点击「同步向量库」");
        }

        questionMap = loadQuestionMap(questionIds);
        if (typeCounts != null) {
            questionIds = sortIdsByTypeCounts(questionIds, typeCounts, questionMap);
        }

        List<PaperQuestionVO> previewItems = buildPreviewItems(questionIds, questionMap, totalScore);

        if (previewItems.isEmpty()) {
            throw new BusinessException(BizCode.QUESTION_NOT_ENOUGH, "匹配题目不存在或已下架");
        }

        String message = buildSmartSearchMessage(typeCounts, ragMatchedCount, previewItems.size());
        return new SmartSearchResult(questionIds, previewItems, composeCondition, ragMatchedCount, message);
    }

    private List<Long> resolveSingleTypeQuestions(Map<String, Object> composeCondition,
                                                  List<Long> ragPool,
                                                  Map<Long, Question> questionMap,
                                                  int requestedCount,
                                                  int maxPerChapter) {
        List<Long> candidates = new ArrayList<>(ragPool);
        List<Long> excludeIds = new ArrayList<>(candidates);
        while (candidates.size() < requestedCount) {
            List<Long> more = searchFallbackQuestions(
                    composeCondition, requestedCount - candidates.size(), excludeIds);
            if (more.isEmpty()) {
                break;
            }
            for (Long id : more) {
                if (!candidates.contains(id)) {
                    candidates.add(id);
                    excludeIds.add(id);
                    questionMap.put(id, loadActiveQuestion(id));
                }
            }
        }
        return selectWithChapterDedup(candidates, questionMap, requestedCount, maxPerChapter);
    }

    private List<Long> resolveByTypeCounts(Map<String, Integer> typeCounts,
                                           Map<String, Object> composeCondition,
                                           List<Long> ragPool,
                                           Map<Long, Question> questionMap,
                                           int maxPerChapter) {
        List<Long> selected = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String questionType = entry.getKey();
            int needed = entry.getValue();

            List<Long> typeCandidates = ragPool.stream()
                    .filter(id -> matchesQuestionType(questionMap.get(id), questionType))
                    .toList();
            List<Long> typeSelected = selectWithChapterDedup(
                    typeCandidates, questionMap, needed, maxPerChapter, selected);

            List<Long> excludeIds = new ArrayList<>(selected);
            excludeIds.addAll(ragPool);
            excludeIds.addAll(typeSelected);
            int attempts = 0;
            while (typeSelected.size() < needed && attempts < 3) {
                Map<String, Object> subCondition = new HashMap<>(composeCondition);
                subCondition.put("questionType", questionType);
                subCondition.put("count", needed);
                List<Long> more = searchFallbackQuestions(
                        subCondition, (needed - typeSelected.size()) * 2, excludeIds);
                if (more.isEmpty()) {
                    break;
                }
                for (Long id : more) {
                    Question question = questionMap.computeIfAbsent(id, this::loadActiveQuestion);
                    if (!matchesQuestionType(question, questionType)) {
                        continue;
                    }
                    if (!canAddWithChapterLimit(typeSelected, selected, question, maxPerChapter)) {
                        continue;
                    }
                    typeSelected.add(id);
                    excludeIds.add(id);
                    if (typeSelected.size() >= needed) {
                        break;
                    }
                }
                attempts++;
            }

            int take = Math.min(needed, typeSelected.size());
            selected.addAll(typeSelected.subList(0, take));
        }
        return selected;
    }

    private List<PaperQuestionVO> buildPreviewItems(List<Long> questionIds,
                                                    Map<Long, Question> questionMap,
                                                    BigDecimal totalScore) {
        List<PaperQuestionVO> previewItems = new ArrayList<>();
        int order = 1;
        for (Long questionId : questionIds) {
            Question question = questionMap.get(questionId);
            if (question == null) {
                continue;
            }
            PaperQuestionVO item = new PaperQuestionVO();
            item.setQuestionId(question.getId());
            item.setSortOrder(order++);
            item.setStem(question.getStem());
            item.setAnswer(question.getAnswer());
            item.setAnalysis(question.getAnalysis());
            item.setQuestionType(question.getQuestionType());
            item.setOptions(JsonUtils.fromJson(question.getOptionsJson(), new TypeReference<List<String>>() {
            }));
            item.setImagesJson(question.getImagesJson());
            item.setImages(imageAssetService.toQuestionImageVOs(question.getImagesJson()));
            previewItems.add(item);
        }
        assignDistributedScores(previewItems, totalScore);
        return previewItems;
    }

    /** 平均分配分值，末题承接余数，保证合计等于目标总分 */
    private void assignDistributedScores(List<PaperQuestionVO> items, BigDecimal totalScore) {
        int count = items.size();
        if (count == 0) {
            return;
        }
        if (totalScore == null) {
            items.forEach(item -> item.setScore(BigDecimal.ONE));
            return;
        }
        BigDecimal perScore = totalScore.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < count - 1; i++) {
            items.get(i).setScore(perScore);
            assigned = assigned.add(perScore);
        }
        items.get(count - 1).setScore(totalScore.subtract(assigned));
    }

    private String buildSmartSearchMessage(Map<String, Integer> typeCounts,
                                           int ragMatchedCount,
                                           int total) {
        if (typeCounts != null) {
            String ratio = typeCounts.entrySet().stream()
                    .map(entry -> questionTypeLabel(entry.getKey()) + entry.getValue() + "题")
                    .collect(Collectors.joining(" + "));
            return ragMatchedCount > 0
                    ? "已按题型比例（" + ratio + "）检索 " + total + " 题，请预览确认"
                    : "已按题型比例（" + ratio + "）从题库检索 " + total + " 题，请预览确认";
        }
        return ragMatchedCount > 0
                ? "已通过 RAG 检索匹配 " + total + " 题（已尽量分散章节），请预览确认"
                : "向量库暂无匹配，已使用题库条件检索 " + total + " 题，请预览确认";
    }

    private String questionTypeLabel(String type) {
        return switch (type) {
            case "SINGLE_CHOICE" -> "单选";
            case "MULTI_CHOICE" -> "多选";
            case "TRUE_FALSE" -> "判断";
            case "FILL_BLANK" -> "填空";
            case "SHORT_ANSWER" -> "简答";
            case "CALCULATION" -> "计算";
            case "ESSAY" -> "论述";
            default -> type != null ? type : "题目";
        };
    }

    private Map<Long, Question> loadQuestionMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return questionMapper.selectBatchIds(ids).stream()
                .filter(question -> "ACTIVE".equals(question.getStatus()))
                .collect(Collectors.toMap(Question::getId, question -> question, (a, b) -> a, HashMap::new));
    }

    private Question loadActiveQuestion(Long id) {
        if (id == null) {
            return null;
        }
        Question question = questionMapper.selectById(id);
        if (question == null || !"ACTIVE".equals(question.getStatus())) {
            return null;
        }
        return question;
    }

    private boolean matchesQuestionType(Question question, String questionType) {
        return question != null && questionType != null && questionType.equals(question.getQuestionType());
    }

    private List<Long> selectWithChapterDedup(List<Long> candidates,
                                              Map<Long, Question> questionMap,
                                              int limit,
                                              int maxPerChapter) {
        return selectWithChapterDedup(candidates, questionMap, limit, maxPerChapter, List.of());
    }

    private List<Long> selectWithChapterDedup(List<Long> candidates,
                                              Map<Long, Question> questionMap,
                                              int limit,
                                              int maxPerChapter,
                                              List<Long> existingSelected) {
        Map<String, Integer> chapterCount = countChapters(existingSelected, questionMap);
        List<Long> selected = new ArrayList<>();
        for (Long id : candidates) {
            Question question = questionMap.computeIfAbsent(id, this::loadActiveQuestion);
            if (question == null || selected.contains(id) || existingSelected.contains(id)) {
                continue;
            }
            String chapterKey = chapterKey(question);
            int used = chapterCount.getOrDefault(chapterKey, 0);
            if (used >= maxPerChapter) {
                continue;
            }
            chapterCount.put(chapterKey, used + 1);
            selected.add(id);
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }

    private boolean canAddWithChapterLimit(List<Long> currentTypeSelected,
                                           List<Long> allSelected,
                                           Question question,
                                           int maxPerChapter) {
        if (question == null) {
            return false;
        }
        String chapterKey = chapterKey(question);
        int used = 0;
        for (Long id : allSelected) {
            Question q = loadActiveQuestion(id);
            if (q != null && chapterKey.equals(chapterKey(q))) {
                used++;
            }
        }
        for (Long id : currentTypeSelected) {
            Question q = loadActiveQuestion(id);
            if (q != null && chapterKey.equals(chapterKey(q))) {
                used++;
            }
        }
        return used < maxPerChapter;
    }

    private Map<String, Integer> countChapters(List<Long> ids, Map<Long, Question> questionMap) {
        Map<String, Integer> chapterCount = new HashMap<>();
        for (Long id : ids) {
            Question question = questionMap.computeIfAbsent(id, this::loadActiveQuestion);
            if (question == null) {
                continue;
            }
            String chapterKey = chapterKey(question);
            chapterCount.put(chapterKey, chapterCount.getOrDefault(chapterKey, 0) + 1);
        }
        return chapterCount;
    }

    private String chapterKey(Question question) {
        if (question.getChapter() != null && !question.getChapter().isBlank()) {
            return question.getChapter().trim();
        }
        return "__none__" + question.getId();
    }

    private List<Long> sortIdsByTypeCounts(List<Long> questionIds,
                                           Map<String, Integer> typeCounts,
                                           Map<Long, Question> questionMap) {
        Map<String, Integer> typeOrder = new HashMap<>();
        int index = 0;
        for (String type : QUESTION_TYPE_ORDER) {
            if (typeCounts.containsKey(type)) {
                typeOrder.put(type, index++);
            }
        }
        for (String type : typeCounts.keySet()) {
            typeOrder.putIfAbsent(type, index++);
        }
        return questionIds.stream()
                .sorted(Comparator
                        .comparingInt((Long id) -> {
                            Question question = questionMap.get(id);
                            String type = question != null ? question.getQuestionType() : "UNKNOWN";
                            return typeOrder.getOrDefault(type, 99);
                        })
                        .thenComparingLong(id -> id))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseTypeCounts(Map<String, Object> composeCondition) {
        Object raw = composeCondition.get("typeCounts");
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String type = stringValue(entry.getKey());
            if (type == null) {
                continue;
            }
            int count = parsePositiveInt(entry.getValue());
            if (count > 0) {
                typeCounts.put(type, count);
            }
        }
        return typeCounts.isEmpty() ? null : typeCounts;
    }

    private int parsePositiveInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int resolveMaxPerChapter(Map<String, Object> composeCondition) {
        Object raw = composeCondition.get("maxPerChapter");
        int value = parsePositiveInt(raw);
        return value > 0 ? value : 1;
    }

    private record SmartSearchResult(List<Long> questionIds,
                                     List<PaperQuestionVO> questions,
                                     Map<String, Object> composeCondition,
                                     int ragMatchedCount,
                                     String message) {
    }

    private List<Long> searchFallbackQuestions(Map<String, Object> composeCondition,
                                               int limit,
                                               List<Long> excludeIds) {
        if (limit <= 0) {
            return List.of();
        }
        String subject = stringValue(composeCondition.get("subject"));
        String grade = stringValue(composeCondition.get("grade"));
        String questionType = stringValue(composeCondition.get("questionType"));
        String difficulty = stringValue(composeCondition.get("difficulty"));
        String chapter = stringValue(composeCondition.get("chapter"));
        String keywords = stringValue(composeCondition.get("keywords"));

        List<Long> found = questionService.searchIdsByCondition(
                subject, grade, questionType, difficulty, chapter, keywords, limit, excludeIds);
        if (!found.isEmpty()) {
            return found;
        }
        found = questionService.searchIdsByCondition(
                subject, grade, null, null, chapter, keywords, limit, excludeIds);
        if (!found.isEmpty()) {
            return found;
        }
        found = questionService.searchIdsByCondition(
                subject, grade, null, null, null, keywords, limit, excludeIds);
        if (!found.isEmpty()) {
            return found;
        }
        found = questionService.searchIdsByCondition(
                null, null, null, null, null, keywords, limit, excludeIds);
        if (!found.isEmpty()) {
            return found;
        }
        return questionService.listRecentActiveIds(limit, excludeIds);
    }

    private int resolveRequestedCount(Map<String, Object> composeCondition) {
        Object count = composeCondition.get("count");
        if (count instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), 30));
        }
        if (count != null) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(count.toString()), 30));
            } catch (NumberFormatException ignored) {
                return 10;
            }
        }
        return 10;
    }


    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDeleteVO batchDelete(PaperBatchDeleteRequest request) {
        List<Long> ids = request.getIds().stream().distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请至少选择一份试卷");
        }

        List<Long> skippedIds = new ArrayList<>();
        int deletedCount = 0;

        for (Long id : ids) {
            Paper paper = paperMapper.selectById(id);
            if (paper == null) {
                skippedIds.add(id);
                continue;
            }
            deleteExportFiles(id);
            paperMapper.deleteById(id);
            deletedCount++;
        }

        PaperDeleteVO vo = new PaperDeleteVO();
        vo.setDeletedCount(deletedCount);
        vo.setSkippedCount(skippedIds.size());
        vo.setSkippedIds(skippedIds);
        if (deletedCount == 0) {
            vo.setMessage("没有试卷被删除，可能不存在");
        } else if (!skippedIds.isEmpty()) {
            vo.setMessage("已删除 " + deletedCount + " 份试卷，跳过 " + skippedIds.size() + " 份（不存在）");
        } else {
            vo.setMessage("已删除 " + deletedCount + " 份试卷");
        }
        return vo;
    }

    private void deleteExportFiles(Long paperId) {
        List<ExportRecord> records = exportRecordMapper.selectList(
                new LambdaQueryWrapper<ExportRecord>().eq(ExportRecord::getPaperId, paperId));
        for (ExportRecord record : records) {
            if (record.getFilePath() == null || record.getFilePath().isBlank()) {
                continue;
            }
            try {
                Files.deleteIfExists(Paths.get(record.getFilePath()));
            } catch (IOException ignored) {
                // 文件清理失败不影响数据库删除
            }
        }
    }

    private String resolveCurrentUsername() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getUsername() : null;
    }

    private Map<Long, Question> loadActiveQuestions(List<PaperCreateRequest.PaperQuestionItem> items) {
        List<Long> ids = items.stream()
                .map(PaperCreateRequest.PaperQuestionItem::getQuestionId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请至少选择一道题目");
        }

        List<Question> questions = questionMapper.selectBatchIds(ids);
        Map<Long, Question> map = new HashMap<>();
        for (Question q : questions) {
            if ("ACTIVE".equals(q.getStatus())) {
                map.put(q.getId(), q);
            }
        }

        for (Long id : ids) {
            if (!map.containsKey(id)) {
                throw new BusinessException(BizCode.NOT_FOUND, "题目不存在或已下架: " + id);
            }
        }
        return map;
    }

    private PaperVO toVO(Paper paper, boolean withQuestions) {
        PaperVO vo = new PaperVO();
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setPaperType(paper.getPaperType());
        vo.setTotalScore(paper.getTotalScore());
        vo.setDurationMinutes(paper.getDurationMinutes());
        vo.setSubject(paper.getSubject());
        vo.setGrade(paper.getGrade());
        vo.setComposeMode(paper.getComposeMode());
        vo.setStatus(paper.getStatus());
        vo.setCreatedBy(paper.getCreatedBy());
        vo.setCreatedAt(paper.getCreatedAt());

        if (withQuestions) {
            List<PaperQuestion> relations = paperQuestionMapper.selectList(
                    new LambdaQueryWrapper<PaperQuestion>()
                            .eq(PaperQuestion::getPaperId, paper.getId())
                            .orderByAsc(PaperQuestion::getSortOrder));

            List<Long> questionIds = relations.stream().map(PaperQuestion::getQuestionId).toList();
            Map<Long, Question> questionMap = new HashMap<>();
            if (!questionIds.isEmpty()) {
                questionMapper.selectBatchIds(questionIds).forEach(q -> questionMap.put(q.getId(), q));
            }

            List<PaperQuestionVO> questionVOs = new ArrayList<>();
            for (PaperQuestion relation : relations) {
                Question question = questionMap.get(relation.getQuestionId());
                if (question == null) {
                    continue;
                }
                PaperQuestionVO qvo = new PaperQuestionVO();
                qvo.setQuestionId(question.getId());
                qvo.setSortOrder(relation.getSortOrder());
                qvo.setScore(relation.getScore());
                qvo.setStem(question.getStem());
                qvo.setAnswer(question.getAnswer());
                qvo.setAnalysis(question.getAnalysis());
                qvo.setQuestionType(question.getQuestionType());
                qvo.setOptions(JsonUtils.fromJson(question.getOptionsJson(), new TypeReference<List<String>>() {
                }));
                qvo.setImagesJson(question.getImagesJson());
                qvo.setImages(imageAssetService.toQuestionImageVOs(question.getImagesJson()));
                questionVOs.add(qvo);
            }
            questionVOs.sort(Comparator.comparing(PaperQuestionVO::getSortOrder));
            vo.setQuestions(questionVOs);
        }
        return vo;
    }
}
