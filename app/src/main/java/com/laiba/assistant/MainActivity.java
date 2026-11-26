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
    private int menuState = 0;
    private int setupPage = 0;

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
        root.addView(new SplashHeartsView(),
            new FrameLayout.LayoutParams(-1, -1));
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


    // Animated falling hearts: UI-only splash decoration.
    private class SplashHeartsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start = System.currentTimeMillis();
        private final int[] colors = {
            0xffff5b91, 0xffff83b0, 0xffe85cff, 0xffff4778
        };

        SplashHeartsView() {
            super(MainActivity.this);
            setClickable(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float elapsed = (System.currentTimeMillis() - start) / 1000f;
            float width = getWidth();
            float height = getHeight();

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);

            for (int i = 0; i < 16; i++) {
                float speed = dp(35 + (i % 5) * 12);
                float phase = (i * height / 16f);
                float y = (elapsed * speed + phase)
                    % (height + dp(40)) - dp(20);

                float x = width * ((i * 73 % 100) / 100f);
                x += (float)Math.sin(elapsed * 1.2f + i) * dp(9);

                paint.setColor(colors[i % colors.length]);
                paint.setAlpha(175);
                paint.setTextSize(dp(12 + (i % 4) * 2));
                canvas.drawText("♥", x, y, paint);
            }

            postInvalidateDelayed(32);
        }
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
        setupPage = 0;
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

        google.setOnClickListener(v -> showGoogleAccountPage());
        api.setOnClickListener(v -> showApiKeyPage());

        // Google login and API verification remain inactive.
    }

    private void authHeader(String title) {
        LinearLayout header = row();
        TextView back = text("‹", 34, cyan, true);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> showVerification());

        LinearLayout labels = column();
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(text("LAIBA", 17, Color.WHITE, true));
        labels.addView(text(title, 11, cyan, true));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(orb(19), new LinearLayout.LayoutParams(dp(42), dp(42)));
        page.addView(header);
    }

    private void showGoogleAccountPage() {
        setupPage = 1;
        base();
        authHeader("GOOGLE ACCOUNT");

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, .18f));

        page.addView(orb(38),
            new LinearLayout.LayoutParams(dp(82), dp(82)));
        gap(page, dp(18));
        page.addView(text("Choose your Google Account",
            23, Color.WHITE, true));
        gap(page, dp(7));
        page.addView(text("Select an account to connect with Laiba.",
            13, 0xffaab8df, false));

        gap(page, dp(22));
        LinearLayout card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.setBackground(shape(panel, dp(22), dp(1), 0xff315bce));

        TextView accountIcon = text("G", 28, Color.WHITE, true);
        accountIcon.setBackground(shape(0xff263d70, dp(30), dp(1), 0xff4b9cff));
        card.addView(accountIcon,
            new LinearLayout.LayoutParams(dp(58), dp(58)));
        gap(card, dp(12));
        card.addView(text("Accounts on this device",
            17, Color.WHITE, true));
        gap(card, dp(6));
        card.addView(text(
            "Your phone's Google accounts will appear here\\n" +
            "when secure account selection is connected.",
            12, 0xffaab8df, false));
        gap(card, dp(15));

        TextView choose = pill("Choose an account", blue);
        card.addView(choose, new LinearLayout.LayoutParams(-1, -2));
        gap(card, dp(9));

        TextView add = text("+   Add another Google account",
            14, 0xffdce7ff, true);
        add.setPadding(dp(10), dp(13), dp(10), dp(13));
        add.setBackground(shape(bg, dp(16), dp(1), cyan));
        card.addView(add, new LinearLayout.LayoutParams(-1, -2));

        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        gap(page, dp(12));
        page.addView(text("UI PREVIEW ONLY • Google sign-in is inactive",
            11, 0xff8296c2, false));

        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, .2f));
    }

    private void showApiKeyPage() {
        setupPage = 2;
        base();
        authHeader("GEMINI API SETUP");

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, .2f));

        page.addView(orb(35),
            new LinearLayout.LayoutParams(dp(76), dp(76)));
        gap(page, dp(18));
        page.addView(text("Connect Gemini API",
            23, Color.WHITE, true));
        gap(page, dp(7));
        page.addView(text("Enter your Gemini API key below.",
            13, 0xffaab8df, false));

        gap(page, dp(22));
        LinearLayout card = column();
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.setBackground(shape(panel, dp(22), dp(1), 0xff315bce));

        TextView label = text("Gemini API Key", 14, Color.WHITE, true);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        card.addView(label);
        gap(card, dp(10));

        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(14);
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(0xff8296c2);
        keyInput.setHint("Enter API key");
        keyInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        keyInput.setBackground(shape(bg, dp(14), dp(1), 0xff42659d));
        card.addView(keyInput, new LinearLayout.LayoutParams(-1, -2));
        gap(card, dp(15));

        TextView continueButton = pill("Continue", blue);
        card.addView(continueButton,
            new LinearLayout.LayoutParams(-1, -2));

        gap(card, dp(9));
        card.addView(text("The key is not saved or verified in this preview.",
            11, 0xffaab8df, false));

        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        gap(page, dp(12));
        page.addView(text("UI PREVIEW ONLY • API verification is inactive",
            11, 0xff8296c2, false));

        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, .2f));
    }

    private void showCallScreen() {
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
        page.setPadding(dp(22), dp(20), dp(22), dp(24));
        page.setGravity(Gravity.CENTER);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        View topSpace = new View(this);
        page.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1));

        page.addView(text("Laiba", 25, Color.WHITE, true));
        gap(page, dp(8));
        page.addView(text("Voice", 14, 0xffaab8df, false));
        gap(page, dp(20));

        CallOrbView callOrb = new CallOrbView();
        page.addView(callOrb,
            new LinearLayout.LayoutParams(dp(270), dp(270)));

        View middleSpace = new View(this);
        page.addView(middleSpace, new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);

        TextView mute = text("🎙", 25, Color.WHITE, true);
        mute.setGravity(Gravity.CENTER);
        mute.setBackground(shape(0xff26334f, dp(34), dp(1), 0xff52658e));
        mute.setContentDescription("Mute microphone");
        controls.addView(mute,
            new LinearLayout.LayoutParams(dp(68), dp(68)));

        View controlGap = new View(this);
        controls.addView(controlGap,
            new LinearLayout.LayoutParams(dp(38), 1));

        TextView close = text("×", 38, Color.WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(0xffe53945, dp(36), 0, 0));
        close.setContentDescription("Close call screen");
        controls.addView(close,
            new LinearLayout.LayoutParams(dp(68), dp(68)));

        page.addView(controls);
        gap(page, dp(18));

        View bottomSpace = new View(this);
        page.addView(bottomSpace, new LinearLayout.LayoutParams(1, 0, 0.25f));

        final boolean[] muted = {false};
        mute.setOnClickListener(v -> {
            muted[0] = !muted[0];
            mute.setText(muted[0] ? "🔇" : "🎙");
            mute.setBackground(shape(
                muted[0] ? 0xff48516a : 0xff26334f,
                dp(34), dp(1), muted[0] ? 0xffaab8df : 0xff52658e));
        });

        close.setOnClickListener(v -> showChat());
    }

    private class CallOrbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start = System.currentTimeMillis();

        CallOrbView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float time = (System.currentTimeMillis() - start) / 1000f;
            float pulse = 1f + .075f *
                (float)(.5 + .5 * Math.sin(time * 2.8f));
            float radius = dp(72) * pulse;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(0x6656c8ff);
            canvas.drawCircle(cx, cy, radius * 1.48f, paint);

            paint.setColor(0x887f7bff);
            canvas.save();
            canvas.rotate(time * 32f, cx, cy);
            canvas.drawOval(new RectF(
                cx - radius * 1.65f, cy - radius * .62f,
                cx + radius * 1.65f, cy + radius * .62f), paint);
            canvas.restore();

            paint.setStyle(Paint.Style.FILL);
            paint.setShadowLayer(dp(24), 0, 0, 0xff168bff);
            paint.setShader(new RadialGradient(
                cx - radius * .28f, cy - radius * .34f, radius * 1.55f,
                new int[]{0xffffffff, 0xffb8efff, 0xff32aaff,
                    0xff2863ed, 0xff5638b8},
                null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
            paint.clearShadowLayer();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xffd8f6ff);
            canvas.drawCircle(cx, cy, radius * 1.04f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99ffffff);
            for (int i = 0; i < 8; i++) {
                float a = time * .7f + i * (float)Math.PI / 4f;
                float x = cx + (float)Math.cos(a) * radius * 1.72f;
                float y = cy + (float)Math.sin(a) * radius * 1.72f;
                canvas.drawCircle(x, y, dp(2.2f), paint);
            }

            postInvalidateDelayed(32);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void showChat() {
        menuState = 0;

        // Restore normal status bar after fullscreen splash.
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

        base();

        // Premium navy, royal blue and subtle warm-gold background.
        GradientDrawable premiumBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{
                0xff040714,
                0xff0b1a35,
                0xff171a2b,
                0xff211d25
            }
        );
        root.setBackground(premiumBg);

        // Keep chat content below the Android status bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            page.setPadding(
                dp(22), topInset + dp(18),
                dp(22), dp(18)
            );
            return insets;
        });
        root.requestApplyInsets();

        LinearLayout header = row();
        header.setPadding(4,14,4,4);

        TextView menu = text("☰", 24, cyan, true);
        menu.setBackground(shape(0xff172b50, dp(21), dp(1), 0xff4b9cff));
        menu.setElevation(dp(5));
        menu.setTranslationX(-dp(12));
        menu.setTranslationY(-dp(5));
        header.addView(menu, new LinearLayout.LayoutParams(dp(46),dp(46)));
        menu.setOnClickListener(v -> showMenu());

        header.addView(orb(22), new LinearLayout.LayoutParams(42,42));

        header.addView(text("⋮", 26, Color.WHITE, true),
            new LinearLayout.LayoutParams(35,45));

        page.addView(header);
        gap(page, 6);
        page.addView(text("✦", 46, cyan, true));
        page.addView(text("Good evening", 24, Color.WHITE, true));
        page.addView(text("How can I help you today?", 14, 0xffaab8df, false));

        // Temporary welcome message: UI only, not saved as chat history.
        gap(page, 12);
        LinearLayout welcomeRow = row();
        welcomeRow.setGravity(Gravity.TOP | Gravity.LEFT);

        ImageView laibaIcon = new ImageView(this);
        laibaIcon.setImageDrawable(
            getPackageManager().getApplicationIcon(getApplicationInfo())
        );
        laibaIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        laibaIcon.setPadding(dp(5), dp(5), dp(5), dp(5));
        laibaIcon.setBackground(
            shape(0xff203b68, dp(18), dp(1), 0xff3279c8)
        );
        LinearLayout.LayoutParams iconParams =
            new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(8);
        welcomeRow.addView(laibaIcon, iconParams);

        TextView welcomeMessage = text(
            "السلام علیکم! میں لائبہ ہوں۔\n" +
            "آپ کی ذاتی AI اسسٹنٹ۔ بتائیے، آج میں آپ کی کیا مدد کر سکتی ہوں؟",
            14, 0xffe7efff, false
        );
        welcomeMessage.setTextIsSelectable(true);
        welcomeMessage.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        welcomeMessage.setPadding(dp(13), dp(11), dp(13), dp(11));
        welcomeMessage.setBackground(
            shape(0xff192640, dp(18), dp(1), 0xff30496c)
        );
        welcomeRow.addView(
            welcomeMessage,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        page.addView(welcomeRow,
            new LinearLayout.LayoutParams(-1, -2));
        gap(page, 16);
        View spacer = new View(this); page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
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

        TextView call = text("☎", 18, Color.WHITE, true);
        call.setGravity(Gravity.CENTER);
        call.setBackground(shape(0xff142d68, dp(22), 1, 0xff315bce));
        composer.addView(call, new LinearLayout.LayoutParams(dp(40), dp(40)));
        call.setOnClickListener(v -> showCallScreen());

        TextView send = text("↑", 24, Color.WHITE, true);
        send.setBackground(shape(0xff3185ff, dp(24), 0, 0));
        send.setElevation(dp(3));
        composer.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));

        page.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        gap(page, 8);
    }

    private void addMenuItem(LinearLayout target, String symbol,
                             String title, String description) {
        LinearLayout card = row();
        card.setPadding(dp(15), dp(14), dp(13), dp(14));
        card.setBackground(shape(0xff152344, dp(20), dp(1), 0xff31558c));
        card.setElevation(dp(3));

        TextView icon = text(symbol, 25, cyan, true);
        icon.setBackground(shape(0xff203b68, dp(16), dp(1), 0xff3279c8));
        card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout labels = column();
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);
        labels.addView(text(title, 16, Color.WHITE, true));
        TextView sub = text(description, 11, 0xffaab8df, false);
        sub.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        labels.addView(sub);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        card.addView(text("›", 29, cyan, true),
            new LinearLayout.LayoutParams(dp(25), dp(45)));

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(13);
        target.addView(card, lp);
        card.setOnClickListener(v -> showMenuPreview(title, description, symbol));
    }

    private void showMenu() {
        menuState = 1;
        base();

        LinearLayout header = row();
        TextView back = text("‹", 34, cyan, true);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> showChat());

        LinearLayout heading = column();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("LAIBA AI", 17, Color.WHITE, true));
        heading.addView(text("COMMAND CENTER", 10, cyan, true));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(orb(20), new LinearLayout.LayoutParams(dp(42), dp(42)));
        page.addView(header);

        gap(page, 24);
        LinearLayout hero = column();
        hero.setGravity(Gravity.LEFT);
        hero.setPadding(dp(18), dp(20), dp(18), dp(20));
        hero.setBackground(shape(0xff111d3d, dp(24), dp(1), 0xff294d82));
        hero.addView(text("✦  YOUR SPACE", 12, cyan, true));
        gap(hero, 8);
        TextView title = text("Your Space.", 27, Color.WHITE, true);
        title.setGravity(Gravity.LEFT);
        hero.addView(title);
        TextView sub = text("Manage your Laiba experience.", 13, 0xffaab8df, false);
        sub.setGravity(Gravity.LEFT);
        hero.addView(sub);
        page.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        gap(page, 20);
        TextView section = text("EXPLORE YOUR LAIBA", 11, 0xff8fa9d8, true);
        section.setGravity(Gravity.LEFT);
        page.addView(section);
        gap(page, 12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = column();
        items.setPadding(dp(1), dp(2), dp(1), dp(12));
        addMenuItem(items, "◷", "Chat History", "Past conversations");
        addMenuItem(items, "◉", "Call History", "Voice sessions");
        addMenuItem(items, "✧", "AI Tools", "Your smart toolkit");
        addMenuItem(items, "⚙", "Settings", "Personalize Laiba");
        scroll.addView(items);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        gap(page, 8);
        page.addView(text("LAIBA  •  PERSONAL AI", 10, 0xff7187b8, true));
    }

    private void showMenuPreview(String title, String description, String symbol) {
        menuState = 2;
        base();

        LinearLayout header = row();
        TextView back = text("‹", 34, cyan, true);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> showMenu());
        header.addView(text("COMMAND CENTER", 14, Color.WHITE, true));
        page.addView(header);

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 0.35f));

        TextView icon = text(symbol, 48, cyan, true);
        icon.setBackground(shape(0xff152d57, dp(30), dp(1), 0xff3279c8));
        page.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(104)));

        gap(page, 22);
        page.addView(text(title, 25, Color.WHITE, true));
        gap(page, 7);
        page.addView(text(description, 14, 0xffaab8df, false));
        gap(page, 18);
        page.addView(text("This section is a UI preview.", 12, 0xff7187b8, false));
        gap(page, 25);

        TextView backButton = pill("‹   Back to Command Center", 0xff174b91);
        page.addView(backButton);
        backButton.setOnClickListener(v -> showMenu());

        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 0.45f));
    }

    @Override public void onBackPressed() {
        if (menuState == 2) showMenu();
        else if (menuState == 1) showChat();
        else showChat();
    }
}
