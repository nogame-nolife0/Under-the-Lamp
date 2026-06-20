package org.example.backend_springboot.util;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DocxSmokeTest {

    @Test
    void generatedDocxShouldBeValidZipArchive() throws Exception {
        Path filePath = Files.createTempFile("smoke", ".docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("测试试卷");
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            XWPFParagraph stemPara = document.createParagraph();
            DocxTextUtils.writeMultilineText(
                    stemPara,
                    "1. 题干含特殊字符 <tag> & \"引号\"\n第二行",
                    12,
                    "宋体"
            );

            document.write(Files.newOutputStream(filePath));
        }

        try (InputStream is = Files.newInputStream(filePath);
             ZipInputStream zip = new ZipInputStream(is)) {
            assertNotNull(zip.getNextEntry(), "docx should contain zip entries");
        }

        byte[] header = Files.readAllBytes(filePath);
        assertEquals('P', (char) header[0]);
        assertEquals('K', (char) header[1]);
    }
}
