package org.example.backend_springboot.service;

import org.springframework.core.io.Resource;

public interface PaperExportService {

    Resource exportWord(Long paperId, String exportType);
}
