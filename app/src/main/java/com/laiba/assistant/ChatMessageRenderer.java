package com.laiba.assistant;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ChatMessageRenderer {

    private ChatMessageRenderer() {}

    public static void populate(
            LinearLayout target,
            Context context,
            String markdown,
            int textColor,
            int codeBackground,
            int codeText,
            int accent) {

        target.removeAllViews();

        if (markdown == null || markdown.isEmpty()) return;

        String normalized = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n');

        String[] lines = normalized.split("\n", -1);

        StringBuilder prose = new StringBuilder();
        StringBuilder code = new StringBuilder();

        boolean inCode = false;
        char fence = '`';
        String language = "code";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            boolean backtickFence = trimmed.startsWith("```");
            boolean tildeFence = trimmed.startsWith("~~~");

            if (backtickFence || tildeFence) {
                if (!inCode) {
                    addProse(target, context, prose, textColor);
                    prose.setLength(0);

                    inCode = true;
                    fence = backtickFence ? '`' : '~';
                    code.setLength(0);

                    String info = trimmed.substring(3).trim();
                    if (info.isEmpty()) {
                        language = "code";
                    } else {
                        language = info.split("\\s+")[0];
                    }

                } else if (isClosingFence(trimmed, fence)) {
                    addCode(target, context, code, language);
                    code.setLength(0);
                    inCode = false;
                    language = "code";
                }

                continue;
            }

            if (inCode) {
                code.append(line);
                if (i < lines.length - 1) code.append('\n');
            } else {
                prose.append(line);
                if (i < lines.length - 1) prose.append('\n');
            }
        }

        if (inCode) {
            addCode(target, context, code, language);
        } else {
            addProse(target, context, prose, textColor);
        }
    }

    private static boolean isClosingFence(
            String line, char marker) {
        if (line.length() < 3) return false;

        for (int i = 0; i < 3; i++) {
            if (line.charAt(i) != marker) return false;
        }

        return true;
    }

    private static void addProse(
            LinearLayout target,
            Context context,
            StringBuilder content,
            int textColor) {

        String value = content.toString().trim();
        if (value.isEmpty()) return;

        TextView text = new TextView(context);
        text.setText(MarkdownRenderer.render(value));
        text.setTextColor(textColor);
        text.setTextSize(14);
        text.setGravity(Gravity.LEFT | Gravity.TOP);
        text.setTextIsSelectable(true);
        text.setLineSpacing(dp(context, 3), 1f);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 5);

        target.addView(text, params);
    }

    private static void addCode(
            LinearLayout target,
            Context context,
            StringBuilder content,
            String language) {

        String code = content.toString();

        if (code.endsWith("\n")) {
            code = code.substring(0, code.length() - 1);
        }

        CodeDisplayView view =
            new CodeDisplayView(context, language, code);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, 5);
        params.bottomMargin = dp(context, 9);

        target.addView(view, params);
    }

    private static int dp(Context context, float value) {
        return (int) (value *
            context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
