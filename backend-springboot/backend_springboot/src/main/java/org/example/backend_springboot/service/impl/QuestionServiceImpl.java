package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.common.PageResult;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.request.QuestionBatchDeleteRequest;
import org.example.backend_springboot.dto.request.QuestionBatchUpdateSubjectRequest;
import org.example.backend_springboot.dto.request.QuestionUpdateRequest;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.example.backend_springboot.dto.vo.QuestionBatchUpdateSubjectVO;
import org.example.backend_springboot.dto.vo.QuestionDeleteVO;
import org.example.backend_springboot.dto.vo.QuestionVO;
import org.example.backend_springboot.service.QuestionEmbedService;
import org.example.backend_springboot.entity.ImportBatch;
import org.example.backend_springboot.entity.ImportItem;
import org.example.backend_springboot.entity.PaperQuestion;
import org.example.backend_springboot.entity.Question;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.ImportBatchMapper;
import org.example.backend_springboot.mapper.ImportItemMapper;
import org.example.backend_springboot.mapper.PaperQuestionMapper;
import org.example.backend_springboot.mapper.QuestionMapper;
import org.example.backend_springboot.service.ImageAssetService;
import org.example.backend_springboot.service.QuestionService;
import org.example.backend_springboot.util.ImageMarkerUtils;
import org.example.backend_springboot.util.JsonUtils;
import org.example.backend_springboot.util.StemSanitizeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final ImportItemMapper importItemMapper;
    private final ImportBatchMapper importBatchMapper;
    private final ImageAssetService imageAssetService;
    private final QuestionEmbedService questionEmbedService;

    public QuestionServiceImpl(QuestionMapper questionMapper,
                               PaperQuestionMapper paperQuestionMapper,
                               ImportItemMapper importItemMapper,
                               ImportBatchMapper importBatchMapper,
                               ImageAssetService imageAssetService,
                               QuestionEmbedService questionEmbedService) {
        this.questionMapper = questionMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.importItemMapper = importItemMapper;
        this.importBatchMapper = importBatchMapper;
        this.imageAssetService = imageAssetService;
        this.questionEmbedService = questionEmbedService;
    }

    @Override
    public PageResult<QuestionVO> pageQuestions(Long pageNum,
                                                Long pageSize,
                                                String subject,
                                                String grade,
                                                String questionType,
                                                String difficulty,
                                                String chapter,
                                                String keyword) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, "ACTIVE");
        if (subject != null && !subject.isBlank()) {
            wrapper.eq(Question::getSubject, subject);
        }
        if (grade != null && !grade.isBlank()) {
            wrapper.eq(Question::getGrade, grade);
        }
        if (questionType != null && !questionType.isBlank()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (chapter != null && !chapter.isBlank()) {
            wrapper.eq(Question::getChapter, chapter);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Question::getStem, keyword);
        }
        wrapper.orderByDesc(Question::getCreatedAt);

        Page<Question> page = questionMapper.selectPage(new Page<>(current, size), wrapper);
        List<QuestionVO> list = page.getRecords().stream()
                .peek(this::repairStemFromSourceItem)
                .peek(this::repairQuestionImagesFromSource)
                .map(this::toVO)
                .toList();
        return new PageResult<>(list, page.getTotal(), current, size);
    }

    @Override
    public QuestionVO getById(Long id) {
        Question question = requireActiveQuestion(id);
        repairStemFromSourceItem(question);
        repairQuestionImagesFromSource(question);
        return toVO(question);
    }

    private void repairStemFromSourceItem(Question question) {
        if (question == null || question.getSourceItemId() == null) {
            return;
        }
        ImportItem item = importItemMapper.selectById(question.getSourceItemId());
        if (item == null) {
            return;
        }
        String bestStem = ImageMarkerUtils.preferImageMarkersOverLatex(
                firstNotBlank(item.getStemRaw(), item.getStemHtml()));
        if (bestStem == null || bestStem.isBlank() || !isRicherText(bestStem, question.getStem())) {
            return;
        }
        question.setStem(bestStem);
        questionMapper.updateById(question);
    }

    private void repairQuestionImagesFromSource(Question question) {
        if (question == null || question.getId() == null) {
            return;
        }
        String batchUuid = resolveSourceBatchUuid(question);
        if (batchUuid == null || batchUuid.isBlank()) {
            return;
        }
        List<String> options = JsonUtils.fromJson(question.getOptionsJson(), new TypeReference<List<String>>() {
        });
        List<ParseWordImageDTO> referenced = ImageMarkerUtils.collectReferencedImages(
                question.getImagesJson(),
                question.getStem(),
                options,
                question.getAnswer(),
                question.getAnalysis());
        if (referenced.isEmpty()) {
            return;
        }
        List<QuestionImageVO> copied = imageAssetService.copyToQuestion(
                question.getId(), batchUuid, referenced);
        if (copied.isEmpty()) {
            return;
        }
        String updatedJson = JsonUtils.toJson(copied);
        if (!java.util.Objects.equals(updatedJson, question.getImagesJson())) {
            question.setImagesJson(updatedJson);
            questionMapper.updateById(question);
        }
    }

    private String resolveSourceBatchUuid(Question question) {
        if (question.getSourceBatchId() != null) {
            ImportBatch batch = importBatchMapper.selectById(question.getSourceBatchId());
            if (batch != null && batch.getBatchUuid() != null) {
                return batch.getBatchUuid();
            }
        }
        if (question.getSourceItemId() != null) {
            ImportItem item = importItemMapper.selectById(question.getSourceItemId());
            if (item != null && item.getBatchId() != null) {
                ImportBatch batch = importBatchMapper.selectById(item.getBatchId());
                if (batch != null) {
                    return batch.getBatchUuid();
                }
            }
        }
        return null;
    }

    private boolean isRicherText(String candidate, String existing) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (existing == null || existing.isBlank()) {
            return true;
        }
        int candidateHan = countHan(candidate);
        int existingHan = countHan(existing);
        if (candidateHan != existingHan) {
            return candidateHan > existingHan;
        }
        return candidate.length() > existing.length();
    }

    private int countHan(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO updateQuestion(Long id, QuestionUpdateRequest request) {
        Question question = requireActiveQuestion(id);
        if (request.getStem() != null) {
            if (request.getStem().isBlank()) {
                throw new BusinessException(BizCode.BAD_REQUEST, "题干不能为空");
            }
            question.setStem(request.getStem().trim());
        }
        if (request.getAnswer() != null) {
            question.setAnswer(request.getAnswer().trim());
        }
        if (request.getAnalysis() != null) {
            question.setAnalysis(request.getAnalysis().trim());
        }
        if (request.getOptions() != null) {
            question.setOptionsJson(JsonUtils.toJson(request.getOptions()));
        }
        if (request.getQuestionType() != null && !request.getQuestionType().isBlank()) {
            question.setQuestionType(request.getQuestionType().trim());
        }
        if (request.getDifficulty() != null && !request.getDifficulty().isBlank()) {
            question.setDifficulty(request.getDifficulty().trim());
        }
        if (request.getSubject() != null) {
            question.setSubject(request.getSubject().trim());
        }
        if (request.getChapter() != null) {
            question.setChapter(request.getChapter().trim());
        }
        question.setEmbedStatus("PENDING");
        questionMapper.updateById(question);
        questionEmbedService.embedQuestionsAsync(List.of(id));
        return toVO(questionMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionImageVO replaceQuestionImage(Long id, String scope, int index, MultipartFile file) {
        Question question = requireActiveQuestion(id);
        QuestionImageVO replaced = imageAssetService.replaceQuestionImage(id, scope, index, file);
        String normalizedScope = normalizeImageScope(scope);
        String baseUrl = stripCacheBuster(replaced.getUrl());

        List<ParseWordImageDTO> images = readImageMetadata(question.getImagesJson());
        boolean updated = false;
        for (ParseWordImageDTO image : images) {
            if (image.getIndex() != null && image.getIndex() == index
                    && normalizedScope.equals(normalizeImageScope(image.getScope()))) {
                image.setUrl(baseUrl);
                image.setPlaceholder(replaced.getPlaceholder());
                image.setScope(normalizedScope);
                updated = true;
                break;
            }
        }
        if (!updated) {
            ParseWordImageDTO image = new ParseWordImageDTO();
            image.setIndex(index);
            image.setScope(normalizedScope);
            image.setPlaceholder(replaced.getPlaceholder());
            image.setUrl(baseUrl);
            images.add(image);
        }
        question.setImagesJson(JsonUtils.toJson(images));
        questionMapper.updateById(question);
        return replaced;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionDeleteVO batchDelete(QuestionBatchDeleteRequest request) {
        List<Long> ids = request.getIds().stream().distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请至少选择一道题目");
        }

        List<Question> questions = questionMapper.selectBatchIds(ids);
        Set<Long> existingIds = questions.stream()
                .filter(q -> "ACTIVE".equals(q.getStatus()))
                .map(Question::getId)
                .collect(Collectors.toSet());

        List<Long> skippedIds = new ArrayList<>();
        int deletedCount = 0;

        for (Long id : ids) {
            if (!existingIds.contains(id)) {
                skippedIds.add(id);
                continue;
            }
            long usedInPaper = paperQuestionMapper.selectCount(
                    new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, id));
            if (usedInPaper > 0) {
                skippedIds.add(id);
                continue;
            }
            questionMapper.deleteById(id);
            deletedCount++;
        }

        QuestionDeleteVO vo = new QuestionDeleteVO();
        vo.setDeletedCount(deletedCount);
        vo.setSkippedCount(skippedIds.size());
        vo.setSkippedIds(skippedIds);
        if (deletedCount == 0) {
            vo.setMessage("没有题目被删除，可能已被试卷引用或不存在");
        } else if (!skippedIds.isEmpty()) {
            vo.setMessage("已删除 " + deletedCount + " 题，跳过 " + skippedIds.size() + " 题（已被试卷引用或不存在）");
        } else {
            vo.setMessage("已删除 " + deletedCount + " 题");
        }
        return vo;
    }

    @Override
    public List<Long> searchIdsByCondition(String subject,
                                           String grade,
                                           String questionType,
                                           String difficulty,
                                           String chapter,
                                           String keyword,
                                           int limit,
                                           List<Long> excludeIds) {
        int size = Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, "ACTIVE");
        if (subject != null && !subject.isBlank()) {
            wrapper.like(Question::getSubject, subject);
        }
        if (grade != null && !grade.isBlank()) {
            wrapper.like(Question::getGrade, grade);
        }
        if (questionType != null && !questionType.isBlank()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (chapter != null && !chapter.isBlank()) {
            wrapper.like(Question::getChapter, chapter);
        }
        applyKeywordFilter(wrapper, keyword);
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(Question::getId, excludeIds);
        }
        wrapper.orderByDesc(Question::getCreatedAt);
        wrapper.last("LIMIT " + size);
        return questionMapper.selectList(wrapper).stream().map(Question::getId).toList();
    }

    @Override
    public List<QuestionVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = ids.stream().distinct().toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(orderedIds).stream()
                .filter(q -> "ACTIVE".equals(q.getStatus()))
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        List<QuestionVO> result = new ArrayList<>();
        for (Long id : orderedIds) {
            Question question = questionMap.get(id);
            if (question != null) {
                result.add(toVO(question));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionBatchUpdateSubjectVO batchUpdateSubject(QuestionBatchUpdateSubjectRequest request) {
        String subject = request.getSubject().trim();
        if (subject.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请填写课程名称");
        }
        List<Long> ids = request.getIds().stream().distinct().toList();
        List<Question> questions = questionMapper.selectBatchIds(ids).stream()
                .filter(q -> "ACTIVE".equals(q.getStatus()))
                .toList();
        if (questions.isEmpty()) {
            throw new BusinessException(BizCode.NOT_FOUND, "没有可更新的题目");
        }

        List<Long> updatedIds = new ArrayList<>();
        for (Question question : questions) {
            question.setSubject(subject);
            question.setEmbedStatus("PENDING");
            questionMapper.updateById(question);
            updatedIds.add(question.getId());
        }
        questionEmbedService.embedQuestionsAsync(updatedIds);

        QuestionBatchUpdateSubjectVO vo = new QuestionBatchUpdateSubjectVO();
        vo.setUpdatedCount(updatedIds.size());
        vo.setMessage("已更新 " + updatedIds.size() + " 题的课程，向量库将后台重新同步");
        return vo;
    }

    @Override
    public List<String> listSubjects() {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Question::getSubject);
        wrapper.eq(Question::getStatus, "ACTIVE");
        wrapper.isNotNull(Question::getSubject);
        wrapper.ne(Question::getSubject, "");
        return questionMapper.selectList(wrapper).stream()
                .map(Question::getSubject)
                .filter(subject -> subject != null && !subject.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<Long> listRecentActiveIds(int limit, List<Long> excludeIds) {
        int size = Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, "ACTIVE");
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(Question::getId, excludeIds);
        }
        wrapper.orderByDesc(Question::getCreatedAt);
        wrapper.last("LIMIT " + size);
        return questionMapper.selectList(wrapper).stream().map(Question::getId).toList();
    }

    private void applyKeywordFilter(LambdaQueryWrapper<Question> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String[] tokens = keyword.trim().split("\\s+");
        java.util.List<String> validTokens = new java.util.ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                validTokens.add(token);
            }
        }
        if (validTokens.isEmpty()) {
            return;
        }
        wrapper.and(w -> {
            for (String token : validTokens) {
                w.or(sub -> sub.like(Question::getStem, token)
                        .or().like(Question::getChapter, token)
                        .or().like(Question::getSubject, token));
            }
        });
    }

    private Question requireActiveQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null || !"ACTIVE".equals(question.getStatus())) {
            throw new BusinessException(BizCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private List<ParseWordImageDTO> readImageMetadata(String imagesJson) {
        List<ParseWordImageDTO> images = JsonUtils.fromJson(imagesJson, new TypeReference<List<ParseWordImageDTO>>() {
        });
        return images != null ? new ArrayList<>(images) : new ArrayList<>();
    }

    private String normalizeImageScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "STEM";
        }
        return scope.trim().toUpperCase();
    }

    private String stripCacheBuster(String url) {
        if (url == null) {
            return null;
        }
        int index = url.indexOf('?');
        return index > 0 ? url.substring(0, index) : url;
    }

    private QuestionVO toVO(Question question) {
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setStem(ImageMarkerUtils.preferImageMarkersOverLatex(
                StemSanitizeUtils.sanitizeStem(question.getStem(), question.getChapter())));
        vo.setOptions(JsonUtils.fromJson(question.getOptionsJson(), new TypeReference<List<String>>() {
        }));
        vo.setAnswer(ImageMarkerUtils.preferImageMarkersOverLatex(question.getAnswer()));
        vo.setAnalysis(ImageMarkerUtils.preferImageMarkersOverLatex(question.getAnalysis()));
        vo.setQuestionType(question.getQuestionType());
        vo.setDifficulty(question.getDifficulty());
        vo.setSubject(question.getSubject());
        vo.setGrade(question.getGrade());
        vo.setChapter(question.getChapter());
        vo.setKnowledgePoints(JsonUtils.fromJson(question.getKnowledgePoints(), new TypeReference<List<String>>() {
        }));
        vo.setScoreDefault(question.getScoreDefault());
        vo.setStatus(question.getStatus());
        vo.setEmbedStatus(question.getEmbedStatus());
        vo.setImages(enrichQuestionImages(question));
        return vo;
    }

    private List<QuestionImageVO> enrichQuestionImages(Question question) {
        List<QuestionImageVO> images = new ArrayList<>(imageAssetService.toQuestionImageVOs(question.getImagesJson()));
        Map<String, QuestionImageVO> merged = new HashMap<>();
        for (QuestionImageVO image : images) {
            if (image.getIndex() == null) {
                continue;
            }
            String scope = normalizeImageScope(image.getScope());
            merged.put(scope + ":" + image.getIndex(), image);
        }
        addInferredImages(merged, question.getId(), "STEM", question.getStem());
        List<String> options = JsonUtils.fromJson(question.getOptionsJson(), new TypeReference<List<String>>() {
        });
        if (options != null) {
            for (String option : options) {
                addInferredImages(merged, question.getId(), "STEM", option);
            }
        }
        addInferredImages(merged, question.getId(), "ANSWER", question.getAnswer());
        addInferredImages(merged, question.getId(), "ANSWER", question.getAnalysis());
        return new ArrayList<>(merged.values());
    }

    private void addInferredImages(Map<String, QuestionImageVO> merged, Long questionId, String scope, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (Integer index : ImageMarkerUtils.collectIndices(scope, text)) {
            String key = scope + ":" + index;
            if (merged.containsKey(key)) {
                continue;
            }
            QuestionImageVO image = new QuestionImageVO();
            image.setIndex(index);
            image.setScope(scope);
            image.setPlaceholder("[嵌入图片" + index + "]");
            image.setUrl("/api/assets/questions/" + questionId + "/images/" + scope + "/" + index);
            merged.put(key, image);
        }
    }
}
