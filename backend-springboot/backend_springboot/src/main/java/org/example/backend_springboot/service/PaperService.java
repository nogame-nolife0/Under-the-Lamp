package org.example.backend_springboot.service;

import org.example.backend_springboot.common.PageResult;
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

import java.util.List;

public interface PaperService {

    Long createPaper(PaperCreateRequest request);

    PaperVO getById(Long id);

    PageResult<PaperVO> pagePapers(Long pageNum, Long pageSize, String keyword);

    PaperDeleteVO batchDelete(PaperBatchDeleteRequest request);

    PaperSmartComposeVO smartCompose(PaperSmartComposeRequest request);

    PaperSmartPreviewVO smartPreview(PaperSmartPreviewRequest request);

    List<QuestionVO> smartAlternatives(PaperSmartAlternativeRequest request);
}
