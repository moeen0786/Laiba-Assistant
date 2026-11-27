package com.laiba.assistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeDisplayView extends LinearLayout {

    private static final int BG = 0xff0c1428;
    private static final int HEADER = 0xff15223e;
    private static final int BORDER = 0xff30476d;
    private static final int TEXT = 0xffdceaff;
    private static final int CYAN = 0xff55dfff;
    private static final int PURPLE = 0xffc7a5ff;
    private static final int GREEN = 0xff9be7b0;
    private static final int ORANGE = 0xffffc27a;
    private static final int COMMENT = 0xff8295b4;

    private static final Set<String> KEYWORDS = new HashSet<>(
        Arrays.asList((
            "abstract assert boolean break byte case catch char class const " +
            "continue default do double else enum extends final finally float " +
            "for if implements import instanceof int interface long new package " +
            "private protected public return short static super switch this " +
            "throw throws try void while var let function async await export " +
            "from type def lambda pass raise with self None True False " +
            "null undefined true false fun val object select insert update " +
            "delete create alter drop table where join on group by order " +
            "having limit offset distinct values primary foreign key"
        ).split("\\s+"))
    );

    private static final Pattern TOKENS = Pattern.compile(
        "(?s)(//[^\\n]*|#[^\\n]*|/\\*.*?\\*/|<!--.*?-->|" +
        "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|" +
        "`(?:\\\\.|[^`\\\\])*`|\\b[A-Za-z_$][A-Za-z0-9_$]*\\b|" +
        "\\b\\d+(?:\\.\\d+)?\\b)"
    );

    private final String source;
    private final String language;

    public CodeDisplayView(Context context, String language, String source) {
        super(context);
        this.source = source == null ? "" : source;
        this.language = normalize(language);

        setOrientation(VERTICAL);
        setGravity(Gravity.LEFT);
        setPadding(dp(9), dp(8), dp(9), dp(9));
        setBackground(drawable(BG, BORDER, dp(13)));

        buildHeader();
        buildCode();
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(5), dp(6), dp(5));
        header.setBackground(drawable(HEADER, BORDER, dp(9)));

        TextView label = new TextView(getContext());
        label.setText(language.toUpperCase(Locale.US));
        label.setTextColor(CYAN);
        label.setTextSize(11);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        header.addView(label, new LayoutParams(0, dp(34), 1f));

        TextView copy = new TextView(getContext());
        copy.setText("COPY  ⧉");
        copy.setTextColor(Color.WHITE);
        copy.setTextSize(11);
        copy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(12), 0, dp(12), 0);
        copy.setBackground(drawable(0xff20518d, 0xff397bc1, dp(8)));
        copy.setContentDescription("Copy code");
        copy.setOnClickListener(v -> copyCode());

        header.addView(copy, new LayoutParams(-2, dp(32)));
        addView(header, new LayoutParams(-1, -2));
    }

    private void buildCode() {
        TextView code = new TextView(getContext());
        code.setText(highlight(source));
        code.setTextSize(12);
        code.setTextColor(TEXT);
        code.setTypeface(Typeface.MONOSPACE);
        code.setGravity(Gravity.TOP | Gravity.LEFT);
        code.setTextIsSelectable(true);
        code.setHorizontallyScrolling(true);
        code.setIncludeFontPadding(false);
        code.setLineSpacing(dp(3), 1f);
        code.setPadding(dp(8), dp(11), dp(12), dp(11));

        HorizontalScrollView scroll =
            new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(code,
            new HorizontalScrollView.LayoutParams(-2, -2));

        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(7);
        addView(scroll, params);
    }

    private void copyCode() {
        ClipboardManager clipboard = (ClipboardManager)
            getContext().getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard == null) {
            Toast.makeText(getContext(),
                "Clipboard unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Laiba code", source));

        Toast.makeText(getContext(),
            "Code copied successfully", Toast.LENGTH_SHORT).show();
    }

    private CharSequence highlight(String value) {
        SpannableStringBuilder result =
            new SpannableStringBuilder(value);

        Matcher matcher = TOKENS.matcher(value);

        while (matcher.find()) {
            String token = matcher.group();
            int color = 0;
            boolean bold = false;

            if (token.startsWith("//") || token.startsWith("#")
                    || token.startsWith("/*")
                    || token.startsWith("<!--")) {
                color = COMMENT;
            } else if (token.startsWith("\"")
                    || token.startsWith("'")
                    || token.startsWith("`")) {
                color = GREEN;
            } else if (Character.isDigit(token.charAt(0))) {
                color = ORANGE;
            } else if (KEYWORDS.contains(token)) {
                color = PURPLE;
                bold = true;
            } else {
                int next = matcher.end();
                while (next < value.length()
                        && Character.isWhitespace(value.charAt(next))) {
                    next++;
                }
                if (next < value.length() && value.charAt(next) == '(') {
                    color = CYAN;
                }
            }

            if (color != 0) {
                result.setSpan(new ForegroundColorSpan(color),
                    matcher.start(), matcher.end(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (bold) {
                result.setSpan(new StyleSpan(Typeface.BOLD),
                    matcher.start(), matcher.end(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Matcher jsonKeys = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)"
        ).matcher(value);

        while (jsonKeys.find()) {
            result.setSpan(new ForegroundColorSpan(CYAN),
                jsonKeys.start(), jsonKeys.end(),
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return result;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return "code";
        String name = value.trim().split("\\s+")[0];
        return name.substring(0, Math.min(name.length(), 16))
            .toLowerCase(Locale.US);
    }

    private GradientDrawable drawable(int color, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        shape.setStroke(dp(1), border);
        return shape;
    }

    private int dp(float value) {
        return (int) (value *
            getResources().getDisplayMetrics().density + 0.5f);
    }
}
