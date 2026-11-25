package com.laiba.assistant;

import android.app.Activity;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.animation.LinearInterpolator;

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
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        page = column();
        page.setPadding(dp(20), dp(12), dp(20), dp(12));
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        View topSpace = new View(this);
        page.addView(topSpace,
            new LinearLayout.LayoutParams(1, 0, 0.45f));

        SplashOrbView orb = new SplashOrbView();
        page.addView(orb,
            new LinearLayout.LayoutParams(dp(250), dp(250)));

        gap(page, 10);
        page.addView(text("Laiba", 48, Color.WHITE, true));
        page.addView(text("Your AI Assistant", 19,
            0xffc9d3f5, false));

        gap(page, 12);

        SplashWaveView wave = new SplashWaveView();
        page.addView(wave,
            new LinearLayout.LayoutParams(-1, dp(48)));

        gap(page, 18);

        LinearLayout card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shape(panel, dp(25), dp(1), 0xff315bce));

        TextView loading = text("Preparing your AI experience",
            16, Color.WHITE, true);
        card.addView(loading);

        gap(card, 12);

        LinearLayout progressRow = row();

        ProgressBar progress = new ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setProgressTintList(
            android.content.res.ColorStateList.valueOf(cyan));
        progress.setProgressBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xff172653));

        progressRow.addView(progress,
            new LinearLayout.LayoutParams(0, dp(7), 1));

        TextView percent = text("0%", 16, Color.WHITE, true);
        LinearLayout.LayoutParams percentParams =
            new LinearLayout.LayoutParams(dp(48), dp(30));
        percentParams.leftMargin = dp(10);
        progressRow.addView(percent, percentParams);

        card.addView(progressRow);
        page.addView(card,
            new LinearLayout.LayoutParams(-1, -2));

        gap(page, 20);

        page.addView(text("Powered by Google Gemini",
            15, 0xffdbe5ff, true));

        View bottomSpace = new View(this);
        page.addView(bottomSpace,
            new LinearLayout.LayoutParams(1, 0, 0.45f));

        ValueAnimator loadingAnimator =
            ValueAnimator.ofInt(0, 100);

        loadingAnimator.setDuration(4200);
        loadingAnimator.setInterpolator(new LinearInterpolator());

        loadingAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            progress.setProgress(value);
            percent.setText(value + "%");
        });

        loadingAnimator.addListener(
            new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(
                    android.animation.Animator animation) {
                    showVerification();
                }
            });

        root.post(loadingAnimator::start);
    }

    private class SplashOrbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start = System.currentTimeMillis();

        SplashOrbView() {
            super(MainActivity.this);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(72);
            float t = (System.currentTimeMillis() - start) / 1000f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));

            for (int i = 0; i < 3; i++) {
                paint.setColor(i == 1 ? 0xff00cdff : 0xff674dff);
                canvas.save();
                canvas.rotate(t * (i % 2 == 0 ? 25 : -35) + i * 55,
                    cx, cy);
                canvas.drawOval(new RectF(
                    cx - r * (1.45f + i * .12f),
                    cy - r * (.42f + i * .08f),
                    cx + r * (1.45f + i * .12f),
                    cy + r * (.42f + i * .08f)
                ), paint);
                canvas.restore();
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                cx - r * .25f, cy - r * .3f,
                r * 1.5f,
                new int[]{0xffb8ffff, 0xff00cfff,
                    0xff315bff, 0xff873bff},
                null, Shader.TileMode.CLAMP));

            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(75));
            canvas.drawText("✦", cx, cy + dp(25), paint);

            paint.setColor(0xff00dfff);
            for (int i = 0; i < 12; i++) {
                float a = t + i * .523f;
                float x = cx + (float)Math.cos(a) * r * 1.65f;
                float y = cy + (float)Math.sin(a) * r * .9f;
                canvas.drawCircle(x, y, dp(2), paint);
            }

            postInvalidateDelayed(16);
        }
    }

    private class SplashWaveView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start = System.currentTimeMillis();

        SplashWaveView() {
            super(MainActivity.this);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float t = (System.currentTimeMillis() - start) / 180f;
            float center = getWidth() / 2f;
            float base = getHeight() / 2f;

            for (int i = 0; i < 25; i++) {
                float x = center + (i - 12) * dp(5);
                float h = dp(7) + dp(25) *
                    Math.abs((float)Math.sin(t + i * .55f));

                paint.setColor(i < 12 ?
                    0xff00dfff : 0xffa15bff);

                canvas.drawRoundRect(
                    x, base - h / 2,
                    x + dp(2.5f), base + h / 2,
                    dp(3), dp(3), paint);
            }

            postInvalidateDelayed(35);
        }
    }


    private void showVerification() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        page = column();
        page.setPadding(dp(22), dp(10), dp(22), dp(10));
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        View topSpace = new View(this);
        page.addView(topSpace,
            new LinearLayout.LayoutParams(1, 0, 0.3f));

        SplashOrbView orb = new SplashOrbView();
        page.addView(orb,
            new LinearLayout.LayoutParams(dp(180), dp(180)));

        gap(page, 8);
        page.addView(text("Laiba", 38, Color.WHITE, true));
        page.addView(text("Google Gemini", 19, 0xffb8d5ff, true));
        page.addView(text("Verification Required", 15, 0xffaab8df, false));

        gap(page, 12);

        SplashWaveView wave = new SplashWaveView();
        page.addView(wave,
            new LinearLayout.LayoutParams(-1, dp(38)));

        gap(page, 12);

        LinearLayout card = column();
        card.setPadding(dp(17), dp(14), dp(17), dp(14));
        card.setBackground(shape(panel, dp(25), dp(1), 0xff315bce));

        card.addView(text("Welcome to Laiba", 21, Color.WHITE, true));
        gap(card, 6);
        card.addView(text(
            "Continue by choosing your preferred sign-in option",
            14, 0xffb8c7e9, false));

        gap(card, 12);

        for (String s : new String[]{
            "✦   Secure & Private",
            "✦   Your Personal AI Assistant",
            "✦   Powered by Gemini"
        }) {
            TextView item = text(s, 14, 0xffdce7ff, true);
            item.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(item,
                new LinearLayout.LayoutParams(-1, dp(30)));
        }

        gap(card, 14);

        TextView google = text(
            "G     Sign in with Google", 16, Color.WHITE, true);
        google.setPadding(dp(12), dp(15), dp(12), dp(15));
        google.setBackground(shape(blue, dp(18), 1, 0xff67aaff));
        card.addView(google,
            new LinearLayout.LayoutParams(-1, -2));

        gap(card, 10);

        TextView api = text(
            "Use API Key", 15, 0xffe5f2ff, true);
        api.setPadding(dp(12), dp(14), dp(12), dp(14));
        api.setBackground(shape(bg, dp(18), 1, cyan));
        card.addView(api,
            new LinearLayout.LayoutParams(-1, -2));

        page.addView(card,
            new LinearLayout.LayoutParams(-1, -2));

        gap(page, 12);
        page.addView(text(
            "Your AI experience starts here",
            13, 0xff9cadd5, false));

        View bottomSpace = new View(this);
        page.addView(bottomSpace,
            new LinearLayout.LayoutParams(1, 0, 0.3f));

        // UI-only preview. No login, API, or button actions are connected.
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
