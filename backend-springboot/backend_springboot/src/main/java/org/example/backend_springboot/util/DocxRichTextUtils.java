package org.example.backend_springboot.util;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DocxRichTextUtils {

    private static final Pattern MATH_PATTERN = Pattern.compile("(\\$\\$[\\s\\S]+?\\$\\$|\\$[^$\\n]+?\\$)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[嵌入图片(\\d+)\\]");
    /** Word 内显示尺寸不变时，嵌入更高像素密度以提升打印/放大清晰度 */
    private static final int EMBED_IMAGE_DPI = 220;

    private DocxRichTextUtils() {
    }

    public static void writeRichText(XWPFParagraph paragraph, String text, int fontSize, String fontFamily) {
        writeRichText(paragraph, text, fontSize, fontFamily, Collections.emptyMap());
    }

    public static void writeRichText(XWPFParagraph paragraph,
                                     String text,
                                     int fontSize,
                                     String fontFamily,
                                     Map<Integer, Path> imageMap) {
        String plain = DocxTextUtils.toPlainText(text);
        plain = ImageMarkerUtils.preferImageMarkersOverLatex(plain);
        if (!ImageMarkerUtils.IMAGE_MARKER_PATTERN.matcher(plain).find()) {
            plain = LatexDelimiterUtils.normalize(plain);
        }
        if (plain.isEmpty()) {
            return;
        }
        String[] lines = plain.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            writeRichLine(paragraph, lines[i], fontSize, fontFamily, imageMap);
            if (i < lines.length - 1) {
                XWPFRun breakRun = paragraph.createRun();
                breakRun.addBreak();
            }
        }
    }

    private static void writeRichLine(XWPFParagraph paragraph,
                                      String line,
                                      int fontSize,
                                      String fontFamily,
                                      Map<Integer, Path> imageMap) {
        if (line.isEmpty()) {
            return;
        }
        List<Segment> segments = parseSegments(line);
        for (Segment segment : segments) {
            switch (segment.type) {
                case TEXT -> writeTextRun(paragraph, segment.content, fontSize, fontFamily);
                case IMAGE -> writeImageRun(paragraph, segment.content, fontSize, fontFamily, imageMap);
                case MATH_INLINE -> writeMathRun(paragraph, segment.content, fontSize, false);
                case MATH_DISPLAY -> writeMathRun(paragraph, segment.content, fontSize, true);
                default -> {
                }
            }
        }
    }

    private static void writeImageRun(XWPFParagraph paragraph,
                                        String indexText,
                                        int fontSize,
                                        String fontFamily,
                                        Map<Integer, Path> imageMap) {
        try {
            int index = Integer.parseInt(indexText);
            Path imagePath = imageMap.get(index);
            if (imagePath != null && Files.exists(imagePath)) {
                try (InputStream inputStream = Files.newInputStream(imagePath)) {
                    byte[] bytes = inputStream.readAllBytes();
                    BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (bufferedImage == null) {
                        return;
                    }
                    int[] displaySizePt = resolvePictureDisplaySizePt(bufferedImage, fontSize);
                    int widthPt = displaySizePt[0];
                    int heightPt = displaySizePt[1];
                    byte[] pictureBytes = renderScaledPictureBytes(bufferedImage, widthPt, heightPt);
                    int pictureType = Document.PICTURE_TYPE_PNG;
                    String pictureName = imagePath.getFileName().toString();
                    XWPFRun run = paragraph.createRun();
                    run.setTextPosition(Math.round(-fontSize * 0.2f));
                    run.addPicture(
                            new ByteArrayInputStream(pictureBytes),
                            pictureType,
                            pictureName,
                            Units.toEMU(widthPt),
                            Units.toEMU(heightPt)
                    );
                    return;
                }
            }
        } catch (Exception ignored) {
            // 回退为占位文字
        }
        writeTextRun(paragraph, "图" + indexText, fontSize, fontFamily);
    }

    /** 按图片类型计算 Word 中的显示尺寸（单位：磅 point，非像素）。 */
    private static int[] resolvePictureDisplaySizePt(BufferedImage image, int fontSize) {
        int srcW = Math.max(1, image.getWidth());
        int srcH = Math.max(1, image.getHeight());
        boolean inlineSymbol = srcH <= 120 && srcW <= 480;
        boolean wideDiagram = srcW > srcH * 1.6 && srcW > 400;

        int heightPt;
        if (inlineSymbol) {
            heightPt = Math.max(10, Math.round(fontSize * 1.05f));
        } else if (wideDiagram) {
            heightPt = Math.min(120, Math.max(48, Math.round(fontSize * 4f)));
        } else {
            heightPt = Math.max(14, Math.round(fontSize * 1.4f));
        }

        int widthPt = Math.max(1, (int) Math.round(heightPt * (srcW / (double) srcH)));
        int maxWidthPt = wideDiagram
                ? Math.round(fontSize * 18f)
                : (inlineSymbol ? Math.round(fontSize * 3.2f) : Math.round(fontSize * 6f));
        if (widthPt > maxWidthPt) {
            widthPt = maxWidthPt;
            heightPt = Math.max(1, (int) Math.round(widthPt * (srcH / (double) srcW)));
        }
        return new int[]{widthPt, heightPt};
    }

    private static byte[] renderScaledPictureBytes(BufferedImage source, int widthPt, int heightPt)
            throws IOException {
        int embedPxW = Math.max(1, Math.round(widthPt * EMBED_IMAGE_DPI / 72f));
        int embedPxH = Math.max(1, Math.round(heightPt * EMBED_IMAGE_DPI / 72f));
        if (source.getWidth() >= embedPxW && source.getHeight() >= embedPxH) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(source, "png", outputStream);
            return outputStream.toByteArray();
        }

        double scaleW = embedPxW / (double) Math.max(1, source.getWidth());
        double scaleH = embedPxH / (double) Math.max(1, source.getHeight());
        double scale = Math.max(scaleW, scaleH);
        if (scale <= 1.0) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(source, "png", outputStream);
            return outputStream.toByteArray();
        }

        int targetPxW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetPxH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(targetPxW, targetPxH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetPxW, targetPxH, null);
        graphics.dispose();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", outputStream);
        return outputStream.toByteArray();
    }

    private static List<Segment> parseSegments(String line) {
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = MATH_PATTERN.matcher(line);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                appendTextWithImages(segments, line.substring(last, matcher.start()));
            }
            String math = matcher.group();
            boolean display = math.startsWith("$$");
            String latex = display ? math.substring(2, math.length() - 2) : math.substring(1, math.length() - 1);
            segments.add(new Segment(display ? SegmentType.MATH_DISPLAY : SegmentType.MATH_INLINE, latex.trim()));
            last = matcher.end();
        }
        if (last < line.length()) {
            appendTextWithImages(segments, line.substring(last));
        }
        return segments;
    }

    private static void appendTextWithImages(List<Segment> segments, String text) {
        if (text.isEmpty()) {
            return;
        }
        Matcher matcher = IMAGE_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                segments.add(new Segment(SegmentType.TEXT, text.substring(last, matcher.start())));
            }
            segments.add(new Segment(SegmentType.IMAGE, matcher.group(1)));
            last = matcher.end();
        }
        if (last < text.length()) {
            segments.add(new Segment(SegmentType.TEXT, text.substring(last)));
        }
    }

    private static void writeTextRun(XWPFParagraph paragraph, String text, int fontSize, String fontFamily) {
        if (text == null || text.isEmpty()) {
            return;
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setFontFamily(fontFamily);
    }

    private static void writeMathRun(XWPFParagraph paragraph, String latex, int fontSize, boolean display) {
        if (latex == null || latex.isBlank()) {
            return;
        }
        try {
            TeXFormula formula = new TeXFormula(latex);
            int style = display ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT;
            TeXIcon icon = formula.createTeXIcon(style, fontSize);
            BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            icon.paintIcon(null, graphics, 0, 0);
            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);
            XWPFRun run = paragraph.createRun();
            run.addPicture(
                    new ByteArrayInputStream(outputStream.toByteArray()),
                    Document.PICTURE_TYPE_PNG,
                    "formula.png",
                    Units.toEMU(icon.getIconWidth()),
                    Units.toEMU(icon.getIconHeight())
            );
        } catch (Exception ex) {
            writeTextRun(paragraph, latex, fontSize, "Cambria Math");
        }
    }

    private static int resolvePictureType(Path imagePath) {
        String name = imagePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".emf") || name.endsWith(".wmf")) {
            return Document.PICTURE_TYPE_EMF;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return Document.PICTURE_TYPE_JPEG;
        }
        if (name.endsWith(".gif")) {
            return Document.PICTURE_TYPE_GIF;
        }
        if (name.endsWith(".bmp")) {
            return Document.PICTURE_TYPE_BMP;
        }
        return Document.PICTURE_TYPE_PNG;
    }

    private enum SegmentType {
        TEXT, IMAGE, MATH_INLINE, MATH_DISPLAY
    }

    private record Segment(SegmentType type, String content) {
    }
}
