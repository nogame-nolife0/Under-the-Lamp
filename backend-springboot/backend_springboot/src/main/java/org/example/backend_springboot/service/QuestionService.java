package org.example.backend_springboot.service;

import org.example.backend_springboot.common.PageResult;
import org.example.backend_springboot.dto.request.QuestionBatchDeleteRequest;
import org.example.backend_springboot.dto.request.QuestionBatchUpdateSubjectRequest;
import org.example.backend_springboot.dto.request.QuestionUpdateRequest;
import org.example.backend_springboot.dto.vo.QuestionImageVO;
import org.example.backend_springboot.dto.vo.QuestionBatchUpdateSubjectVO;
import org.example.backend_springboot.dto.vo.QuestionDeleteVO;
import org.example.backend_springboot.dto.vo.QuestionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionService {

    PageResult<QuestionVO> pageQuestions(Long pageNum,
                                         Long pageSize,
                                         String subject,
                                         String grade,
                                         String questionType,
                                         String difficulty,
                                         String chapter,
                                         String keyword);

    QuestionVO getById(Long id);

    List<QuestionVO> listByIds(List<Long> ids);

    QuestionDeleteVO batchDelete(QuestionBatchDeleteRequest request);

    List<Long> searchIdsByCondition(String subject,
                                    String grade,
                                    String questionType,
                                    String difficulty,
                                    String chapter,
                                    String keyword,
                                    int limit,
                                    List<Long> excludeIds);

    List<Long> listRecentActiveIds(int limit, List<Long> excludeIds);

    List<String> listSubjects();

    QuestionBatchUpdateSubjectVO batchUpdateSubject(QuestionBatchUpdateSubjectRequest request);

    QuestionVO updateQuestion(Long id, QuestionUpdateRequest request);

    QuestionImageVO replaceQuestionImage(Long id, String scope, int index, MultipartFile file);
}
