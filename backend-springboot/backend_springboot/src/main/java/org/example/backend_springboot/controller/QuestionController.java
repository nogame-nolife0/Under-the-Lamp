package org.example.backend_springboot.controller;

import jakarta.validation.Valid;
import org.example.backend_springboot.common.PageResult;
import org.example.backend_springboot.common.Result;
import org.example.backend_springboot.dto.request.QuestionBatchDeleteRequest;
import org.example.backend_springboot.dto.request.QuestionBatchUpdateSubjectRequest;
import org.example.backend_springboot.dto.vo.QuestionBatchUpdateSubjectVO;
import org.example.backend_springboot.dto.vo.QuestionDeleteVO;
import org.example.backend_springboot.dto.vo.QuestionEmbedSyncVO;
import org.example.backend_springboot.dto.vo.QuestionVO;
import org.example.backend_springboot.service.QuestionEmbedService;
import org.example.backend_springboot.service.QuestionService;
import org.example.backend_springboot.dto.request.QuestionUpdateRequest;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionEmbedService questionEmbedService;

    public QuestionController(QuestionService questionService,
                              QuestionEmbedService questionEmbedService) {
        this.questionService = questionService;
        this.questionEmbedService = questionEmbedService;
    }

    @GetMapping("/subjects")
    public Result<List<String>> listSubjects() {
        return Result.success(questionService.listSubjects());
    }

    @GetMapping
    public Result<PageResult<QuestionVO>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                               @RequestParam(defaultValue = "10") Long pageSize,
                                               @RequestParam(required = false) String subject,
                                               @RequestParam(required = false) String grade,
                                               @RequestParam(required = false) String questionType,
                                               @RequestParam(required = false) String difficulty,
                                               @RequestParam(required = false) String chapter,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(questionService.pageQuestions(
                pageNum, pageSize, subject, grade, questionType, difficulty, chapter, keyword));
    }

    @GetMapping("/{id}")
    public Result<QuestionVO> detail(@PathVariable Long id) {
        return Result.success(questionService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<QuestionVO> update(@PathVariable Long id,
                                     @Valid @RequestBody QuestionUpdateRequest request) {
        return Result.success(questionService.updateQuestion(id, request));
    }

    @PostMapping("/{id}/images/{scope}/{index}")
    public Result<QuestionImageVO> replaceImage(@PathVariable Long id,
                                                @PathVariable String scope,
                                                @PathVariable int index,
                                                @RequestParam("file") MultipartFile file) {
        return Result.success(questionService.replaceQuestionImage(id, scope, index, file));
    }

    @PostMapping("/batch-delete")
    public Result<QuestionDeleteVO> batchDelete(@Valid @RequestBody QuestionBatchDeleteRequest request) {
        return Result.success(questionService.batchDelete(request));
    }

    @PostMapping("/batch-update-subject")
    public Result<QuestionBatchUpdateSubjectVO> batchUpdateSubject(
            @Valid @RequestBody QuestionBatchUpdateSubjectRequest request) {
        return Result.success(questionService.batchUpdateSubject(request));
    }

    @PostMapping("/sync-embed")
    public Result<QuestionEmbedSyncVO> syncEmbed(@RequestParam(defaultValue = "50") int batchSize) {
        return Result.success(questionEmbedService.syncPendingQuestions(batchSize));
    }
}
