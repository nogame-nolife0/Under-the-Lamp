package org.example.backend_springboot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题干清洗：去掉 OCR 垃圾前缀，并按题号截断误合并的后文。
 */
public final class StemSanitizeUtils {

    private static final Pattern GARBAGE_PREFIX = Pattern.compile("^[\\s01\\n]{4,}");
    private static final Pattern CHAPTER_LABEL = Pattern.compile("(?:(?<=\\n)|^)(\\d+-\\d+)\\s+");
    private static final Pattern CHAPTER_LABEL_INLINE = Pattern.compile("(?<=[。？?；!！\\n])(\\d+-\\d+)\\s+(?=[\\u4e00-\\u9fff])");
    private static final Pattern CHAPTER_LABEL_MID = Pattern.compile("(?<=\\s)(\\d+-\\d+)\\s+(?=解|[\\(（\\u4e00-\\u9fff])");
    private static final Pattern NUMBER_LABEL = Pattern.compile("(?:(?<=\\n)|^)(\\d+)[.、．]\\s*");
    private static final Pattern NUMBER_LABEL_INLINE = Pattern.compile("(?<=[。？?；!！\\n])(\\d+)[.、．]\\s+(?=[\\u4e00-\\u9fff])");
    private static final Pattern MERGED_OPENINGS = Pattern.compile(
            "(设计一个|写出下图|试用\\s*74|实现下列|优先编码器|用三线-八线|某车间有)");
    private static final Pattern OPTION_LINE = Pattern.compile("^[A-Ha-h][.、．、:：]");
    private static final Pattern INLINE_OPTION = Pattern.compile(
            "(?:(?<=\\n)|^|\\s|(?<=[\\]\\)）]))([A-Ha-h])[.、．、:：]\\s*");
    private static final Pattern ANSWER_CHOICE = Pattern.compile(
            "(?:[为应选]|需要|结论|答案)\\s*[（(]\\s*([A-Ha-h])\\s*[）)]");
    private static final Pattern ANSWER_SOLUTION = Pattern.compile("解\\s*[：:]");
    private static final Pattern TRAILING_CHOICE = Pattern.compile(
            "[（(]\\s*([A-Ha-h])\\s*[）)]\\s*[。．]?\\s*$");

    private StemSanitizeUtils() {
    }

    public static String sanitizeStem(String stem, String questionLabel) {
        if (stem == null || stem.isBlank()) {
            return stem;
        }
        String cleaned = stripGarbagePrefix(stem.strip());
        cleaned = clipToQuestionLabel(cleaned, questionLabel, true);
        return cleaned.isBlank() ? stem.strip() : cleaned;
    }

    public static String sanitizeAnswer(String answer, String questionLabel) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String cleaned = stripGarbagePrefix(answer.strip());
        cleaned = stripOptionsFromAnswer(cleaned);
        cleaned = clipToQuestionLabel(cleaned, questionLabel, false);
        cleaned = compactChoiceAnswer(cleaned);
        return cleaned.isBlank() ? answer.strip() : cleaned;
    }

    private static String stripOptionsFromAnswer(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String[] lines = content.split("\\R");
        StringBuilder stem = new StringBuilder();
        for (String line : lines) {
            String stripped = line.strip();
            if (OPTION_LINE.matcher(stripped).matches()) {
                break;
            }
            if (!stem.isEmpty()) {
                stem.append('\n');
            }
            stem.append(line);
        }
        String result = stem.toString().strip();
        if (!result.isBlank()) {
            Matcher inline = INLINE_OPTION.matcher(result);
            if (inline.find() && inline.start() > 10) {
                result = result.substring(0, inline.start()).strip();
            }
        }
        if (result.isBlank()) {
            Matcher inline = INLINE_OPTION.matcher(content);
            if (inline.find() && inline.start() > 10) {
                result = content.substring(0, inline.start()).strip();
            }
        }
        return result.isBlank() ? content.strip() : result;
    }

    private static String compactChoiceAnswer(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String stripped = text.strip();
        if (ANSWER_SOLUTION.matcher(stripped).find()) {
            return stripped;
        }
        Matcher choice = ANSWER_CHOICE.matcher(stripped);
        if (choice.find()) {
            return choice.group(1).toUpperCase();
        }
        Matcher trailing = TRAILING_CHOICE.matcher(stripped);
        if (trailing.find() && stripped.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF)) {
            return trailing.group(1).toUpperCase();
        }
        return stripped;
    }

    public static java.util.List<String> sanitizeOptions(java.util.List<String> options) {
        if (options == null || options.isEmpty()) {
            return options;
        }
        java.util.List<String> flattened = new java.util.ArrayList<>();
        Pattern labelPattern = Pattern.compile("([A-H])[.、．、:：]\\s*", Pattern.CASE_INSENSITIVE);
        for (String option : options) {
            if (option == null || option.isBlank()) {
                continue;
            }
            String text = option.strip();
            int markerCount = 0;
            Matcher counter = labelPattern.matcher(text);
            while (counter.find()) {
                markerCount++;
            }
            if (markerCount > 1) {
                flattened.addAll(splitOptionMarkers(text));
            } else {
                flattened.add(text);
            }
        }
        return takeFirstOptionSet(flattened);
    }

    private static java.util.List<String> splitOptionMarkers(String text) {
        Pattern labelPattern = Pattern.compile("([A-H])[.、．、:：]\\s*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = labelPattern.matcher(text);
        java.util.List<LabelMatch> matches = new java.util.ArrayList<>();
        while (matcher.find()) {
            matches.add(new LabelMatch(matcher.start(), matcher.end(), matcher.group(1).toUpperCase()));
        }
        int startIdx = -1;
        for (int i = 0; i < matches.size(); i++) {
            if ("A".equals(matches.get(i).label)) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < 0) {
            return java.util.List.of();
        }
        java.util.List<LabelMatch> selected = new java.util.ArrayList<>();
        int expected = 0;
        for (int i = startIdx; i < matches.size(); i++) {
            LabelMatch current = matches.get(i);
            int letterOrd = current.label.charAt(0) - 'A';
            if (letterOrd == expected) {
                selected.add(current);
                expected++;
            } else if (letterOrd == 0 && expected >= 2) {
                break;
            }
            if (expected >= 8) {
                break;
            }
        }
        if (selected.size() < 2) {
            return java.util.List.of();
        }
        java.util.List<String> extracted = new java.util.ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            LabelMatch current = selected.get(i);
            int bodyStart = current.end;
            int bodyEnd = i + 1 < selected.size() ? selected.get(i + 1).start : text.length();
            String body = text.substring(bodyStart, bodyEnd).strip();
            if (!body.isBlank()) {
                extracted.add(current.label + ". " + body);
            }
        }
        return extracted;
    }

    private static java.util.List<String> takeFirstOptionSet(java.util.List<String> options) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int expected = 0;
        Pattern headPattern = Pattern.compile("^([A-H])[.、．、:：]", Pattern.CASE_INSENSITIVE);
        for (String option : options) {
            Matcher matcher = headPattern.matcher(option);
            if (!matcher.find()) {
                continue;
            }
            int letterOrd = Character.toUpperCase(matcher.group(1).charAt(0)) - 'A';
            if (letterOrd == expected) {
                result.add(option);
                expected++;
            } else if (letterOrd == 0 && expected >= 2) {
                break;
            }
            if (expected >= 8) {
                break;
            }
        }
        if (result.size() >= 2) {
            return result;
        }
        return options.size() > 8 ? options.subList(0, 8) : options;
    }

    private static final class LabelMatch {
        final int start;
        final int end;
        final String label;

        LabelMatch(int start, int end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    private static String stripGarbagePrefix(String cleaned) {
        Matcher garbage = GARBAGE_PREFIX.matcher(cleaned);
        if (garbage.find()) {
            String stripped = cleaned.substring(garbage.end()).strip();
            if (!stripped.isBlank()) {
                return stripped;
            }
        }
        return cleaned;
    }

    public static String clipToQuestionLabel(String stem, String questionLabel) {
        return clipToQuestionLabel(stem, questionLabel, true);
    }

    private static String clipToQuestionLabel(String stem, String questionLabel, boolean clipMergedOpenings) {
        if (stem == null || stem.isBlank()) {
            return stem;
        }
        String normalizedLabel = normalizeLabel(questionLabel);
        String text = stem.strip();
        int start = 0;
        if (normalizedLabel != null) {
            int labelAt = findLabelStart(text, normalizedLabel);
            if (labelAt >= 0) {
                start = labelAt;
            }
        }
        String clipped = text.substring(start).strip();
        int searchFrom = normalizedLabel != null ? Math.max(normalizedLabel.length(), 2) : 2;
        int end = findNextLabelStart(clipped, normalizedLabel, searchFrom);
        if (end > 0) {
            clipped = clipped.substring(0, end).strip();
        }
        if (clipMergedOpenings && looksMerged(clipped, normalizedLabel)) {
            clipped = clipAtSecondOpening(clipped);
        }
        return clipped.isBlank() ? text : clipped;
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return label.replaceAll("\\s+", "").trim();
    }

    private static int findLabelStart(String text, String label) {
        Pattern[] patterns = {
                Pattern.compile("(?:(?<=\\n)|^)" + Pattern.quote(label) + "(?=\\S)"),
                Pattern.compile("(?:(?<=\\n)|^)" + Pattern.quote(label) + "\\s+"),
                Pattern.compile("(?:(?<=\\n)|^)" + Pattern.quote(label) + "[.、．]\\s*"),
        };
        int best = -1;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                best = matcher.start();
                break;
            }
        }
        return best;
    }

    private static int findNextLabelStart(String text, String ownLabel, int searchFrom) {
        int best = -1;
        for (Pattern pattern : new Pattern[]{CHAPTER_LABEL, CHAPTER_LABEL_INLINE, CHAPTER_LABEL_MID, NUMBER_LABEL, NUMBER_LABEL_INLINE}) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (matcher.start() < searchFrom) {
                    continue;
                }
                String found = normalizeLabel(matcher.group(1));
                if (ownLabel != null && ownLabel.equals(found)) {
                    continue;
                }
                if (best < 0 || matcher.start() < best) {
                    best = matcher.start();
                }
            }
        }
        return best;
    }

    private static boolean looksMerged(String stem, String ownLabel) {
        if (stem == null || stem.isBlank()) {
            return false;
        }
        if (significantOpenings(stem).size() >= 2) {
            return true;
        }
        boolean hasHowMany = stem.contains("为多少") || stem.contains("为多少？");
        boolean hasBlankChoice = stem.contains("应（") || stem.contains("应(");
        return hasHowMany && hasBlankChoice;
    }

    private static final class OpeningMatch {
        final int start;
        final int end;
        final String text;

        OpeningMatch(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private static java.util.List<OpeningMatch> significantOpenings(String stem) {
        Matcher matcher = MERGED_OPENINGS.matcher(stem);
        java.util.List<OpeningMatch> matches = new java.util.ArrayList<>();
        while (matcher.find()) {
            matches.add(new OpeningMatch(matcher.start(), matcher.end(), matcher.group()));
        }
        if (matches.isEmpty()) {
            return matches;
        }
        java.util.List<OpeningMatch> significant = new java.util.ArrayList<>();
        significant.add(matches.get(0));
        for (int i = 1; i < matches.size(); i++) {
            OpeningMatch prev = significant.get(significant.size() - 1);
            OpeningMatch current = matches.get(i);
            String between = stem.substring(prev.end, current.start);
            if ("设计一个".equals(prev.text) && "优先编码器".equals(current.text)) {
                continue;
            }
            if (between.matches(".*[。？?；!！\\n].*") || between.strip().length() >= 12) {
                significant.add(current);
            }
        }
        return significant;
    }

    private static String clipAtSecondOpening(String stem) {
        java.util.List<OpeningMatch> openings = significantOpenings(stem);
        if (openings.size() < 2) {
            return stem;
        }
        String clipped = stem.substring(0, openings.get(1).start).strip();
        return clipped.isBlank() ? stem : clipped;
    }
}
