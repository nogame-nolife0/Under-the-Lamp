package org.example.backend_springboot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.config.PaperProperties;
import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.example.backend_springboot.entity.ImportBatch;
import org.example.backend_springboot.entity.Question;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.ImportBatchMapper;
import org.example.backend_springboot.mapper.QuestionMapper;
import org.example.backend_springboot.service.ImageAssetService;
import org.example.backend_springboot.util.ImageMarkerUtils;
import org.example.backend_springboot.util.JsonUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
@Slf4j
public class ImageAssetServiceImpl implements ImageAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "gif");

    private final PaperProperties paperProperties;
    private final QuestionMapper questionMapper;
    private final ImportBatchMapper importBatchMapper;

    public ImageAssetServiceImpl(PaperProperties paperProperties,
                                 QuestionMapper questionMapper,
                                 ImportBatchMapper importBatchMapper) {
        this.paperProperties = paperProperties;
        this.questionMapper = questionMapper;
        this.importBatchMapper = importBatchMapper;
    }

    @Override
    public void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images) {
        persistDocumentImages(batchUuid, scope, images, null);
    }

    @Override
    public void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images, Path wordFilePath) {
        if (images == null || images.isEmpty()) {
            return;
        }
        Path targetDir = batchImageDir(batchUuid, scope);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException ex) {
            throw new BusinessException(BizCode.INTERNAL_ERROR, "创建图片目录失败");
        }

        for (ParseWordImageDTO image : images) {
            if (image.getIndex() == null) {
                continue;
            }
            Path target = targetDir.resolve(image.getIndex() + ".png");
            if (Files.exists(target)) {
                continue;
            }
            Path source = resolveSourcePath(image);
            if (source == null || !Files.exists(source)) {
                source = resolveExtractedImage(wordFilePath, image.getIndex());
            }
            if (source == null || !Files.exists(source)) {
                log.warn("导入图片源文件不存在: batch={}, scope={}, index={}",
                        batchUuid, scope, image.getIndex());
                continue;
            }
            try {
                writeBatchImage(target, source);
            } catch (IOException ex) {
                log.warn("保存导入图片失败: batch={}, scope={}, index={}, source={}",
                        batchUuid, scope, image.getIndex(), source, ex);
            }
        }
    }

    @Override
    public List<ParseWordImageDTO> enrichImageUrls(String batchUuid, List<ParseWordImageDTO> images) {
        if (images == null || images.isEmpty()) {
            return images;
        }
        List<ParseWordImageDTO> enriched = new ArrayList<>();
        for (ParseWordImageDTO image : images) {
            ParseWordImageDTO copy = image;
            if (image.getIndex() != null) {
                String scope = normalizeScope(image.getScope());
                copy = cloneImage(image);
                copy.setUrl(buildBatchImageUrl(batchUuid, scope, image.getIndex()));
            }
            enriched.add(copy);
        }
        return enriched;
    }

    @Override
    public List<QuestionImageVO> copyToQuestion(Long questionId, List<ParseWordImageDTO> images) {
        return copyToQuestion(questionId, null, images);
    }

    @Override
    public List<QuestionImageVO> copyToQuestion(Long questionId, String batchUuid, List<ParseWordImageDTO> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        Path targetDir = questionImageDir(questionId);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException ex) {
            throw new BusinessException(BizCode.INTERNAL_ERROR, "创建题目图片目录失败");
        }

        List<QuestionImageVO> result = new ArrayList<>();
        for (ParseWordImageDTO image : images) {
            if (image.getIndex() == null) {
                continue;
            }
            String scope = normalizeScope(image.getScope());
            Path scopedDir = targetDir.resolve(scope);
            Path target = scopedDir.resolve(image.getIndex() + ".png");
            if (Files.exists(target)) {
                QuestionImageVO vo = new QuestionImageVO();
                vo.setIndex(image.getIndex());
                vo.setPlaceholder(image.getPlaceholder());
                vo.setScope(scope);
                vo.setUrl(buildQuestionImageUrl(questionId, scope, image.getIndex()));
                result.add(vo);
                continue;
            }

            Path source = resolveImageSource(image, batchUuid);
            if (source == null) {
                continue;
            }
            try {
                Files.createDirectories(scopedDir);
                writeBatchImage(target, source);
            } catch (IOException ex) {
                throw new BusinessException(BizCode.INTERNAL_ERROR, "复制题目图片失败");
            }

            QuestionImageVO vo = new QuestionImageVO();
            vo.setIndex(image.getIndex());
            vo.setPlaceholder(image.getPlaceholder());
            vo.setScope(scope);
            vo.setUrl(buildQuestionImageUrl(questionId, scope, image.getIndex()));
            result.add(vo);
        }
        return result;
    }

    private Path resolveImageSource(ParseWordImageDTO image, String batchUuid) {
        Path source = resolveSourcePath(image);
        if (source != null && Files.exists(source)) {
            return source;
        }
        if (batchUuid != null && !batchUuid.isBlank() && image.getIndex() != null) {
            String scope = normalizeScope(image.getScope());
            Path batchFile = findBatchImageFile(batchUuid, scope, image.getIndex());
            if (batchFile != null) {
                return batchFile;
            }
        }
        String urlBatch = extractBatchFromUrl(image.getUrl());
        if (!urlBatch.isBlank() && image.getIndex() != null) {
            Path batchFile = findBatchImageFile(urlBatch, normalizeScope(image.getScope()), image.getIndex());
            if (batchFile != null) {
                return batchFile;
            }
        }
        return null;
    }

    @Override
    public List<QuestionImageVO> toQuestionImageVOs(String imagesJson) {
        List<ParseWordImageDTO> images = JsonUtils.fromJson(imagesJson, new TypeReference<List<ParseWordImageDTO>>() {
        });
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<QuestionImageVO> result = new ArrayList<>();
        for (ParseWordImageDTO image : images) {
            if (image.getIndex() == null) {
                continue;
            }
            QuestionImageVO vo = new QuestionImageVO();
            vo.setIndex(image.getIndex());
            vo.setPlaceholder(image.getPlaceholder());
            vo.setScope(normalizeScope(image.getScope()));
            vo.setUrl(image.getUrl());
            result.add(vo);
        }
        return result;
    }

    @Override
    public Resource loadBatchImage(String batchUuid, String scope, int index) {
        Path file = findBatchImageFile(batchUuid, scope, index);
        if (file == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "图片不存在");
        }
        return new FileSystemResource(file);
    }

    @Override
    public Resource loadQuestionImage(Long questionId, String scope, int index) {
        Path file = findQuestionImageFile(questionId, scope, index);
        if (file == null) {
            tryCopyFromSourceBatch(questionId, scope, index);
            file = findQuestionImageFile(questionId, scope, index);
        }
        if (file == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "图片不存在");
        }
        return new FileSystemResource(file);
    }

    private void tryCopyFromSourceBatch(Long questionId, String scope, int index) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            return;
        }
        String batchUuid = null;
        if (question.getSourceBatchId() != null) {
            ImportBatch batch = importBatchMapper.selectById(question.getSourceBatchId());
            if (batch != null) {
                batchUuid = batch.getBatchUuid();
            }
        }
        if (batchUuid == null || batchUuid.isBlank()) {
            return;
        }
        Path batchFile = findBatchImageFile(batchUuid, scope, index);
        if (batchFile == null) {
            return;
        }
        Path scopedDir = questionImageDir(questionId).resolve(normalizeScope(scope));
        try {
            Files.createDirectories(scopedDir);
            writeBatchImage(scopedDir.resolve(index + ".png"), batchFile);
        } catch (IOException ex) {
            log.warn("按需复制题目图片失败: questionId={}, scope={}, index={}", questionId, scope, index, ex);
        }
    }

    @Override
    public QuestionImageVO replaceQuestionImage(Long questionId, String scope, int index, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "请上传图片文件");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(BizCode.BAD_REQUEST, "图片大小不能超过 5MB");
        }
        String extension = resolveImageExtension(file);
        String normalizedScope = normalizeScope(scope);
        Path scopedDir = questionImageDir(questionId).resolve(normalizedScope);
        try {
            Files.createDirectories(scopedDir);
            clearQuestionImageFiles(scopedDir, index);
            Path target = scopedDir.resolve(index + "." + extension);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BusinessException(BizCode.INTERNAL_ERROR, "保存图片失败");
        }

        QuestionImageVO vo = new QuestionImageVO();
        vo.setIndex(index);
        vo.setPlaceholder("img_" + String.format("%03d", index));
        vo.setScope(normalizedScope);
        vo.setUrl(buildQuestionImageUrl(questionId, normalizedScope, index) + "?t=" + System.currentTimeMillis());
        return vo;
    }

    @Override
    public Map<Integer, Path> resolveQuestionImagePaths(Long questionId, String scope, String imagesJson,
                                                        String... relatedTexts) {
        Map<Integer, Path> map = new HashMap<>();
        String normalizedScope = normalizeScope(scope);
        List<QuestionImageVO> images = toQuestionImageVOs(imagesJson);
        for (QuestionImageVO image : images) {
            if (image.getIndex() == null || !normalizedScope.equals(normalizeScope(image.getScope()))) {
                continue;
            }
            Path file = findQuestionImageFile(questionId, normalizedScope, image.getIndex());
            if (file != null) {
                map.put(image.getIndex(), file);
            }
        }
        for (Integer index : ImageMarkerUtils.collectIndices(normalizedScope, relatedTexts)) {
            map.putIfAbsent(index, findQuestionImageFile(questionId, normalizedScope, index));
        }
        map.values().removeIf(Objects::isNull);
        return map;
    }

    private Path resolveSourcePath(ParseWordImageDTO image) {
        if (image.getDisplayPath() != null && !image.getDisplayPath().isBlank()) {
            Path displayPath = Paths.get(image.getDisplayPath());
            if (Files.exists(displayPath)) {
                return displayPath;
            }
        }
        if (image.getFilePath() != null && !image.getFilePath().isBlank()) {
            Path filePath = Paths.get(image.getFilePath());
            if (Files.exists(filePath)) {
                return filePath;
            }
        }
        return null;
    }

    private Path resolveExtractedImage(Path wordFilePath, Integer index) {
        if (wordFilePath == null || index == null || index <= 0) {
            return null;
        }
        String fileName = wordFilePath.getFileName().toString();
        String stem = fileName.toLowerCase().endsWith(".docx")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        Path extractDir = wordFilePath.getParent().resolve(".extracted_images_" + stem);
        if (!Files.exists(extractDir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(extractDir)) {
            List<Path> allFiles = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            List<Path> visionFiles = allFiles.stream()
                    .filter(path -> path.getFileName().toString().contains("_vision.png"))
                    .toList();
            if (index <= visionFiles.size()) {
                return visionFiles.get(index - 1);
            }
            List<Path> rasterFiles = allFiles.stream()
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.startsWith("img_")
                                && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp"));
                    })
                    .toList();
            if (index <= rasterFiles.size()) {
                return rasterFiles.get(index - 1);
            }
        } catch (IOException ex) {
            log.warn("扫描 Word 提取图片目录失败: {}", extractDir, ex);
        }
        return null;
    }

    private void writeBatchImage(Path target, Path source) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image != null) {
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            int maxWidth;
            int maxHeight;
            if (srcH <= 60 && srcW <= 200) {
                maxWidth = 480;
                maxHeight = 120;
            } else if (srcW > srcH * 1.3 || srcW > 400 || srcH > 200) {
                maxWidth = 2400;
                maxHeight = 2000;
            } else {
                maxWidth = 1600;
                maxHeight = 1200;
            }
            BufferedImage resized = resizeImage(image, maxWidth, maxHeight);
            ImageIO.write(resized, "png", target.toFile());
            return;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private BufferedImage resizeImage(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = 1.0;
        if (width > maxWidth) {
            scale = Math.min(scale, (double) maxWidth / width);
        }
        if (height > maxHeight) {
            scale = Math.min(scale, (double) maxHeight / height);
        }
        if (scale >= 1.0) {
            return source;
        }
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    private Path findBatchImageFile(String batchUuid, String scope, int index) {
        Path scopedDir = batchImageDir(batchUuid, normalizeScope(scope));
        for (String extension : IMAGE_EXTENSIONS) {
            Path file = scopedDir.resolve(index + "." + extension);
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    private ParseWordImageDTO cloneImage(ParseWordImageDTO source) {
        ParseWordImageDTO copy = new ParseWordImageDTO();
        copy.setIndex(source.getIndex());
        copy.setPlaceholder(source.getPlaceholder());
        copy.setPosition(source.getPosition());
        copy.setFilePath(source.getFilePath());
        copy.setDisplayPath(source.getDisplayPath());
        copy.setScope(source.getScope());
        return copy;
    }

    private String extractBatchFromUrl(String url) {
        if (url == null) {
            return "";
        }
        String marker = "/assets/batches/";
        int start = url.indexOf(marker);
        if (start < 0) {
            return "";
        }
        String rest = url.substring(start + marker.length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }

    private Path imageRoot() {
        return Paths.get(paperProperties.getUpload().getBaseDir(), "images");
    }

    private Path batchImageDir(String batchUuid, String scope) {
        return imageRoot().resolve("batches").resolve(batchUuid).resolve(normalizeScope(scope));
    }

    private Path questionImageDir(Long questionId) {
        return imageRoot().resolve("questions").resolve(String.valueOf(questionId));
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "STEM";
        }
        return scope.trim().toUpperCase();
    }

    private String buildBatchImageUrl(String batchUuid, String scope, int index) {
        return "/api/assets/batches/" + batchUuid + "/images/" + normalizeScope(scope) + "/" + index;
    }

    private String buildQuestionImageUrl(Long questionId, String scope, int index) {
        return "/api/assets/questions/" + questionId + "/images/" + normalizeScope(scope) + "/" + index;
    }

    private Path findQuestionImageFile(Long questionId, String scope, int index) {
        Path scopedDir = questionImageDir(questionId).resolve(normalizeScope(scope));
        for (String extension : IMAGE_EXTENSIONS) {
            Path file = scopedDir.resolve(index + "." + extension);
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    private void clearQuestionImageFiles(Path scopedDir, int index) throws IOException {
        for (String extension : IMAGE_EXTENSIONS) {
            Files.deleteIfExists(scopedDir.resolve(index + "." + extension));
        }
    }

    private String resolveImageExtension(MultipartFile file) {
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (contentType.contains("png") || originalName.endsWith(".png")) {
            return "png";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")
                || originalName.endsWith(".jpg") || originalName.endsWith(".jpeg")) {
            return "jpg";
        }
        if (contentType.contains("webp") || originalName.endsWith(".webp")) {
            return "webp";
        }
        if (contentType.contains("gif") || originalName.endsWith(".gif")) {
            return "gif";
        }
        throw new BusinessException(BizCode.BAD_REQUEST, "仅支持 png/jpg/jpeg/webp/gif 图片");
    }
}
