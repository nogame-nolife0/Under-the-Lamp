package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.request.ImportBatchUpdateRequest;
import org.example.backend_springboot.dto.request.ImportItemUpdateRequest;
import org.example.backend_springboot.dto.vo.ImportConfirmVO;
import org.example.backend_springboot.dto.vo.ImportItemVO;
import org.example.backend_springboot.dto.vo.ImportUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportService {

    ImportUploadVO uploadAndParse(List<MultipartFile> stemFiles, List<MultipartFile> answerFiles, String subject, String grade);

    ImportUploadVO getBatch(String batchUuid);

    void updateBatch(String batchUuid, ImportBatchUpdateRequest request);

    List<ImportItemVO> listItems(String batchUuid, String status);

    void updateItem(Long itemId, ImportItemUpdateRequest request);

    void acceptItem(Long itemId);

    void rejectItem(Long itemId);

    ImportConfirmVO confirmBatch(String batchUuid);
}
