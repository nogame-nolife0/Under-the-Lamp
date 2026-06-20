package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.config.PaperProperties;
import org.example.backend_springboot.converter.ImportConverter;
import org.example.backend_springboot.dto.agent.ParseWordHintsDTO;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.agent.ParseWordItemDTO;
import org.example.backend_springboot.dto.agent.ParseWordRequest;
import org.example.backend_springboot.dto.agent.ParseWordResponse;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.example.backend_springboot.dto.request.ImportBatchUpdateRequest;
import org.example.backend_springboot.dto.request.ImportItemUpdateRequest;
import org.example.backend_springboot.dto.vo.ImportConfirmVO;
import org.example.backend_springboot.dto.vo.ImportItemVO;
import org.example.backend_springboot.dto.vo.ImportUploadVO;
import org.example.backend_springboot.entity.ImportBatch;
import org.example.backend_springboot.entity.ImportItem;
import org.example.backend_springboot.entity.Question;
import org.example.backend_springboot.enums.ImportBatchStatusEnum;
import org.example.backend_springboot.enums.ImportItemStatusEnum;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.ImportBatchMapper;
import org.example.backend_springboot.mapper.ImportItemMapper;
import org.example.backend_springboot.mapper.QuestionMapper;
import org.example.backend_springboot.service.AgentClientService;
import org.example.backend_springboot.service.ImageAssetService;
import org.example.backend_springboot.service.ImportService;
import org.example.backend_springboot.service.QuestionEmbedService;
import org.example.backend_springboot.util.ImageMarkerUtils;
import org.example.backend_springboot.util.StemSanitizeUtils;
import org.example.backend_springboot.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportServiceImpl implements ImportService {

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.70");
    private static final Pattern IMAGE_MARKER_PATTERN = Pattern.compile("\\[嵌入图片(\\d+)\\]");

    private final ImportBatchMapper importBatchMapper;
    private final ImportItemMapper importItemMapper;
    private final QuestionMapper questionMapper;
    private final AgentClientService agentClientService;
    private final ImageAssetService imageAssetService;
    private final PaperProperties paperProperties;
    private final QuestionEmbedService questionEmbedService;

    public ImportServiceImpl(ImportBatchMapper importBatchMapper,
                             ImportItemMapper importItemMapper,
                             QuestionMapper questionMapper,
                             AgentClientService agentClientService,
                             ImageAssetService imageAssetService,
                             PaperProperties paperProperties,
                             QuestionEmbedService questionEmbedService) {
        this.importBatchMapper = importBatchMapper;
        this.importItemMapper = importItemMapper;
        this.questionMapper = questionMapper;
        this.agentClientService = agentClientService;
        this.imageAssetService = imageAssetService;
        this.paperProperties = paperProperties;
        this.questionEmbedService = questionEmbedService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportUploadVO uploadAndParse(List<MultipartFile> stemFiles, List<MultipartFile> answerFiles, String subject, String grade) {
        List<MultipartFile> normalizedStemFiles = stemFiles == null
                ? Collections.emptyList()
                : stemFiles.stream().filter(item -> item != null && !item.isEmpty()).toList();
        if (normalizedStemFiles.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "请上传试卷 Word 文件（form-data 字段名 file 或 stemFiles）");
        }
        String courseName = normalizeText(subject);
        if (courseName == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请填写课程名称");
        }
        String courseGrade = normalizeText(grade);
        for (MultipartFile stemFile : normalizedStemFiles) {
            validateDocx(stemFile, "试卷");
        }

        List<MultipartFile> normalizedAnswerFiles = answerFiles == null
                ? Collections.emptyList()
                : answerFiles.stream().filter(item -> item != null && !item.isEmpty()).toList();
        for (MultipartFile answerFile : normalizedAnswerFiles) {
            validateDocx(answerFile, "答案");
        }

        String batchUuid = UUID.randomUUID().toString();
        List<String> stemOriginalNames = new ArrayList<>();
        List<Path> stemSavedPaths = new ArrayList<>();
        for (MultipartFile stemFile : normalizedStemFiles) {
            stemOriginalNames.add(stemFile.getOriginalFilename());
            stemSavedPaths.add(saveUploadFile(stemFile, stemFile.getOriginalFilename()));
        }

        List<String> answerOriginalNames = new ArrayList<>();
        List<Path> answerSavedPaths = new ArrayList<>();
        for (MultipartFile answerFile : normalizedAnswerFiles) {
            answerOriginalNames.add(answerFile.getOriginalFilename());
            answerSavedPaths.add(saveUploadFile(answerFile, answerFile.getOriginalFilename()));
        }

        boolean hasAnswerFile = !normalizedAnswerFiles.isEmpty();
        String importMode = hasAnswerFile ? "SEPARATE" : "STEM_ONLY";

        ImportBatch batch = new ImportBatch();
        batch.setBatchUuid(batchUuid);
        batch.setFileName(String.join(" + ", stemOriginalNames));
        batch.setFilePath(stemSavedPaths.get(0).toString());
        batch.setFileSize(normalizedStemFiles.stream().mapToLong(MultipartFile::getSize).sum());
        if (hasAnswerFile) {
            batch.setAnswerFileName(String.join(" + ", answerOriginalNames));
            batch.setAnswerFilePath(answerSavedPaths.get(0).toString());
        }
        batch.setImportMode(importMode);
        batch.setSubject(courseName);
        batch.setGrade(courseGrade);
        batch.setStatus(ImportBatchStatusEnum.PARSING.name());
        importBatchMapper.insert(batch);

        try {
            List<ParseWordItemDTO> stemItems = new ArrayList<>();
            for (int index = 0; index < stemSavedPaths.size(); index++) {
                Path stemSavedPath = stemSavedPaths.get(index);
                String stemOriginalName = stemOriginalNames.get(index);
                ParseWordRequest stemRequest = buildParseRequest(batchUuid, stemSavedPath.toString(), "STEM");
                ParseWordResponse stemResponse;
                try {
                    stemResponse = agentClientService.parseWord(stemRequest);
                } catch (BusinessException ex) {
                    throw new BusinessException(ex.getCode(),
                            "试卷文件解析失败（" + stemOriginalName + "）: " + ex.getMessage());
                }
                persistDocumentImages(batchUuid, "STEM", stemResponse.getDocumentImages(), stemSavedPath);
                for (ParseWordItemDTO stemItem : stemResponse.getItems()) {
                    persistDocumentImages(batchUuid, "STEM", stemItem.getImages(), stemSavedPath);
                }
                appendStemItems(stemResponse.getItems(), stemItems);
            }

            Map<Integer, ParseWordItemDTO> answerBySeq = new HashMap<>();
            Map<String, ParseWordItemDTO> answerByLabel = new HashMap<>();
            for (int index = 0; index < answerSavedPaths.size(); index++) {
                Path answerSavedPath = answerSavedPaths.get(index);
                String answerOriginalName = answerOriginalNames.get(index);
                ParseWordRequest answerRequest = buildParseRequest(batchUuid, answerSavedPath.toString(), "ANSWER");
                ParseWordResponse answerResponse;
                try {
                    answerResponse = agentClientService.parseWord(answerRequest);
                } catch (BusinessException ex) {
                    throw new BusinessException(ex.getCode(),
                            "答案文件解析失败（" + answerOriginalName + "）: " + ex.getMessage());
                }
                persistDocumentImages(batchUuid, "ANSWER", answerResponse.getDocumentImages(), answerSavedPath);
                for (ParseWordItemDTO answerItem : answerResponse.getItems()) {
                    persistDocumentImages(batchUuid, "ANSWER", answerItem.getImages(), answerSavedPath);
                }
                collectAnswerItems(answerResponse.getItems(), answerBySeq, answerByLabel);
            }

            int needsReview = 0;
            for (ParseWordItemDTO stemItem : stemItems) {
                mergeAnswer(stemItem, findAnswerItem(stemItem, answerBySeq, answerByLabel));
                stemItem.setImages(imageAssetService.enrichImageUrls(batchUuid, stemItem.getImages()));
                ImportItem item = ImportConverter.fromAgentItem(stemItem, batch.getId(), courseName, courseGrade);
                importItemMapper.insert(item);
                if (needsReview(item, hasAnswerFile)) {
                    needsReview++;
                }
            }

            batch.setStatus(ImportBatchStatusEnum.DRAFT.name());
            batch.setTotalCount(stemItems.size());
            batch.setNeedsReviewCount(needsReview);
            batch.setAcceptedCount(0);
            batch.setRejectedCount(0);
            importBatchMapper.updateById(batch);

            return buildUploadVO(batch, listItems(batchUuid, null));
        } catch (BusinessException ex) {
            batch.setStatus(ImportBatchStatusEnum.FAILED.name());
            batch.setErrorMessage(ex.getMessage());
            importBatchMapper.updateById(batch);
            throw ex;
        } catch (Exception ex) {
            batch.setStatus(ImportBatchStatusEnum.FAILED.name());
            batch.setErrorMessage(ex.getMessage());
            importBatchMapper.updateById(batch);
            throw new BusinessException(BizCode.AGENT_PARSE_FAILED, ex.getMessage());
        }
    }

    private void appendStemItems(List<ParseWordItemDTO> incoming, List<ParseWordItemDTO> target) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        int baseSeq = target.stream()
                .map(ParseWordItemDTO::getSeqNo)
                .filter(seq -> seq != null && seq > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        for (int index = 0; index < incoming.size(); index++) {
            ParseWordItemDTO item = incoming.get(index);
            item.setSeqNo(baseSeq + index + 1);
            target.add(item);
        }
    }

    private ParseWordRequest buildParseRequest(String batchUuid, String filePath, String parseMode) {
        ParseWordRequest request = new ParseWordRequest();
        request.setBatchId(batchUuid);
        request.setFilePath(filePath);
        request.setParseMode(parseMode);
        ParseWordHintsDTO hints = new ParseWordHintsDTO();
        hints.setAnswerSectionKeywords(List.of("参考答案", "答案解析", "答案"));
        request.setHints(hints);
        return request;
    }

    private void collectAnswerItems(List<ParseWordItemDTO> answerItems,
                                    Map<Integer, ParseWordItemDTO> answerBySeq,
                                    Map<String, ParseWordItemDTO> answerByLabel) {
        if (answerItems == null) {
            return;
        }
        for (ParseWordItemDTO answerItem : answerItems) {
            if (answerItem.getSeqNo() != null) {
                answerBySeq.merge(answerItem.getSeqNo(), answerItem, this::mergeAnswerCandidates);
            }
            String label = resolveQuestionLabel(answerItem);
            if (label != null) {
                answerByLabel.merge(label, answerItem, this::mergeAnswerCandidates);
            }
        }
    }

    private ParseWordItemDTO mergeAnswerCandidates(ParseWordItemDTO existing, ParseWordItemDTO incoming) {
        if (incoming.getAnswerRaw() != null && !incoming.getAnswerRaw().isBlank()
                && (existing.getAnswerRaw() == null || existing.getAnswerRaw().isBlank())) {
            existing.setAnswerRaw(incoming.getAnswerRaw());
        }
        if (incoming.getAnalysisRaw() != null && !incoming.getAnalysisRaw().isBlank()
                && (existing.getAnalysisRaw() == null || existing.getAnalysisRaw().isBlank())) {
            existing.setAnalysisRaw(incoming.getAnalysisRaw());
        }
        if (existing.getQuestionLabel() == null && incoming.getQuestionLabel() != null) {
            existing.setQuestionLabel(incoming.getQuestionLabel());
        }
        if (incoming.getImages() != null) {
            List<ParseWordImageDTO> mergedImages = new ArrayList<>();
            if (existing.getImages() != null) {
                mergedImages.addAll(existing.getImages());
            }
            mergedImages.addAll(incoming.getImages());
            existing.setImages(mergedImages);
        }
        return existing;
    }

    private ParseWordItemDTO findAnswerItem(ParseWordItemDTO stemItem,
                                            Map<Integer, ParseWordItemDTO> answerBySeq,
                                            Map<String, ParseWordItemDTO> answerByLabel) {
        String label = resolveQuestionLabel(stemItem);
        if (label != null && answerByLabel.containsKey(label)) {
            return answerByLabel.get(label);
        }
        if (stemItem.getSeqNo() != null) {
            return answerBySeq.get(stemItem.getSeqNo());
        }
        return null;
    }

    private String resolveQuestionLabel(ParseWordItemDTO item) {
        if (item == null) {
            return null;
        }
        if (item.getQuestionLabel() != null && !item.getQuestionLabel().isBlank()) {
            return normalizeQuestionLabel(item.getQuestionLabel());
        }
        if (item.getChapter() != null && !item.getChapter().isBlank()) {
            return normalizeQuestionLabel(item.getChapter());
        }
        return null;
    }

    private String normalizeQuestionLabel(String label) {
        return label.trim().replaceAll("\\s+", "");
    }

    private void mergeAnswer(ParseWordItemDTO stemItem, ParseWordItemDTO answerItem) {
        if (answerItem == null) {
            return;
        }
        if (answerItem.getAnswerRaw() != null && !answerItem.getAnswerRaw().isBlank()) {
            stemItem.setAnswerRaw(answerItem.getAnswerRaw());
        }
        if (answerItem.getAnalysisRaw() != null && !answerItem.getAnalysisRaw().isBlank()) {
            stemItem.setAnalysisRaw(answerItem.getAnalysisRaw());
        }
        if (stemItem.getQuestionLabel() == null && answerItem.getQuestionLabel() != null) {
            stemItem.setQuestionLabel(answerItem.getQuestionLabel());
        }
        if (stemItem.getConfidenceScore() == null
                || stemItem.getConfidenceScore().compareTo(new BigDecimal("0.70")) < 0) {
            if (stemItem.getAnswerRaw() != null && !stemItem.getAnswerRaw().isBlank()
                    && !isPlaceholderAnswer(stemItem.getAnswerRaw())) {
                stemItem.setConfidenceScore(new BigDecimal("0.85"));
            }
        }
        List<ParseWordImageDTO> mergedImages = new ArrayList<>();
        if (stemItem.getImages() != null) {
            mergedImages.addAll(stemItem.getImages());
        }
        if (answerItem.getImages() != null) {
            for (ParseWordImageDTO image : answerItem.getImages()) {
                ParseWordImageDTO copy = cloneImage(image);
                copy.setScope("ANSWER");
                mergedImages.add(copy);
            }
        }
        stemItem.setImages(mergedImages.isEmpty() ? null : mergedImages);
    }

    private ParseWordImageDTO cloneImage(ParseWordImageDTO source) {
        ParseWordImageDTO copy = new ParseWordImageDTO();
        copy.setIndex(source.getIndex());
        copy.setPlaceholder(source.getPlaceholder());
        copy.setPosition(source.getPosition());
        copy.setFilePath(source.getFilePath());
        copy.setDisplayPath(source.getDisplayPath());
        copy.setScope(source.getScope());
        copy.setUrl(source.getUrl());
        return copy;
    }

    private void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images) {
        imageAssetService.persistDocumentImages(batchUuid, scope, images);
    }

    private void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images, Path wordFilePath) {
        imageAssetService.persistDocumentImages(batchUuid, scope, images, wordFilePath);
    }

    private boolean isPlaceholderAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        return answer.contains("未给出") || answer.contains("需依据") || answer.contains("需推导");
    }

    private void validateDocx(MultipartFile file, String label) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".docx")) {
            throw new BusinessException(BizCode.WORD_FORMAT_UNSUPPORTED, label + "文件仅支持 .docx 格式");
        }
    }

    @Override
    public ImportUploadVO getBatch(String batchUuid) {
        ImportBatch batch = getBatchOrThrow(batchUuid);
        return buildUploadVO(batch, listItems(batchUuid, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(String batchUuid, ImportBatchUpdateRequest request) {
        ImportBatch batch = getBatchOrThrow(batchUuid);
        String courseName = normalizeText(request.getSubject());
        if (courseName == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请填写课程名称");
        }
        String courseGrade = normalizeText(request.getGrade());
        batch.setSubject(courseName);
        batch.setGrade(courseGrade);
        importBatchMapper.updateById(batch);

        LambdaQueryWrapper<ImportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportItem::getBatchId, batch.getId());
        List<ImportItem> items = importItemMapper.selectList(wrapper);
        for (ImportItem item : items) {
            item.setSubject(courseName);
            item.setGrade(courseGrade);
            importItemMapper.updateById(item);
            if (item.getQuestionId() != null) {
                Question question = questionMapper.selectById(item.getQuestionId());
                if (question != null) {
                    question.setSubject(courseName);
                    question.setGrade(courseGrade);
                    questionMapper.updateById(question);
                }
            }
        }
    }

    @Override
    public List<ImportItemVO> listItems(String batchUuid, String status) {
        ImportBatch batch = getBatchOrThrow(batchUuid);
        LambdaQueryWrapper<ImportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportItem::getBatchId, batch.getId());
        if (status != null && !status.isBlank()) {
            wrapper.eq(ImportItem::getStatus, status);
        }
        wrapper.orderByAsc(ImportItem::getSeqNo);
        return importItemMapper.selectList(wrapper).stream()
                .peek(this::repairLinkedQuestionStem)
                .map(ImportConverter::toVO)
                .map(vo -> enrichImportItemVo(vo, batchUuid))
                .toList();
    }

    private void repairLinkedQuestionStem(ImportItem item) {
        if (item.getQuestionId() == null) {
            return;
        }
        Question question = questionMapper.selectById(item.getQuestionId());
        if (question == null) {
            return;
        }
        String bestStem = ImageMarkerUtils.preferImageMarkersOverLatex(preferImportStem(item));
        if (bestStem == null || bestStem.isBlank()) {
            return;
        }
        if (!isRicherText(bestStem, question.getStem())) {
            return;
        }
        question.setStem(bestStem);
        questionMapper.updateById(question);
    }

    private String preferImportStem(ImportItem item) {
        return firstNotBlank(item.getStemRaw(), item.getStemHtml());
    }

    private String preferImportAnswer(ImportItem item) {
        return firstNotBlank(item.getAnswerRaw(), item.getAnswerHtml());
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

    private ImportItemVO enrichImportItemVo(ImportItemVO vo, String batchUuid) {
        List<ParseWordImageDTO> images = vo.getImages() != null
                ? new ArrayList<>(vo.getImages())
                : new ArrayList<>();
        images = mergeImageList(images, inferImagesFromText(vo.getStemRaw(), "STEM"));
        images = mergeImageList(images, inferImagesFromText(vo.getStemHtml(), "STEM"));
        if (vo.getOptions() != null) {
            for (String option : vo.getOptions()) {
                images = mergeImageList(images, inferImagesFromText(option, "STEM"));
            }
        }
        images = mergeImageList(images, inferImagesFromText(vo.getAnswerRaw(), "ANSWER"));
        images = mergeImageList(images, inferImagesFromText(vo.getAnswerHtml(), "ANSWER"));
        vo.setImages(imageAssetService.enrichImageUrls(batchUuid, images));
        return vo;
    }

    private List<ParseWordImageDTO> inferImagesFromText(String text, String scope) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<Integer> indices = new LinkedHashSet<>();
        Matcher matcher = IMAGE_MARKER_PATTERN.matcher(text);
        while (matcher.find()) {
            indices.add(Integer.parseInt(matcher.group(1)));
        }
        List<ParseWordImageDTO> images = new ArrayList<>();
        for (Integer index : indices) {
            ParseWordImageDTO image = new ParseWordImageDTO();
            image.setIndex(index);
            image.setPlaceholder("[嵌入图片" + index + "]");
            image.setScope(scope);
            images.add(image);
        }
        return images;
    }

    private List<ParseWordImageDTO> mergeImageList(List<ParseWordImageDTO> base, List<ParseWordImageDTO> extra) {
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        Map<String, ParseWordImageDTO> merged = new HashMap<>();
        for (ParseWordImageDTO image : base) {
            if (image.getIndex() == null) {
                continue;
            }
            String scope = image.getScope() != null ? image.getScope() : "STEM";
            merged.put(scope + ":" + image.getIndex(), image);
        }
        for (ParseWordImageDTO image : extra) {
            if (image.getIndex() == null) {
                continue;
            }
            String scope = image.getScope() != null ? image.getScope() : "STEM";
            merged.putIfAbsent(scope + ":" + image.getIndex(), image);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long itemId, ImportItemUpdateRequest request) {
        ImportItem item = getItemOrThrow(itemId);
        if (request.getStemHtml() != null) {
            item.setStemHtml(request.getStemHtml());
        }
        if (request.getAnswerHtml() != null) {
            item.setAnswerHtml(request.getAnswerHtml());
        }
        if (request.getAnalysisHtml() != null) {
            item.setAnalysisHtml(request.getAnalysisHtml());
        }
        if (request.getOptions() != null) {
            item.setOptionsJson(JsonUtils.toJson(request.getOptions()));
        }
        if (request.getQuestionType() != null) {
            item.setQuestionType(request.getQuestionType());
        }
        if (request.getDifficulty() != null) {
            item.setDifficulty(request.getDifficulty());
        }
        if (request.getChapter() != null) {
            item.setChapter(request.getChapter());
        }
        item.setStatus(ImportItemStatusEnum.EDITED.name());
        importItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptItem(Long itemId) {
        ImportItem item = getItemOrThrow(itemId);
        if (item.getQuestionId() == null) {
            importItemToQuestion(item);
        } else {
            item.setStatus(ImportItemStatusEnum.ACCEPTED.name());
        }
        importItemMapper.updateById(item);
        refreshBatchCounts(item.getBatchId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectItem(Long itemId) {
        ImportItem item = getItemOrThrow(itemId);
        item.setStatus(ImportItemStatusEnum.REJECTED.name());
        importItemMapper.updateById(item);
        refreshBatchCounts(item.getBatchId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportConfirmVO confirmBatch(String batchUuid) {
        ImportBatch batch = getBatchOrThrow(batchUuid);
        if (ImportBatchStatusEnum.CONFIRMED.name().equals(batch.getStatus())) {
            throw new BusinessException(BizCode.BATCH_STATUS_INVALID, "批次已确认入库");
        }

        LambdaQueryWrapper<ImportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportItem::getBatchId, batch.getId());
        wrapper.in(ImportItem::getStatus,
                ImportItemStatusEnum.ACCEPTED.name(),
                ImportItemStatusEnum.EDITED.name(),
                ImportItemStatusEnum.PENDING.name());
        List<ImportItem> items = importItemMapper.selectList(wrapper);

        int imported = 0;
        int skipped = 0;
        List<Long> newQuestionIds = new ArrayList<>();
        boolean hasAnswer = hasAnswerFile(batch);
        for (ImportItem item : items) {
            if (ImportItemStatusEnum.REJECTED.name().equals(item.getStatus())) {
                skipped++;
                continue;
            }
            if (item.getQuestionId() != null) {
                imported++;
                continue;
            }
            if (ImportItemStatusEnum.PENDING.name().equals(item.getStatus())
                    && needsReview(item, hasAnswer)) {
                skipped++;
                continue;
            }
            importItemToQuestion(item);
            importItemMapper.updateById(item);
            if (item.getQuestionId() != null) {
                newQuestionIds.add(item.getQuestionId());
            }
            imported++;
        }

        if (!newQuestionIds.isEmpty()) {
            questionEmbedService.embedQuestionsAsync(newQuestionIds);
        }

        batch.setStatus(imported > 0 ? ImportBatchStatusEnum.CONFIRMED.name() : ImportBatchStatusEnum.PARTIAL.name());
        batch.setAcceptedCount(imported);
        importBatchMapper.updateById(batch);

        ImportConfirmVO vo = new ImportConfirmVO();
        vo.setBatchUuid(batchUuid);
        vo.setStatus(batch.getStatus());
        vo.setImportedCount(imported);
        vo.setSkippedCount(skipped);
        return vo;
    }

    private void importItemToQuestion(ImportItem item) {
        Question question = buildQuestion(item);
        if (question.getStem() == null || question.getStem().isBlank()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "题干不能为空，请先编辑题目");
        }
        questionMapper.insert(question);
        List<String> options = JsonUtils.fromJson(item.getOptionsJson(), new TypeReference<List<String>>() {
        });
        List<ParseWordImageDTO> sourceImages = ImageMarkerUtils.collectReferencedImages(
                item.getImagesJson(),
                firstNotBlank(item.getStemRaw(), item.getStemHtml()),
                options,
                firstNotBlank(item.getAnswerRaw(), item.getAnswerHtml()),
                item.getAnalysisHtml());
        ImportBatch batch = importBatchMapper.selectById(item.getBatchId());
        String batchUuid = batch != null ? batch.getBatchUuid() : null;
        List<QuestionImageVO> questionImages = imageAssetService.copyToQuestion(
                question.getId(), batchUuid, sourceImages);
        if (!questionImages.isEmpty()) {
            question.setImagesJson(JsonUtils.toJson(questionImages));
            questionMapper.updateById(question);
        }
        item.setQuestionId(question.getId());
        item.setStatus(ImportItemStatusEnum.ACCEPTED.name());
    }

    private Question buildQuestion(ImportItem item) {
        Question question = new Question();
        question.setStem(ImageMarkerUtils.preferImageMarkersOverLatex(
                StemSanitizeUtils.sanitizeStem(preferImportStem(item), item.getChapter())));
        question.setAnswer(ImageMarkerUtils.preferImageMarkersOverLatex(
                StemSanitizeUtils.sanitizeAnswer(
                        firstNotBlank(preferImportAnswer(item), "待补充"), item.getChapter())));
        question.setAnalysis(item.getAnalysisHtml());
        question.setOptionsJson(item.getOptionsJson());
        question.setQuestionType(item.getQuestionType());
        question.setDifficulty(item.getDifficulty());
        question.setSubject(resolveCourseName(item));
        question.setGrade(resolveCourseGrade(item));
        question.setChapter(item.getChapter());
        question.setKnowledgePoints(item.getKnowledgePoints());
        question.setScoreDefault(BigDecimal.ZERO);
        question.setSourceBatchId(item.getBatchId());
        question.setSourceItemId(item.getId());
        question.setConfidenceSnapshot(item.getConfidenceScore());
        question.setImagesJson(item.getImagesJson());
        question.setEmbedStatus("PENDING");
        question.setStatus("ACTIVE");
        question.setVersion(1);
        return question;
    }

    private boolean hasAnswerFile(ImportBatch batch) {
        return batch.getAnswerFilePath() != null && !batch.getAnswerFilePath().isBlank();
    }

    private void refreshBatchCounts(Long batchId) {
        ImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        LambdaQueryWrapper<ImportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportItem::getBatchId, batchId);
        List<ImportItem> items = importItemMapper.selectList(wrapper);
        boolean hasAnswerFile = hasAnswerFile(batch);
        batch.setAcceptedCount((int) items.stream()
                .filter(i -> ImportItemStatusEnum.ACCEPTED.name().equals(i.getStatus())
                        || ImportItemStatusEnum.EDITED.name().equals(i.getStatus()))
                .count());
        batch.setRejectedCount((int) items.stream()
                .filter(i -> ImportItemStatusEnum.REJECTED.name().equals(i.getStatus()))
                .count());
        batch.setNeedsReviewCount((int) items.stream()
                .filter(i -> needsReview(i, hasAnswerFile))
                .count());
        importBatchMapper.updateById(batch);
    }

    private boolean needsReview(ImportItem item, boolean hasAnswerFile) {
        if (item.getConfidenceScore() != null && item.getConfidenceScore().compareTo(REVIEW_THRESHOLD) < 0) {
            return true;
        }
        if (isPlaceholderAnswer(item.getAnswerRaw())) {
            return true;
        }
        if (hasAnswerFile && (item.getAnswerRaw() == null || item.getAnswerRaw().isBlank())) {
            return true;
        }
        return false;
    }

    private ImportUploadVO buildUploadVO(ImportBatch batch, List<ImportItemVO> items) {
        ImportUploadVO vo = new ImportUploadVO();
        vo.setBatchUuid(batch.getBatchUuid());
        vo.setStatus(batch.getStatus());
        vo.setFileName(batch.getFileName());
        vo.setAnswerFileName(batch.getAnswerFileName());
        vo.setImportMode(batch.getImportMode());
        vo.setSubject(batch.getSubject());
        vo.setGrade(batch.getGrade());
        vo.setTotalCount(batch.getTotalCount());
        vo.setNeedsReviewCount(batch.getNeedsReviewCount());
        vo.setAcceptedCount(batch.getAcceptedCount());
        vo.setRejectedCount(batch.getRejectedCount());
        vo.setItems(items);
        return vo;
    }

    private ImportBatch getBatchOrThrow(String batchUuid) {
        ImportBatch batch = importBatchMapper.selectOne(new LambdaQueryWrapper<ImportBatch>()
                .eq(ImportBatch::getBatchUuid, batchUuid));
        if (batch == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "导入批次不存在");
        }
        return batch;
    }

    private ImportItem getItemOrThrow(Long itemId) {
        ImportItem item = importItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "导入题目不存在");
        }
        return item;
    }

    private Path saveUploadFile(MultipartFile file, String originalName) {
        try {
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path dir = Path.of(paperProperties.getUpload().getBaseDir(), dateDir);
            Files.createDirectories(dir);
            String savedName = UUID.randomUUID() + "_" + originalName;
            Path target = dir.resolve(savedName);
            file.transferTo(target);
            return target;
        } catch (IOException e) {
            throw new BusinessException(BizCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String resolveCourseName(ImportItem item) {
        if (item.getSubject() != null && !item.getSubject().isBlank()) {
            return item.getSubject();
        }
        ImportBatch batch = importBatchMapper.selectById(item.getBatchId());
        return batch != null && batch.getSubject() != null ? batch.getSubject() : "";
    }

    private String resolveCourseGrade(ImportItem item) {
        if (item.getGrade() != null && !item.getGrade().isBlank()) {
            return item.getGrade();
        }
        ImportBatch batch = importBatchMapper.selectById(item.getBatchId());
        return batch != null ? batch.getGrade() : null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
