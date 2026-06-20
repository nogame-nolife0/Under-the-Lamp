package org.example.backend_springboot.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.agent.ParseWordItemDTO;
import org.example.backend_springboot.dto.vo.ImportItemVO;
import org.example.backend_springboot.entity.ImportItem;
import org.example.backend_springboot.util.JsonUtils;
import org.example.backend_springboot.util.StemSanitizeUtils;

import java.util.List;

public final class ImportConverter {

    private ImportConverter() {
    }

    public static ImportItemVO toVO(ImportItem entity) {
        ImportItemVO vo = new ImportItemVO();
        vo.setId(entity.getId());
        vo.setBatchId(entity.getBatchId());
        vo.setSeqNo(entity.getSeqNo());
        vo.setStemRaw(entity.getStemRaw());
        String answerLabel = entity.getChapter();
        String sanitizedAnswer = StemSanitizeUtils.sanitizeAnswer(entity.getAnswerRaw(), answerLabel);
        vo.setAnswerRaw(sanitizedAnswer);
        vo.setStemHtml(resolveDisplayText(entity.getStemHtml(), entity.getStemRaw(), entity.getStatus()));
        vo.setAnswerHtml(resolveDisplayText(entity.getAnswerHtml(), sanitizedAnswer, entity.getStatus()));
        vo.setAnalysisHtml(entity.getAnalysisHtml());
        vo.setOptions(StemSanitizeUtils.sanitizeOptions(JsonUtils.fromJson(entity.getOptionsJson(), new TypeReference<List<String>>() {
        })));
        vo.setQuestionType(entity.getQuestionType());
        vo.setDifficulty(entity.getDifficulty());
        vo.setSubject(entity.getSubject());
        vo.setGrade(entity.getGrade());
        vo.setChapter(entity.getChapter());
        vo.setKnowledgePoints(JsonUtils.fromJson(entity.getKnowledgePoints(), new TypeReference<List<String>>() {
        }));
        vo.setConfidenceScore(entity.getConfidenceScore());
        vo.setWarnings(JsonUtils.fromJson(entity.getWarnings(), new TypeReference<List<String>>() {
        }));
        vo.setImages(JsonUtils.fromJson(entity.getImagesJson(), new TypeReference<List<ParseWordImageDTO>>() {
        }));
        vo.setStatus(entity.getStatus());
        vo.setQuestionId(entity.getQuestionId());
        return vo;
    }

    private static String firstNotBlank(String... values) {
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

    private static String resolveDisplayText(String html, String raw, String status) {
        if ("EDITED".equals(status) && html != null && !html.isBlank()) {
            return html;
        }
        return pickRicherImportText(raw, html);
    }

    private static String pickRicherImportText(String... values) {
        String best = null;
        int bestScore = 0;
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            int score = scoreImportText(value);
            if (score > bestScore) {
                best = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static int scoreImportText(String text) {
        int markers = 0;
        java.util.regex.Matcher matcher = org.example.backend_springboot.util.ImageMarkerUtils.IMAGE_MARKER_PATTERN.matcher(text);
        while (matcher.find()) {
            markers++;
        }
        int han = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                han++;
            }
        }
        return markers * 10000 + han * 100 + text.length();
    }

    public static ImportItem fromAgentItem(ParseWordItemDTO dto, Long batchId, String subject, String grade) {
        ImportItem item = new ImportItem();
        item.setBatchId(batchId);
        item.setSeqNo(dto.getSeqNo());
        String sanitizedStem = StemSanitizeUtils.sanitizeStem(
                dto.getStemRaw(), firstNotBlank(dto.getQuestionLabel(), dto.getChapter()));
        String sanitizedAnswer = StemSanitizeUtils.sanitizeAnswer(
                dto.getAnswerRaw(), firstNotBlank(dto.getQuestionLabel(), dto.getChapter()));
        item.setStemRaw(sanitizedStem);
        item.setAnswerRaw(sanitizedAnswer);
        item.setStemHtml(sanitizedStem);
        item.setAnswerHtml(sanitizedAnswer);
        item.setAnalysisHtml(dto.getAnalysisRaw());
        item.setOptionsJson(JsonUtils.toJson(StemSanitizeUtils.sanitizeOptions(dto.getOptions())));
        item.setQuestionType(dto.getQuestionType());
        item.setDifficulty(dto.getDifficulty());
        item.setSubject(subject);
        item.setGrade(grade);
        item.setChapter(firstNotBlank(dto.getQuestionLabel(), dto.getChapter()));
        item.setKnowledgePoints(JsonUtils.toJson(dto.getKnowledgePoints()));
        item.setConfidenceScore(dto.getConfidenceScore());
        item.setAgentMeta(JsonUtils.toJson(dto.getAgentMeta()));
        item.setWarnings(JsonUtils.toJson(dto.getWarnings()));
        item.setImagesJson(JsonUtils.toJson(dto.getImages()));
        item.setStatus("PENDING");
        return item;
    }
}
