package org.example.backend_springboot.util;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public final class DocxTextUtils {

    private DocxTextUtils() {
    }

    public static String toPlainText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String plain = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]+>", "");
        return sanitizeForXml(plain.trim());
    }

    public static String sanitizeForXml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == 0x9 || ch == 0xA || ch == 0xD
                    || (ch >= 0x20 && ch <= 0xD7FF)
                    || (ch >= 0xE000 && ch <= 0xFFFD)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static void writeMultilineText(XWPFParagraph paragraph, String text, int fontSize, String fontFamily) {
        String plain = toPlainText(text);
        if (plain.isEmpty()) {
            return;
        }
        String[] lines = plain.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setText(lines[i]);
            run.setFontSize(fontSize);
            run.setFontFamily(fontFamily);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }
}
