package org.example.backend_springboot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将裸 LaTeX / \( \) 格式统一为 $...$，供 Word 导出时 jLaTeXMath 渲染。
 */
public final class LatexDelimiterUtils {

    private static final Pattern MATH_SPLIT = Pattern.compile("(\\$\\$[\\s\\S]+?\\$\\$|\\$[^$\\n]+?\\$)");
    private static final Pattern PAREN_INLINE = Pattern.compile("\\\\\\((.+?)\\\\\\)");
    private static final Pattern PAREN_BLOCK = Pattern.compile("\\\\\\[(.+?)\\\\\\]");
    private static final Pattern LATEX_CMD = Pattern.compile(
            "\\\\(?:overline|underline|frac|sqrt|cdot|times|bar|vec|hat|tilde|"
                    + "text|mathrm|mathbf|left|right|quad|pm|mp|leq|geq|neq|approx|"
                    + "sum|prod|int|alpha|beta|gamma|delta|pi|theta|lambda|mu|sigma|omega)\\b");
    private static final Pattern VAR_EQ = Pattern.compile("[A-Za-z]\\s*=\\s*(?=\\\\)");

    private LatexDelimiterUtils() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (ImageMarkerUtils.IMAGE_MARKER_PATTERN.matcher(text).find()) {
            return ImageMarkerUtils.preferImageMarkersOverLatex(text);
        }
        if (!text.contains("\\") && !text.contains("$")) {
            return text;
        }
        String converted = PAREN_INLINE.matcher(text).replaceAll("\\$$1\\$");
        converted = PAREN_BLOCK.matcher(converted).replaceAll("\\$\\$$1\\$\\$");
        return wrapSegmentsOutsideMath(converted);
    }

    private static String wrapSegmentsOutsideMath(String text) {
        Matcher matcher = MATH_SPLIT.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                out.append(wrapBareLatexInPlain(text.substring(last, matcher.start())));
            }
            out.append(matcher.group());
            last = matcher.end();
        }
        if (last < text.length()) {
            out.append(wrapBareLatexInPlain(text.substring(last)));
        }
        return out.toString();
    }

    private static String wrapBareLatexInPlain(String text) {
        if (!text.contains("\\")) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length() + 8);
        int i = 0;
        int n = text.length();
        while (i < n) {
            Integer start = findLatexStart(text, i);
            if (start == null) {
                result.append(text.charAt(i));
                i++;
                continue;
            }
            result.append(text, i, start);
            int end = scanLatexEnd(text, start);
            result.append('$').append(text, start, end).append('$');
            i = end;
        }
        result.append(text, i, n);
        return result.toString();
    }

    private static Integer findLatexStart(String text, int pos) {
        for (int i = pos; i < text.length(); i++) {
            if (text.charAt(i) == '\\') {
                Matcher cmd = LATEX_CMD.matcher(text);
                if (cmd.find(i) && cmd.start() == i) {
                    Matcher prefix = VAR_EQ.matcher(text.substring(pos, i));
                    if (prefix.find()) {
                        return pos + prefix.start();
                    }
                    return i;
                }
            }
            Matcher eq = VAR_EQ.matcher(text.substring(i));
            if (eq.lookingAt()) {
                Matcher cmd = LATEX_CMD.matcher(text);
                if (cmd.find(i + eq.end())) {
                    return i;
                }
            }
        }
        return null;
    }

    private static int scanLatexEnd(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                Matcher cmd = LATEX_CMD.matcher(text);
                if (!cmd.find(i) || cmd.start() != i) {
                    break;
                }
                i = cmd.end();
                while (i < text.length() && text.charAt(i) == ' ') {
                    i++;
                }
                if (i < text.length() && text.charAt(i) == '{') {
                    i = skipBraceGroup(text, i) + 1;
                }
                continue;
            }
            if (ch == '_' || ch == '^') {
                i++;
                if (i < text.length() && text.charAt(i) == '{') {
                    i = skipBraceGroup(text, i) + 1;
                } else if (i < text.length()) {
                    i++;
                }
                continue;
            }
            if ("+-=·()[]".indexOf(ch) >= 0) {
                i++;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                int j = i + 1;
                while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j < text.length()) {
                    char next = text.charAt(j);
                    if (next == '\\' || "+-=·".indexOf(next) >= 0) {
                        i = j;
                        continue;
                    }
                }
                break;
            }
            if (ch < 128 && (Character.isLetterOrDigit(ch) || ch == ',' || ch == '.')) {
                i++;
                continue;
            }
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                break;
            }
            break;
        }
        return i;
    }

    private static int skipBraceGroup(String text, int pos) {
        if (pos >= text.length() || text.charAt(pos) != '{') {
            return pos;
        }
        int depth = 0;
        int i = pos;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return pos;
    }
}
