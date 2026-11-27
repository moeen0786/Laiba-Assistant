package com.laiba.assistant;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

public final class MarkdownRenderer {

    private static final int CYAN = 0xff55dfff;
    private static final int PURPLE = 0xffbd91ff;
    private static final int TEXT = 0xffe7efff;
    private static final int CODE_BG = 0xff101a31;
    private static final int CODE_TEXT = 0xffa9e7ff;
    private static final int QUOTE = 0xff8ebdff;

    private MarkdownRenderer() {}

    public static CharSequence render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        String normalized = markdown
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] lines = normalized.split("\n", -1);
        SpannableStringBuilder out = new SpannableStringBuilder();

        boolean inCode = false;
        int codeStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().startsWith("```")) {
                if (!inCode) {
                    inCode = true;
                    codeStart = out.length();
                } else {
                    applyCodeBlock(out, codeStart, out.length());
                    inCode = false;
                    codeStart = -1;
                    if (i < lines.length - 1) out.append('\n');
                }
                continue;
            }

            if (inCode) {
                out.append(line);
                if (i < lines.length - 1) out.append('\n');
                continue;
            }

            int start = out.length();
            String trimmed = line.trim();

            int headingLevel = headingLevel(line);
            if (headingLevel > 0) {
                String content = line.trim().substring(headingLevel).trim();
                int headingStart = out.length();
                appendInline(out, content);
                set(out, new StyleSpan(Typeface.BOLD),
                        headingStart, out.length());
                set(out, new RelativeSizeSpan(
                        headingLevel == 1 ? 1.35f :
                        headingLevel == 2 ? 1.22f :
                        headingLevel == 3 ? 1.12f : 1.04f),
                        headingStart, out.length());
                set(out, new ForegroundColorSpan(
                        headingLevel <= 2 ? CYAN : PURPLE),
                        headingStart, out.length());

            } else if (isHorizontalRule(trimmed)) {
                out.append("━━━━━━━━━━━━━━━━━━━━");
                set(out, new ForegroundColorSpan(0xff526b9b),
                        start, out.length());

            } else if (trimmed.startsWith(">")) {
                String quote = trimmed.substring(1);
                if (quote.startsWith(" ")) quote = quote.substring(1);
                appendInline(out, quote);
                set(out, new ForegroundColorSpan(QUOTE), start, out.length());
                set(out, new LeadingMarginSpan.Standard(22, 22),
                        start, out.length());

            } else {
                int markerEnd = listMarkerEnd(line);
                if (markerEnd >= 0) {
                    String prefix = line.substring(0, markerEnd).trim();
                    String marker = prefix.matches("\\d+[.)]")
                            ? prefix.replace(')', '.') + "  "
                            : "•  ";

                    out.append(marker);
                    appendInline(out, line.substring(markerEnd));
                    set(out, new LeadingMarginSpan.Standard(20, 20),
                            start, out.length());

                } else if (trimmed.startsWith("|")
                        && trimmed.endsWith("|")) {
                    out.append(line);
                    set(out, new TypefaceSpan("monospace"),
                            start, out.length());
                    set(out, new ForegroundColorSpan(CODE_TEXT),
                            start, out.length());

                } else {
                    appendInline(out, line);
                }
            }

            if (i < lines.length - 1) out.append('\n');
        }

        if (inCode) applyCodeBlock(out, codeStart, out.length());

        return out;
    }

    private static int headingLevel(String line) {
        int spaces = 0;
        while (spaces < line.length()
                && spaces < 3
                && line.charAt(spaces) == ' ') spaces++;

        int count = 0;
        while (spaces + count < line.length()
                && line.charAt(spaces + count) == '#') count++;

        if (count < 1 || count > 6) return 0;
        int next = spaces + count;
        if (next >= line.length()
                || !Character.isWhitespace(line.charAt(next))) return 0;
        return count;
    }

    private static int listMarkerEnd(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;

        if (i >= line.length()) return -1;

        char first = line.charAt(i);
        if ((first == '-' || first == '*' || first == '+')
                && i + 1 < line.length()
                && Character.isWhitespace(line.charAt(i + 1))) {
            return i + 2;
        }

        int digits = i;
        while (i < line.length() && Character.isDigit(line.charAt(i))) i++;

        if (i > digits && i + 1 < line.length()
                && (line.charAt(i) == '.' || line.charAt(i) == ')')
                && Character.isWhitespace(line.charAt(i + 1))) {
            return i + 2;
        }

        return -1;
    }

    private static boolean isHorizontalRule(String line) {
        if (line.length() < 3) return false;

        char marker = line.charAt(0);
        if (marker != '-' && marker != '*' && marker != '_') return false;

        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == marker) count++;
            else if (!Character.isWhitespace(c)) return false;
        }
        return count >= 3;
    }

    private static void appendInline(
            SpannableStringBuilder out, String value) {

        int i = 0;

        while (i < value.length()) {

            if (value.charAt(i) == '[') {
                int closeLabel = value.indexOf("](", i + 1);
                int closeUrl = closeLabel < 0 ? -1
                        : value.indexOf(')', closeLabel + 2);

                if (closeLabel > i && closeUrl > closeLabel) {
                    String label = value.substring(i + 1, closeLabel);
                    String url = value.substring(closeLabel + 2, closeUrl);
                    int start = out.length();
                    out.append(label);

                    set(out, new ForegroundColorSpan(CYAN),
                            start, out.length());
                    set(out, new UnderlineSpan(),
                            start, out.length());

                    i = closeUrl + 1;
                    continue;
                }
            }

            String marker = null;
            int type = 0;

            if (value.startsWith("**", i)
                    || value.startsWith("__", i)) {
                marker = value.substring(i, i + 2);
                type = 1;
            } else if (value.startsWith("~~", i)) {
                marker = "~~";
                type = 2;
            } else if (value.charAt(i) == '`') {
                marker = "`";
                type = 3;
            } else if (value.charAt(i) == '*'
                    || value.charAt(i) == '_') {
                marker = value.substring(i, i + 1);
                type = 4;
            }

            if (marker != null) {
                int close = value.indexOf(marker, i + marker.length());

                if (close > i + marker.length()) {
                    String inner = value.substring(i + marker.length(), close);
                    int start = out.length();
                    out.append(inner);
                    int end = out.length();

                    if (type == 1) {
                        set(out, new StyleSpan(Typeface.BOLD), start, end);
                    } else if (type == 2) {
                        set(out, new StrikethroughSpan(), start, end);
                    } else if (type == 3) {
                        set(out, new TypefaceSpan("monospace"), start, end);
                        set(out, new BackgroundColorSpan(CODE_BG), start, end);
                        set(out, new ForegroundColorSpan(CODE_TEXT), start, end);
                    } else {
                        set(out, new StyleSpan(Typeface.ITALIC), start, end);
                    }

                    i = close + marker.length();
                    continue;
                }
            }

            out.append(value.charAt(i));
            i++;
        }
    }

    private static void applyCodeBlock(
            SpannableStringBuilder out, int start, int end) {
        if (start < 0 || end <= start) return;

        set(out, new TypefaceSpan("monospace"), start, end);
        set(out, new BackgroundColorSpan(CODE_BG), start, end);
        set(out, new ForegroundColorSpan(CODE_TEXT), start, end);
    }

    private static void set(
            SpannableStringBuilder text,
            Object span, int start, int end) {
        if (start >= 0 && end > start) {
            text.setSpan(span, start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
