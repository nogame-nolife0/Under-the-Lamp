package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.agent.ParseWordImageDTO;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ImageAssetService {

    void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images);

    void persistDocumentImages(String batchUuid, String scope, List<ParseWordImageDTO> images, Path wordFilePath);

    List<ParseWordImageDTO> enrichImageUrls(String batchUuid, List<ParseWordImageDTO> images);

    List<QuestionImageVO> copyToQuestion(Long questionId, List<ParseWordImageDTO> images);

    List<QuestionImageVO> copyToQuestion(Long questionId, String batchUuid, List<ParseWordImageDTO> images);

    List<QuestionImageVO> toQuestionImageVOs(String imagesJson);

    Resource loadBatchImage(String batchUuid, String scope, int index);

    Resource loadQuestionImage(Long questionId, String scope, int index);

    Map<Integer, Path> resolveQuestionImagePaths(Long questionId, String scope, String imagesJson, String... relatedTexts);

    QuestionImageVO replaceQuestionImage(Long questionId, String scope, int index, MultipartFile file);
}
