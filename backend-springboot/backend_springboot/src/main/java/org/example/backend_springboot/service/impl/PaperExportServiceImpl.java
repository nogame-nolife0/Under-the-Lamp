package org.example.backend_springboot.service.impl;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.config.PaperProperties;
import org.example.backend_springboot.dto.vo.PaperQuestionVO;
import org.example.backend_springboot.dto.vo.PaperVO;
import org.example.backend_springboot.entity.ExportRecord;
import org.example.backend_springboot.entity.Paper;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.ExportRecordMapper;
import org.example.backend_springboot.mapper.PaperMapper;
import org.example.backend_springboot.service.ImageAssetService;
import org.example.backend_springboot.service.PaperExportService;
import org.example.backend_springboot.service.PaperService;
import org.example.backend_springboot.util.DocxRichTextUtils;
import org.example.backend_springboot.util.DocxTextUtils;
import org.example.backend_springboot.util.StemSanitizeUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaperExportServiceImpl implements PaperExportService {

    private final PaperService paperService;
    private final PaperMapper paperMapper;
    private final ExportRecordMapper exportRecordMapper;
    private final PaperProperties paperProperties;
    private final ImageAssetService imageAssetService;

    public PaperExportServiceImpl(PaperService paperService,
                                  PaperMapper paperMapper,
                                  ExportRecordMapper exportRecordMapper,
                                  PaperProperties paperProperties,
                                  ImageAssetService imageAssetService) {
        this.paperService = paperService;
        this.paperMapper = paperMapper;
        this.exportRecordMapper = exportRecordMapper;
        this.paperProperties = paperProperties;
        this.imageAssetService = imageAssetService;
    }

    @Override
    public Resource exportWord(Long paperId, String exportType) {
        if (!"STUDENT".equals(exportType) && !"TEACHER".equals(exportType)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "exportType 仅支持 STUDENT 或 TEACHER");
        }

        PaperVO paper = paperService.getById(paperId);
        boolean includeAnswer = "TEACHER".equals(exportType);

        ExportRecord record = new ExportRecord();
        record.setPaperId(paperId);
        record.setExportType(exportType);
        record.setFileFormat("DOCX");
        record.setStatus("GENERATING");
        record.setCreatedAt(LocalDateTime.now());

        try {
            String suffix = includeAnswer ? "_教师版" : "_学生版";
            String fileName = sanitizeFileName(paper.getTitle()) + suffix + ".docx";

            Path exportDir = Paths.get(paperProperties.getExport().getBaseDir());
            Files.createDirectories(exportDir);
            Path filePath = exportDir.resolve(System.currentTimeMillis() + "_" + fileName);

            record.setFileName(fileName);
            record.setFilePath(filePath.toString());
            exportRecordMapper.insert(record);

            byte[] content = buildDocumentBytes(paper, includeAnswer);
            Files.write(filePath, content);

            record.setFileSize((long) content.length);
            record.setStatus("SUCCESS");
            exportRecordMapper.updateById(record);

            Paper update = new Paper();
            update.setId(paperId);
            update.setStatus("COMPLETED");
            update.setUpdatedAt(LocalDateTime.now());
            paperMapper.updateById(update);

            return new ByteArrayResource(content);
        } catch (BusinessException ex) {
            markFailed(record, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(record, ex.getMessage());
            throw new BusinessException(BizCode.INTERNAL_ERROR, "Word 导出失败: " + ex.getMessage());
        }
    }

    private byte[] buildDocumentBytes(PaperVO paper, boolean includeAnswer) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeTitle(document, paper);
            writeMeta(document, paper);
            writeQuestions(document, paper.getQuestions(), includeAnswer);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void markFailed(ExportRecord record, String message) {
        if (record.getId() == null) {
            return;
        }
        record.setStatus("FAILED");
        record.setErrorMessage(message);
        exportRecordMapper.updateById(record);
    }

    private void writeTitle(XWPFDocument document, PaperVO paper) {
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(DocxTextUtils.toPlainText(paper.getTitle()));
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setFontFamily("宋体");
    }

    private void writeMeta(XWPFDocument document, PaperVO paper) {
        StringBuilder meta = new StringBuilder();
        if (paper.getSubject() != null && !paper.getSubject().isBlank()) {
            meta.append(paper.getSubject()).append("  ");
        }
        if (paper.getGrade() != null && !paper.getGrade().isBlank()) {
            meta.append(paper.getGrade()).append("  ");
        }
        if (paper.getTotalScore() != null) {
            meta.append("满分").append(paper.getTotalScore().stripTrailingZeros().toPlainString()).append("分  ");
        }
        if (paper.getDurationMinutes() != null) {
            meta.append("时长").append(paper.getDurationMinutes()).append("分钟");
        }
        if (paper.getCreatedBy() != null && !paper.getCreatedBy().isBlank()) {
            if (!meta.isEmpty()) {
                meta.append("  ");
            }
            meta.append("出题人：").append(paper.getCreatedBy());
        }
        if (meta.isEmpty()) {
            return;
        }

        XWPFParagraph metaPara = document.createParagraph();
        metaPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun metaRun = metaPara.createRun();
        metaRun.setText(DocxTextUtils.toPlainText(meta.toString().trim()));
        metaRun.setFontSize(12);
        metaRun.setFontFamily("宋体");

        document.createParagraph();
    }

    private void writeQuestions(XWPFDocument document, List<PaperQuestionVO> questions, boolean includeAnswer) {
        int index = 1;
        for (PaperQuestionVO question : questions) {
            XWPFParagraph stemPara = document.createParagraph();
            String scorePart = question.getScore() != null
                    ? "（" + question.getScore().stripTrailingZeros().toPlainString() + "分）" : "";
            XWPFRun stemIndexRun = stemPara.createRun();
            stemIndexRun.setText(index + ". ");
            stemIndexRun.setFontSize(12);
            stemIndexRun.setFontFamily("宋体");
            Map<Integer, Path> stemImages = imageAssetService.resolveQuestionImagePaths(
                    question.getQuestionId(), "STEM", question.getImagesJson(),
                    buildStemImageContext(question));
            DocxRichTextUtils.writeRichText(
                    stemPara,
                    nullToEmpty(StemSanitizeUtils.sanitizeStem(question.getStem(), null)) + scorePart,
                    12,
                    "宋体",
                    stemImages
            );

            List<String> options = question.getOptions();
            if (options != null && !options.isEmpty()) {
                for (String option : options) {
                    XWPFParagraph optPara = document.createParagraph();
                    optPara.setIndentationLeft(400);
                    DocxRichTextUtils.writeRichText(optPara, option, 12, "宋体", stemImages);
                }
            }

            if (includeAnswer) {
                if (question.getAnswer() != null && !question.getAnswer().isBlank()) {
                    XWPFParagraph ansPara = document.createParagraph();
                    XWPFRun ansLabelRun = ansPara.createRun();
                    ansLabelRun.setText("【答案】");
                    ansLabelRun.setColor("006600");
                    ansLabelRun.setFontSize(12);
                    ansLabelRun.setFontFamily("宋体");
                    Map<Integer, Path> answerImages = imageAssetService.resolveQuestionImagePaths(
                            question.getQuestionId(), "ANSWER", question.getImagesJson(),
                            question.getAnswer(), question.getAnalysis());
                    DocxRichTextUtils.writeRichText(
                            ansPara,
                            StemSanitizeUtils.sanitizeAnswer(question.getAnswer(), null),
                            12,
                            "宋体",
                            answerImages);
                }
                if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
                    XWPFParagraph anaPara = document.createParagraph();
                    XWPFRun anaLabelRun = anaPara.createRun();
                    anaLabelRun.setText("【解析】");
                    anaLabelRun.setFontSize(12);
                    anaLabelRun.setFontFamily("宋体");
                    DocxRichTextUtils.writeRichText(anaPara, question.getAnalysis(), 12, "宋体", stemImages);
                }
            }

            document.createParagraph();
            index++;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String buildStemImageContext(PaperQuestionVO question) {
        StringBuilder builder = new StringBuilder();
        if (question.getStem() != null) {
            builder.append(question.getStem());
        }
        if (question.getOptions() != null) {
            for (String option : question.getOptions()) {
                if (option != null && !option.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(option);
                }
            }
        }
        return builder.toString();
    }

    private String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) {
            return "试卷";
        }
        return title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
