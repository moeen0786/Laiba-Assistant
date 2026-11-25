package com.laiba.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(4, 7, 20);
    private final int panel = Color.rgb(19, 25, 49);
    private final int blue = Color.rgb(24, 113, 255);
    private final int cyan = Color.rgb(0, 205, 255);
    private final int purple = Color.rgb(151, 65, 255);
    private FrameLayout root;
    private LinearLayout page;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        showSplash();
    }

    private GradientDrawable shape(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(stroke, strokeColor);
        return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL); return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void base() {
        root = new FrameLayout(this); root.setBackgroundColor(bg);
        page = column(); page.setPadding(22, 18, 22, 18);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }
    private void gap(LinearLayout l, int h) {
        View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, h));
    }
    private TextView pill(String label, int color) {
        TextView t = text(label, 15, Color.WHITE, true);
        t.setPadding(18, 15, 18, 15); t.setBackground(shape(color, 48, 0, color));
        return t;
    }
    private TextView orb(int size) {
        TextView o = text("✦", size, Color.WHITE, true);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cyan, blue, purple});
        g.setShape(GradientDrawable.OVAL); g.setStroke(3, Color.rgb(105,190,255));
        o.setBackground(g); o.setElevation(18); return o;
    }
    private void showSplash() {
        base(); gap(page, 80); page.addView(orb(76), new LinearLayout.LayoutParams(150,150));
        gap(page, 22); page.addView(text("Laiba", 48, Color.WHITE, true));
        page.addView(text("Your AI Assistant", 19, 0xffc9d3f5, false));
        gap(page, 24); page.addView(text("Chat  •  Call  •  Create  •  Explore", 15, 0xffaab8df, false));
        gap(page, 100); page.addView(text("Powered by Google Gemini", 15, 0xffdbe5ff, true));
        gap(page, 18); ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));
        p.setProgress(72); page.addView(p, new LinearLayout.LayoutParams(180, 5));
        page.addView(text("Initializing Laiba", 12, 0xff8e9bc1, false));
        root.postDelayed(this::showVerification, 1300);
    }
    private void showVerification() {
        base(); gap(page, 28); page.addView(text("G", 48, 0xff4285f4, true));
        page.addView(text("Google Gemini", 27, Color.WHITE, true));
        page.addView(text("Verification Required", 16, 0xffc6d1f1, false));
        gap(page, 18); page.addView(text("To continue using Laiba,\\nverify your Google account", 16, Color.WHITE, false));
        gap(page, 24);
        LinearLayout benefits = column(); benefits.setPadding(18, 18, 18, 18);
        benefits.setBackground(shape(panel, 24, 1, 0xff29365f));
        for (String s : new String[]{"✓   Secure & Private", "✓   Get Latest AI Features", "✓   Continue with Google"}) {
            TextView t = text(s, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER_VERTICAL);
            benefits.addView(t, new LinearLayout.LayoutParams(-1, 42));
        }
        page.addView(benefits, new LinearLayout.LayoutParams(-1, -2));
        gap(page, 22); page.addView(pill("G    Sign in with Google", blue), new LinearLayout.LayoutParams(-1,-2));
        gap(page, 14); page.addView(text("──────────────  or  ──────────────", 13, 0xff8795bd, false));
        gap(page, 14);
        TextView api = text("Use API Key", 15, Color.WHITE, true);
        api.setPadding(12,14,12,14); api.setBackground(shape(bg, 40, 2, cyan));
        page.addView(api, new LinearLayout.LayoutParams(-1,-2));
        gap(page, 26); page.addView(text("◆  Your data is safe with Laiba", 12, 0xffaab8df, false));
        gap(page, 35); TextView preview = text("Preview UI  •  No functions connected yet", 11, 0xff6e7ca7, false);
        page.addView(preview);
        // UI-only prototype: verification controls are intentionally non-functional.
    }
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void showChat() {
        base();
        LinearLayout header = row(); header.setPadding(4,8,4,16);
        TextView menu = text("☰", 25, Color.WHITE, true); header.addView(menu, new LinearLayout.LayoutParams(48,52));
        header.addView(orb(22), new LinearLayout.LayoutParams(42,42));
        LinearLayout name = column(); name.setGravity(Gravity.CENTER_VERTICAL);
        name.addView(text("Laiba", 19, Color.WHITE, true));
        name.addView(text("● Online", 11, 0xff43d69a, false));
        header.addView(name, new LinearLayout.LayoutParams(0,-2,1));
        header.addView(text("⋮", 26, Color.WHITE, true), new LinearLayout.LayoutParams(35,45));
        page.addView(header);
        View line = new View(this); line.setBackgroundColor(0xff202947);
        page.addView(line, new LinearLayout.LayoutParams(-1,1));
        gap(page, 25);
        page.addView(text("✦", 52, cyan, true));
        page.addView(text("Good evening", 26, Color.WHITE, true));
        page.addView(text("How can I help you today?", 16, 0xffaab8df, false));
        gap(page, 32);
        View spacer = new View(this); page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        TextView call = text("☎     Start a voice conversation", 15, Color.WHITE, true);
        call.setPadding(14,16,14,16); call.setBackground(shape(0xff142d68,24,1,0xff315bce));
        page.addView(call,new LinearLayout.LayoutParams(-1,-2));
        gap(page,12);
        LinearLayout composer = row();
        composer.setPadding(dp(10), dp(6), dp(8), dp(6));
        composer.setBackground(shape(0xff171e38, dp(28), 1, 0xff354568));

        TextView plus = text("＋", 25, 0xffc5d4f5, false);
        plus.setBackground(shape(0xff222d4b, dp(20), 1, 0xff354568));
        composer.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));

        EditText input = new EditText(this);
        input.setHint("Message Laiba…");
        input.setHintTextColor(0xff929fbe);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setMinLines(1);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        input.setPadding(dp(12), dp(8), dp(8), dp(8));
        input.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        TextView mic = text("🎙", 19, Color.WHITE, false);
        composer.addView(mic, new LinearLayout.LayoutParams(dp(40), dp(44)));

        TextView send = text("↑", 24, Color.WHITE, true);
        send.setBackground(shape(0xff3185ff, dp(24), 0, 0));
        send.setElevation(dp(3));
        composer.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));

        page.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        gap(page,10);
        LinearLayout nav = row(); nav.setBackground(shape(0xff0b1024,22,1,0xff222d4d)); nav.setPadding(4,8,4,8);
        String[] tabs={"▣\\nChat","☎\\nCall","▦\\nTools","⚙\\nSettings"};
        for(String s:tabs){ TextView t=text(s,13,0xffcbd8fb,true); nav.addView(t,new LinearLayout.LayoutParams(0,55,1)); }
        page.addView(nav);
    }
    @Override public void onBackPressed() { showChat(); }
}
