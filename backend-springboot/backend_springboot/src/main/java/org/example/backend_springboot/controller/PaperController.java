package org.example.backend_springboot.controller;

import jakarta.validation.Valid;
import org.example.backend_springboot.common.PageResult;
import org.example.backend_springboot.common.Result;
import org.example.backend_springboot.dto.request.PaperBatchDeleteRequest;
import org.example.backend_springboot.dto.request.PaperCreateRequest;
import org.example.backend_springboot.dto.request.PaperSmartAlternativeRequest;
import org.example.backend_springboot.dto.request.PaperSmartComposeRequest;
import org.example.backend_springboot.dto.request.PaperSmartPreviewRequest;
import org.example.backend_springboot.dto.vo.PaperDeleteVO;
import org.example.backend_springboot.dto.vo.PaperSmartComposeVO;
import org.example.backend_springboot.dto.vo.PaperSmartPreviewVO;
import org.example.backend_springboot.dto.vo.PaperVO;
import org.example.backend_springboot.dto.vo.QuestionVO;
import org.example.backend_springboot.service.PaperExportService;
import org.example.backend_springboot.service.PaperService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;
    private final PaperExportService paperExportService;

    public PaperController(PaperService paperService, PaperExportService paperExportService) {
        this.paperService = paperService;
        this.paperExportService = paperExportService;
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody PaperCreateRequest request) {
        Long paperId = paperService.createPaper(request);
        return Result.success(Map.of("id", paperId));
    }

    @PostMapping("/smart/preview")
    public Result<PaperSmartPreviewVO> smartPreview(@Valid @RequestBody PaperSmartPreviewRequest request) {
        return Result.success(paperService.smartPreview(request));
    }

    @PostMapping("/smart/alternatives")
    public Result<List<QuestionVO>> smartAlternatives(@Valid @RequestBody PaperSmartAlternativeRequest request) {
        return Result.success(paperService.smartAlternatives(request));
    }

    @PostMapping("/smart")
    public Result<PaperSmartComposeVO> smartCompose(@Valid @RequestBody PaperSmartComposeRequest request) {
        return Result.success(paperService.smartCompose(request));
    }

    @GetMapping
    public Result<PageResult<PaperVO>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                          @RequestParam(defaultValue = "10") Long pageSize,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(paperService.pagePapers(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public Result<PaperVO> detail(@PathVariable Long id) {
        return Result.success(paperService.getById(id));
    }

    @PostMapping("/batch-delete")
    public Result<PaperDeleteVO> batchDelete(@Valid @RequestBody PaperBatchDeleteRequest request) {
        return Result.success(paperService.batchDelete(request));
    }

    @RequestMapping(value = "/{id}/export", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Resource> export(@PathVariable Long id,
                                           @RequestParam String exportType) throws IOException {
        Resource resource = paperExportService.exportWord(id, exportType);
        PaperVO paper = paperService.getById(id);
        String suffix = "TEACHER".equals(exportType) ? "_教师版.docx" : "_学生版.docx";
        String downloadName = sanitizeDownloadName(paper.getTitle()) + suffix;
        String encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        long contentLength = resource.contentLength();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    private String sanitizeDownloadName(String title) {
        if (title == null || title.isBlank()) {
            return "试卷";
        }
        return title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
