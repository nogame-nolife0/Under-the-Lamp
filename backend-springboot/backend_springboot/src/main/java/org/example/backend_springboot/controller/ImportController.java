package org.example.backend_springboot.controller;

import org.example.backend_springboot.common.Result;
import org.example.backend_springboot.dto.request.ImportBatchUpdateRequest;
import org.example.backend_springboot.dto.request.ImportItemUpdateRequest;
import org.example.backend_springboot.dto.vo.ImportConfirmVO;
import org.example.backend_springboot.dto.vo.ImportItemVO;
import org.example.backend_springboot.dto.vo.ImportUploadVO;
import org.example.backend_springboot.service.ImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/upload")
    public Result<ImportUploadVO> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                         @RequestParam(value = "stemFiles", required = false) MultipartFile[] stemFiles,
                                         @RequestParam(value = "answerFiles", required = false) MultipartFile[] answerFiles,
                                         @RequestParam(value = "answerFile", required = false) MultipartFile answerFile,
                                         @RequestParam("subject") String subject,
                                         @RequestParam(value = "grade", required = false) String grade) {
        List<MultipartFile> mergedStemFiles = new ArrayList<>();
        if (file != null && !file.isEmpty()) {
            mergedStemFiles.add(file);
        }
        if (stemFiles != null) {
            for (MultipartFile item : stemFiles) {
                if (item != null && !item.isEmpty()) {
                    mergedStemFiles.add(item);
                }
            }
        }
        List<MultipartFile> mergedAnswerFiles = new ArrayList<>();
        if (answerFiles != null) {
            for (MultipartFile item : answerFiles) {
                if (item != null && !item.isEmpty()) {
                    mergedAnswerFiles.add(item);
                }
            }
        }
        if (answerFile != null && !answerFile.isEmpty()) {
            mergedAnswerFiles.add(answerFile);
        }
        return Result.success(importService.uploadAndParse(mergedStemFiles, mergedAnswerFiles, subject, grade));
    }

    @PutMapping("/batches/{batchUuid}")
    public Result<Void> updateBatch(@PathVariable String batchUuid,
                                    @RequestBody ImportBatchUpdateRequest request) {
        importService.updateBatch(batchUuid, request);
        return Result.success();
    }

    @GetMapping("/batches/{batchUuid}")
    public Result<ImportUploadVO> getBatch(@PathVariable String batchUuid) {
        return Result.success(importService.getBatch(batchUuid));
    }

    @GetMapping("/batches/{batchUuid}/items")
    public Result<List<ImportItemVO>> listItems(@PathVariable String batchUuid,
                                                @RequestParam(required = false) String status) {
        return Result.success(importService.listItems(batchUuid, status));
    }

    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable Long id,
                                   @RequestBody ImportItemUpdateRequest request) {
        importService.updateItem(id, request);
        return Result.success();
    }

    @PostMapping("/items/{id}/accept")
    public Result<Void> acceptItem(@PathVariable Long id) {
        importService.acceptItem(id);
        return Result.success();
    }

    @PostMapping("/items/{id}/reject")
    public Result<Void> rejectItem(@PathVariable Long id) {
        importService.rejectItem(id);
        return Result.success();
    }

    @PostMapping("/batches/{batchUuid}/confirm")
    public Result<ImportConfirmVO> confirmBatch(@PathVariable String batchUuid) {
        return Result.success(importService.confirmBatch(batchUuid));
    }
}
