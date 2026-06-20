package org.example.backend_springboot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.example.backend_springboot.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageMarkerUtils {

    public static final Pattern IMAGE_MARKER_PATTERN = Pattern.compile("\\[嵌入图片(\\d+)\\]");
    private static final Pattern MATH_PATTERN = Pattern.compile("(\\$\\$[\\s\\S]+?\\$\\$|\\$[^$\\n]+?\\$)");
    /** 占位符后紧跟的 OCR LaTeX 重复块，如 [嵌入图片1]$\\overline{I_0}$ */
    private static final Pattern LATEX_AFTER_MARKER_PATTERN = Pattern.compile(
            "(\\[嵌入图片\\d+\\])(?:\\s*\\$[^$\\n]+?\\$)+");
    /** 两个占位符之间仅有 LaTeX、无汉字的重复段 */
    private static final Pattern LATEX_BETWEEN_MARKERS_PATTERN = Pattern.compile(
            "(\\[嵌入图片\\d+\\])(?:\\s*\\$[^$\\n]+?\\$)+\\s*(?=\\[嵌入图片\\d+\\])");

    private ImageMarkerUtils() {
    }

    /**
     * 去掉与嵌入图片重复的 LaTeX（仅紧邻占位符的 OCR 块），保留问句尾部等独立公式。
     */
    public static String preferImageMarkersOverLatex(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (!IMAGE_MARKER_PATTERN.matcher(text).find()) {
            return text;
        }
        String cleaned = LATEX_BETWEEN_MARKERS_PATTERN.matcher(text).replaceAll("$1");
        cleaned = LATEX_AFTER_MARKER_PATTERN.matcher(cleaned).replaceAll("$1");
        return cleaned;
    }

    public static Set<Integer> collectIndices(String scope, String... texts) {
        Set<Integer> indices = new LinkedHashSet<>();
        if (texts == null) {
            return indices;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = IMAGE_MARKER_PATTERN.matcher(text);
            while (matcher.find()) {
                indices.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return indices;
    }

    public static List<ParseWordImageDTO> inferImagesFromText(String text, String scope) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParseWordImageDTO> images = new ArrayList<>();
        for (Integer index : collectIndices(scope, text)) {
            ParseWordImageDTO image = new ParseWordImageDTO();
            image.setIndex(index);
            image.setPlaceholder("[嵌入图片" + index + "]");
            image.setScope(scope);
            images.add(image);
        }
        return images;
    }

    public static List<ParseWordImageDTO> mergeImageList(List<ParseWordImageDTO> base, List<ParseWordImageDTO> extra) {
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        Map<String, ParseWordImageDTO> merged = new HashMap<>();
        if (base != null) {
            for (ParseWordImageDTO image : base) {
                if (image.getIndex() == null) {
                    continue;
                }
                String imageScope = image.getScope() != null ? image.getScope() : "STEM";
                merged.put(imageScope + ":" + image.getIndex(), image);
            }
        }
        for (ParseWordImageDTO image : extra) {
            if (image.getIndex() == null) {
                continue;
            }
            String imageScope = image.getScope() != null ? image.getScope() : "STEM";
            merged.putIfAbsent(imageScope + ":" + image.getIndex(), image);
        }
        return new ArrayList<>(merged.values());
    }

    public static List<ParseWordImageDTO> collectReferencedImages(String imagesJson,
                                                                String stem,
                                                                List<String> options,
                                                                String answer,
                                                                String analysis) {
        List<ParseWordImageDTO> images = new ArrayList<>();
        if (imagesJson != null && !imagesJson.isBlank()) {
            List<ParseWordImageDTO> parsed = JsonUtils.fromJson(imagesJson, new TypeReference<List<ParseWordImageDTO>>() {
            });
            if (parsed != null) {
                images.addAll(parsed);
            }
        }
        images = mergeImageList(images, inferImagesFromText(stem, "STEM"));
        if (options != null) {
            for (String option : options) {
                images = mergeImageList(images, inferImagesFromText(option, "STEM"));
            }
        }
        images = mergeImageList(images, inferImagesFromText(answer, "ANSWER"));
        images = mergeImageList(images, inferImagesFromText(analysis, "ANSWER"));
        return images;
    }

    public static List<QuestionImageVO> mergeQuestionImageList(List<QuestionImageVO> base, List<QuestionImageVO> extra) {
        if (extra == null || extra.isEmpty()) {
            return base != null ? base : List.of();
        }
        Map<String, QuestionImageVO> merged = new HashMap<>();
        if (base != null) {
            for (QuestionImageVO image : base) {
                if (image.getIndex() == null) {
                    continue;
                }
                String scope = image.getScope() != null ? image.getScope() : "STEM";
                merged.put(scope + ":" + image.getIndex(), image);
            }
        }
        for (QuestionImageVO image : extra) {
            if (image.getIndex() == null) {
                continue;
            }
            String scope = image.getScope() != null ? image.getScope() : "STEM";
            merged.putIfAbsent(scope + ":" + image.getIndex(), image);
        }
        return new ArrayList<>(merged.values());
    }
}
