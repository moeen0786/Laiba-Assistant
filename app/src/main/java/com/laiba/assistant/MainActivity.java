package com.laiba.assistant;

import android.app.Activity;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;





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
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private boolean chatSending = false;
    private View chatLoadingIndicator;
    private LaibaChatEngine.Request activeChatRequest;
    private TextView chatSendButton;
    private LinearLayout streamingBubble;
    private String streamingReplyText = "";
    private InternetConnectionManager internetConnectionManager;
    private TextView networkStatusBanner;
    private EditText chatInput;



    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        showSplash();
    }

    @Override protected void onStart() {
        super.onStart();

        if (internetConnectionManager == null) {
            internetConnectionManager =
                new InternetConnectionManager(this);
        }

        internetConnectionManager.start(this::updateInternetStatus);
    }

    @Override protected void onStop() {
        if (internetConnectionManager != null) {
            internetConnectionManager.stop();
        }
        super.onStop();
    }

    private boolean hasInternetConnection() {
        return InternetConnectionManager.hasValidatedInternet(this);
    }

    private void updateInternetStatus(boolean available) {
        if (networkStatusBanner == null ||
                networkStatusBanner.getParent() == null) {
            return;
        }

        networkStatusBanner.setVisibility(
            available ? View.GONE : View.VISIBLE
        );
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

        // Keep screens clear of the Android status and navigation bars.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                int bars = android.view.WindowInsets.Type.statusBars()
                    | android.view.WindowInsets.Type.navigationBars()
                    | android.view.WindowInsets.Type.displayCutout();
                android.graphics.Insets safe = insets.getInsets(bars);
                topInset = safe.top;
                bottomInset = safe.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            page.setPadding(
                dp(22), topInset + dp(18),
                dp(22), bottomInset + dp(18)
            );
            return insets;
        });
        root.requestApplyInsets();
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

    private TextView createBackButton() {
        TextView back = text("‹", 34, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setContentDescription("Back");

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{cyan, blue, purple}
        );
        gradient.setShape(GradientDrawable.OVAL);
        gradient.setStroke(dp(1.5f), 0xffc5f4ff);

        back.setBackground(gradient);
        back.setElevation(dp(8));
        return back;
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

        page.addView(text("Powered by Gemini",
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
                    startStoredKeyCheck();
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
        private final float radiusDp;

        SplashOrbView() {
            this(72f);
        }

        SplashOrbView(float radiusDp) {
            super(MainActivity.this);
            this.radiusDp = radiusDp;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(radiusDp);
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
        page.addView(text("Gemini API", 19, 0xffb8d5ff, true));
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
            "Continue with your API key to set up Laiba",
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

        api.setOnClickListener(v -> showApiKeyPage());

    }

    private void authHeader(String title) {
        LinearLayout header = row();
        TextView back = createBackButton();
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


    private static final String SAFE_KEY_ALIAS = "LaibaGeminiSafeKey";

    private SecretKey getSafeEncryptionKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (ks.containsAlias(SAFE_KEY_ALIAS)) {
            return (SecretKey) ks.getKey(SAFE_KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        generator.init(new KeyGenParameterSpec.Builder(
            SAFE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT |
            KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());

        return generator.generateKey();
    }


    private void saveApiKeySecurely(String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Invalid API key");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getSafeEncryptionKey());

        byte[] encrypted = cipher.doFinal(
            apiKey.trim().getBytes(
                java.nio.charset.StandardCharsets.UTF_8));

        File safeFolder = new File(getFilesDir(), "safe");
        if (!safeFolder.exists() && !safeFolder.mkdirs()) {
            throw new Exception("Secure storage unavailable");
        }

        File keyFile = new File(safeFolder, "gemini.key");
        android.util.AtomicFile atomicFile =
            new android.util.AtomicFile(keyFile);

        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(Base64.encode(cipher.getIV(), Base64.NO_WRAP));
            out.write('\n');
            out.write(Base64.encode(encrypted, Base64.NO_WRAP));
            atomicFile.finishWrite(out);
            out = null;
        } catch (Exception error) {
            if (out != null) atomicFile.failWrite(out);
            throw error;
        }
    }

    private String loadApiKeySecurely() throws Exception {
        File keyFile = new File(
            new File(getFilesDir(), "safe"), "gemini.key");

        if (!keyFile.isFile()) return null;

        android.util.AtomicFile atomicFile =
            new android.util.AtomicFile(keyFile);

        try (java.io.BufferedReader reader =
                new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        atomicFile.openRead(),
                        java.nio.charset.StandardCharsets.UTF_8))) {

            String ivLine = reader.readLine();
            String encryptedLine = reader.readLine();

            if (ivLine == null || encryptedLine == null ||
                    reader.readLine() != null) {
                throw new Exception("Secure key file is corrupted");
            }

            byte[] iv = Base64.decode(ivLine, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(
                encryptedLine, Base64.NO_WRAP);

            if (iv.length != 12 || encrypted.length < 16) {
                throw new Exception("Invalid encrypted key data");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getSafeEncryptionKey(),
                new GCMParameterSpec(128, iv));

            String key = new String(
                cipher.doFinal(encrypted),
                java.nio.charset.StandardCharsets.UTF_8);

            if (key.trim().isEmpty()) {
                throw new Exception("Stored API key is empty");
            }

            return key;
        }
    }

    private void startStoredKeyCheck() {
        new Thread(() -> {
            try {
                String key = loadApiKeySecurely();
                if (key == null || key.trim().isEmpty()) {
                    runOnUiThread(this::showVerification);
                    return;
                }

                if (!hasInternetConnection()) {
                    runOnUiThread(this::showChat);
                    return;
                }

                if (!verifyGoogleApiKey(key)) {
                    runOnUiThread(this::showVerification);
                    return;
                }

                runOnUiThread(this::showChat);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (hasInternetConnection()) {
                        showVerification();
                    } else {
                        showChat();
                    }
                });
            }
        }).start();
    }

    private boolean verifyGoogleApiKey(String apiKey) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(
                "https://generativelanguage.googleapis.com/v1beta/models");

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("x-goog-api-key", apiKey);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);

            return connection.getResponseCode() == 200;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showApiKeyPage() {
        setupPage = 2;
        base();
        authHeader("GEMINI API SETUP");

        gap(page, dp(30));
        page.addView(orb(35),
            new LinearLayout.LayoutParams(dp(76), dp(76)));
        gap(page, dp(18));

        page.addView(text("Connect Gemini API",
            23, Color.WHITE, true));
        gap(page, dp(7));

        page.addView(text("Enter your Google Gemini API key.",
            13, 0xffaab8df, false));

        gap(page, dp(22));

        LinearLayout card = column();
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.setBackground(shape(panel, dp(22), dp(1), 0xff315bce));

        TextView label = text("Gemini API Key",
            14, Color.WHITE, true);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        card.addView(label);
        gap(card, dp(10));

        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(14);
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(0xff8296c2);
        keyInput.setHint("Enter API key");
        keyInput.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        keyInput.setBackground(shape(bg, dp(14), dp(1), 0xff42659d));
        card.addView(keyInput,
            new LinearLayout.LayoutParams(-1, -2));

        gap(card, dp(15));

        TextView continueButton = pill("Verify & Continue", blue);
        card.addView(continueButton,
            new LinearLayout.LayoutParams(-1, -2));

        TextView status = text("", 12, 0xffaab8df, false);
        card.addView(status);
        page.addView(card,
            new LinearLayout.LayoutParams(-1, -2));

        gap(page, dp(12));
        page.addView(text("Encrypted Android private storage",
            11, 0xff8296c2, false));

        continueButton.setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();

            if (key.isEmpty() || key.length() < 20) {
                status.setText("Please enter a valid API key.");
                return;
            }

            if (!hasInternetConnection()) {
                status.setText(
                    "No internet connection. Connect and try again."
                );
                return;
            }

            continueButton.setEnabled(false);
            status.setText("Verifying with Google...");

            new Thread(() -> {
                try {
                    if (!verifyGoogleApiKey(key)) {
                        runOnUiThread(() -> {
                            continueButton.setEnabled(true);
                            status.setText(
                                "Google rejected this API key.");
                        });
                        return;
                    }

                    saveApiKeySecurely(key);

                    runOnUiThread(() -> {
                        keyInput.setText("");
                        showChat();
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        continueButton.setEnabled(true);
                        status.setText(
                            "Verification or secure storage failed.");
                    });
                }
            }).start();
        });
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
        setupPage = -1;

        // Edge-to-Edge: keep system bars visible and handle their insets.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);

            android.view.WindowInsetsController controller =
                getWindow().getInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

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

        // Apply visible system-bar, display-cutout and keyboard insets.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int bars = android.view.WindowInsets.Type.statusBars()
                    | android.view.WindowInsets.Type.navigationBars()
                    | android.view.WindowInsets.Type.displayCutout();

                android.graphics.Insets safe = insets.getInsets(bars);
                int imeBottom = insets.getInsets(
                    android.view.WindowInsets.Type.ime()
                ).bottom;

                page.setPadding(
                    dp(22), safe.top + dp(12),
                    dp(22), Math.max(safe.bottom, imeBottom) + dp(12)
                );
                return insets;
            });
        } else {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                page.setPadding(
                    dp(22), insets.getSystemWindowInsetTop() + dp(12),
                    dp(22), insets.getSystemWindowInsetBottom() + dp(12)
                );
                return insets;
            });
        }
        root.requestApplyInsets();

        LinearLayout header = row();
        header.setPadding(dp(4), dp(8), dp(4), dp(4));

        TextView menu = text("☰", 24, cyan, true);
        menu.setBackground(shape(0xff172b50, dp(21), dp(1), 0xff4b9cff));
        menu.setElevation(dp(5));
        header.addView(menu, new LinearLayout.LayoutParams(dp(46),dp(46)));
        menu.setOnClickListener(v -> showMenu());

        header.addView(orb(22), new LinearLayout.LayoutParams(42,42));

        header.addView(text("⋮", 26, Color.WHITE, true),
            new LinearLayout.LayoutParams(35,45));

        page.addView(header);

        networkStatusBanner = text(
            "No internet connection. Check Wi-Fi or mobile data.",
            12, 0xffffc2ce, true
        );
        networkStatusBanner.setPadding(
            dp(12), dp(10), dp(12), dp(10)
        );
        networkStatusBanner.setBackground(
            shape(0xff39202d, dp(13), dp(1), 0xffa34d69)
        );

        page.addView(
            networkStatusBanner,
            new LinearLayout.LayoutParams(-1, -2)
        );

        updateInternetStatus(hasInternetConnection());

        gap(page, 6);
        page.addView(text("✦", 46, cyan, true));
        page.addView(text("Good evening", 24, Color.WHITE, true));
        page.addView(text("How can I help you today?", 14, 0xffaab8df, false));


        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatMessages = column();
        chatLoadingIndicator = null;
        chatMessages.setGravity(Gravity.TOP);
        chatMessages.setPadding(0, dp(8), 0, dp(12));
        chatScroll.addView(chatMessages, new ScrollView.LayoutParams(-1, -2));
        page.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderChatHistory();

        LinearLayout composer = row();
        composer.setPadding(dp(10), dp(6), dp(8), dp(6));
        composer.setBackground(shape(0xff171e38, dp(28), 1, 0xff354568));

        TextView plus = text("＋", 25, 0xffc5d4f5, false);
        plus.setBackground(shape(0xff222d4b, dp(20), 1, 0xff354568));
        composer.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));

        EditText input = new EditText(this);
        chatInput = input;
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

        FrameLayout actionSlot = new FrameLayout(this);

        TextView call = text("☎", 18, Color.WHITE, true);
        call.setGravity(Gravity.CENTER);
        call.setBackground(shape(0xff142d68, dp(22), 1, 0xff315bce));
        call.setOnClickListener(v -> showCallScreen());

        TextView send = text("↑", 24, Color.WHITE, true);
        chatSendButton = send;
        send.setBackground(shape(0xff3185ff, dp(24), 0, 0));
        send.setElevation(dp(3));

        actionSlot.addView(call, new FrameLayout.LayoutParams(
            dp(40), dp(40), Gravity.CENTER));
        actionSlot.addView(send, new FrameLayout.LayoutParams(
            dp(44), dp(44), Gravity.CENTER));

        send.setVisibility(View.GONE);
        composer.addView(actionSlot,
            new LinearLayout.LayoutParams(dp(44), dp(44)));


        send.setOnClickListener(v -> {
            if (chatSending) {
                LaibaChatEngine.Request running = activeChatRequest;
                if (running != null) running.cancel();
                return;
            }

            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            input.setText("");
            sendChatMessage(message);
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(
                CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                call.setVisibility(hasText || chatSending ? View.GONE : View.VISIBLE);
                send.setVisibility(hasText || chatSending ? View.VISIBLE : View.GONE);
                send.setText(chatSending ? "■" : "↑");
            }

            @Override public void afterTextChanged(
                android.text.Editable s) {}
        });

        page.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        gap(page, 8);
    }


    private void renderChatHistory() {
        if (chatMessages == null) return;
        chatMessages.removeAllViews();
        org.json.JSONArray items = LaibaChatEngine.history(this);
        if (items.length() == 0) {
            TextView welcome = text(
                "السلام علیکم! میں لائبہ ہوں۔\nآپ کی ذاتی AI اسسٹنٹ۔ بتائیے، آج میں آپ کی کیا مدد کر سکتی ہوں؟",
                14, 0xffe7efff, false);
            welcome.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            welcome.setPadding(dp(14), dp(12), dp(14), dp(12));
            welcome.setBackground(shape(0xff192640, dp(18), dp(1), 0xff30496c));
            chatMessages.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
        }
        for (int i = 0; i < items.length(); i++) {
            org.json.JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            addChatBubble(item.optString("text"),
                "user".equals(item.optString("role")));
        }
        if (chatScroll != null) chatScroll.post(() ->
            chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View addChatBubble(String value, boolean user) {
        View bubble;

        if (user) {
            TextView userBubble = text(value, 14, Color.WHITE, false);
            userBubble.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            userBubble.setTextIsSelectable(true);
            userBubble.setPadding(dp(14), dp(11), dp(14), dp(11));
            userBubble.setBackground(shape(
                0xff174b91, dp(18), dp(1), 0xff367fd0));
            bubble = userBubble;

        } else {
            LinearLayout assistantBubble = new LinearLayout(this);
            assistantBubble.setOrientation(LinearLayout.VERTICAL);
            assistantBubble.setGravity(Gravity.LEFT | Gravity.TOP);
            assistantBubble.setPadding(
                dp(14), dp(11), dp(14), dp(11));
            assistantBubble.setBackground(shape(
                0xff192640, dp(18), dp(1), 0xff30496c));

            ChatMessageRenderer.populate(
                assistantBubble, this, value,
                0xffe7efff, 0xff101a31, 0xffa9e7ff, cyan);

            bubble = assistantBubble;
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            user ? (int)(getResources().getDisplayMetrics().widthPixels * .82f) : -1,
            -2);
        lp.gravity = user ? Gravity.RIGHT : Gravity.LEFT;
        lp.bottomMargin = dp(10);
        chatMessages.addView(bubble, lp);
        return bubble;
    }

    private void removePendingChatBubble(View bubble) {
        if (bubble != null && bubble.getParent() instanceof ViewGroup) {
            ((ViewGroup) bubble.getParent()).removeView(bubble);
        }
    }

    private void showChatLoadingIndicator() {
        if (chatMessages == null || chatLoadingIndicator != null) return;

        LinearLayout loadingRow = row();
        loadingRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);

        SplashOrbView logo = new SplashOrbView(24f);
        loadingRow.addView(logo,
            new LinearLayout.LayoutParams(dp(92), dp(68)));

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.gravity = Gravity.LEFT;
        params.bottomMargin = dp(10);
        chatMessages.addView(loadingRow, params);
        chatLoadingIndicator = loadingRow;

        if (chatScroll != null) chatScroll.post(() ->
            chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void removeChatLoadingIndicator() {
        if (chatLoadingIndicator == null) return;
        View indicator = chatLoadingIndicator;
        chatLoadingIndicator = null;
        if (indicator.getParent() instanceof ViewGroup) {
            ((ViewGroup) indicator.getParent()).removeView(indicator);
        }
    }

    private void restoreFailedMessage(String message) {
        if (chatInput == null) return;

        if (chatInput.getText().toString().trim().isEmpty()) {
            chatInput.setText(message);
            chatInput.setSelection(chatInput.getText().length());
        }
    }

    private void sendChatMessage(String message) {

        if (!hasInternetConnection()) {
            if (chatInput != null) {
                chatInput.setText(message);
            }
            updateInternetStatus(false);
            return;
        }

        final View pendingUserBubble = addChatBubble(message, true);
        if (chatScroll != null) chatScroll.post(() ->
            chatScroll.fullScroll(View.FOCUS_DOWN));

        chatSending = true;
        if (chatSendButton != null) {
            chatSendButton.setText("■");
            chatSendButton.setVisibility(View.VISIBLE);
        }

        showChatLoadingIndicator();

        final String key;
        try {
            key = loadApiKeySecurely();
            if (key == null || key.trim().isEmpty()) {
                throw new Exception(
                    "Gemini API key is missing. Please reconnect it in Settings.");
            }
        } catch (Exception e) {
            removeChatLoadingIndicator();
            chatSending = false;
            removePendingChatBubble(pendingUserBubble);
            if (chatSendButton != null) chatSendButton.setText("↑");
            addChatBubble("Laiba: " + e.getMessage(), false);
            restoreFailedMessage(message);
            return;
        }

        activeChatRequest = LaibaChatEngine.send(
            this, key, message, new LaibaChatEngine.Callback() {

            @Override public void onStream(String partialReply) {
                runOnUiThread(() -> {
                    if (!chatSending) return;

                    removeChatLoadingIndicator();

                    if (partialReply == null || partialReply.isEmpty()) {
                        if (streamingBubble != null) {
                            if (streamingBubble.getParent() instanceof ViewGroup) {
                                ((ViewGroup) streamingBubble.getParent())
                                    .removeView(streamingBubble);
                            }
                            streamingBubble = null;
                        }
                        streamingReplyText = "";
                        return;
                    }

                    streamingReplyText = partialReply;

                    if (streamingBubble == null) {
                        streamingBubble = new LinearLayout(
                            MainActivity.this);
                        streamingBubble.setOrientation(
                            LinearLayout.VERTICAL);
                        streamingBubble.setGravity(
                            Gravity.LEFT | Gravity.TOP);
                        streamingBubble.setPadding(
                            dp(14), dp(11), dp(14), dp(11));
                        streamingBubble.setBackground(shape(
                            0xff192640, dp(18), dp(1), 0xff30496c));

                        LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(-1, -2);
                        params.gravity = Gravity.LEFT;
                        params.bottomMargin = dp(10);
                        chatMessages.addView(streamingBubble, params);
                    }

                    ChatMessageRenderer.populate(
                        streamingBubble, MainActivity.this,
                        partialReply + "▍",
                        0xffe7efff, 0xff101a31, 0xffa9e7ff, cyan);

                    if (chatScroll != null) chatScroll.post(() ->
                        chatScroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override public void onReply(String reply) {
                runOnUiThread(() -> {
                    removeChatLoadingIndicator();
                    chatSending = false;
                    activeChatRequest = null;

                    if (streamingBubble != null) {
                        ChatMessageRenderer.populate(
                            streamingBubble, MainActivity.this, reply,
                            0xffe7efff, 0xff101a31, 0xffa9e7ff, cyan);
                        streamingBubble = null;
                        streamingReplyText = "";
                    } else {
                        addChatBubble(reply, false);
                    }

                    if (chatSendButton != null) {
                        chatSendButton.setText("↑");
                        chatSendButton.setVisibility(View.GONE);
                    }

                    if (chatScroll != null) chatScroll.post(() ->
                        chatScroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    removeChatLoadingIndicator();
                    chatSending = false;
                    activeChatRequest = null;
                    removePendingChatBubble(pendingUserBubble);

                    if (streamingBubble != null) {
                        ChatMessageRenderer.populate(
                            streamingBubble, MainActivity.this,
                            streamingReplyText +
                                "\n\nConnection interrupted: " + error,
                            0xffe7efff, 0xff101a31, 0xffa9e7ff, cyan);
                        streamingBubble = null;
                        streamingReplyText = "";
                    } else {
                        addChatBubble(
                            "پیغام بھیجنے میں مسئلہ ہوا: " + error, false);
                    }

                    if (chatSendButton != null) {
                        chatSendButton.setText("↑");
                        chatSendButton.setVisibility(View.GONE);
                    }

                    restoreFailedMessage(message);
                });
            }

            @Override public void onCancelled() {
                runOnUiThread(() -> {
                    removeChatLoadingIndicator();
                    chatSending = false;
                    activeChatRequest = null;
                    removePendingChatBubble(pendingUserBubble);

                    if (streamingBubble != null) {
                        String partial = streamingReplyText;
                        ChatMessageRenderer.populate(
                            streamingBubble, MainActivity.this,
                            partial.isEmpty() ? "Generation stopped." :
                                partial + "\n\n[Generation stopped]",
                            0xffe7efff, 0xff101a31, 0xffa9e7ff, cyan);
                        streamingBubble = null;
                        streamingReplyText = "";
                    }

                    if (chatSendButton != null) {
                        chatSendButton.setText("↑");
                        chatSendButton.setVisibility(View.GONE);
                    }

                    restoreFailedMessage(message);
                });
            }
        });
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
        card.setOnClickListener(v -> {
            if ("Chat History".equals(title)) showChatHistory();
            else if ("Token Usage".equals(title)) showTokenUsage();
            else showMenuPreview(title, description, symbol);
        });
    }

    private void showMenu() {
        menuState = 1;
        base();

        LinearLayout header = row();
        TextView back = createBackButton();
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
        addMenuItem(items, "◈", "Token Usage", "API tokens and usage analytics");
        addMenuItem(items, "◉", "Call History", "Voice sessions");
        addMenuItem(items, "✧", "AI Tools", "Your smart toolkit");
        addMenuItem(items, "⚙", "Settings", "Personalize Laiba");
        scroll.addView(items);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        gap(page, 8);
        page.addView(text("LAIBA  •  PERSONAL AI", 10, 0xff7187b8, true));
    }


    private void showChatHistory() {
        menuState = 2;
        base();
        LinearLayout header = row();
        TextView back = createBackButton();
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> showMenu());
        header.addView(text("CHAT HISTORY", 16, Color.WHITE, true));
        page.addView(header);
        gap(page, dp(12));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        list.setGravity(Gravity.TOP);
        org.json.JSONArray items = LaibaChatEngine.history(this);
        for (int i = 0; i < items.length(); i++) {
            org.json.JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            TextView line = text(
                ("user".equals(item.optString("role")) ? "You: " : "Laiba: ") +
                item.optString("text"), 13, 0xffe7efff, false);
            line.setGravity(Gravity.LEFT);
            line.setPadding(dp(12), dp(10), dp(12), dp(10));
            line.setBackground(shape(0xff192640, dp(14), 1, 0xff30496c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(7);
            list.addView(line, lp);
        }
        if (items.length() == 0)
            list.addView(text("No saved messages yet.", 14, 0xffaab8df, false));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private String usageNumber(long value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    private void usageMetric(
            LinearLayout target, String title, String value, String subtitle) {

        LinearLayout card = column();
        card.setGravity(Gravity.LEFT);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(shape(
                0xff152344, dp(17), dp(1), 0xff31558c));

        TextView heading = text(title, 12, 0xffaab8df, true);
        heading.setGravity(Gravity.LEFT);
        card.addView(heading);

        TextView number = text(value, 22, Color.WHITE, true);
        number.setGravity(Gravity.LEFT);
        card.addView(number);

        TextView detail = text(subtitle, 10, 0xffaab8df, false);
        detail.setGravity(Gravity.LEFT);
        card.addView(detail);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        target.addView(card, lp);
    }

    private void showTokenUsage() {
        menuState = 2;
        base();

        LinearLayout header = row();
        TextView back = createBackButton();
        header.addView(back,
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> showMenu());

        header.addView(text("TOKEN USAGE", 16, Color.WHITE, true));
        page.addView(header);
        gap(page, dp(12));

        org.json.JSONObject stats = TokenUsageManager.summary(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setGravity(Gravity.TOP);
        content.setPadding(0, dp(3), 0, dp(12));

        content.addView(text(
                "GEMINI API ANALYTICS", 11, cyan, true));
        gap(content, dp(12));

        usageMetric(content, "TODAY",
                usageNumber(stats.optLong("dayTotal", 0)),
                "Input: " + usageNumber(stats.optLong("dayInput", 0))
                        + "  •  Output: "
                        + usageNumber(stats.optLong("dayOutput", 0)));

        usageMetric(content, "THIS WEEK",
                usageNumber(stats.optLong("weekTotal", 0)),
                "Input: " + usageNumber(stats.optLong("weekInput", 0))
                        + "  •  Output: "
                        + usageNumber(stats.optLong("weekOutput", 0)));

        usageMetric(content, "THIS MONTH",
                usageNumber(stats.optLong("monthTotal", 0)),
                "Input: " + usageNumber(stats.optLong("monthInput", 0))
                        + "  •  Output: "
                        + usageNumber(stats.optLong("monthOutput", 0)));

        gap(content, dp(8));
        content.addView(text(
                "REQUEST STATISTICS", 11, cyan, true));
        gap(content, dp(10));

        usageMetric(content, "API REQUESTS",
                usageNumber(stats.optLong("requests", 0)),
                "Successful: " + usageNumber(stats.optLong("successful", 0))
                        + "  •  Failed: "
                        + usageNumber(stats.optLong("failures", 0)));

        usageMetric(content, "USAGE DATA NOT PROVIDED",
                usageNumber(stats.optLong("unknownUsage", 0)),
                "Requests for which Gemini did not return token metadata.");

        gap(content, dp(8));
        content.addView(text(
                "RECENT API ACTIVITY", 11, cyan, true));
        gap(content, dp(8));

        org.json.JSONArray records = TokenUsageManager.records(this);
        int shown = 0;

        for (int i = records.length() - 1; i >= 0 && shown < 12; i--) {
            org.json.JSONObject item = records.optJSONObject(i);
            if (item == null) continue;

            boolean success = item.optBoolean("success", false);
            long timestamp = item.optLong("timestamp", 0);

            String date = android.text.format.DateFormat.format(
                    "dd MMM, HH:mm", timestamp).toString();

            String tokens = item.optLong("totalTokens", -1) < 0
                    ? "Token count unavailable"
                    : usageNumber(item.optLong("totalTokens", 0))
                            + " tokens";

            String status = success ? "SUCCESS" : "FAILED";

            TextView line = text(
                    status + "  •  " + date + "\n" + tokens
                            + "  •  Attempts: "
                            + item.optInt("attempts", 0),
                    12, success ? 0xffa9e7ff : 0xffffa8b5, false);

            line.setGravity(Gravity.LEFT);
            line.setPadding(dp(12), dp(11), dp(12), dp(11));
            line.setBackground(shape(
                    0xff192640, dp(13), dp(1), 0xff30496c));

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(7);
            content.addView(line, lp);
            shown++;
        }

        if (shown == 0) {
            content.addView(text(
                    "No API activity recorded yet.",
                    13, 0xffaab8df, false));
        }

        gap(content, dp(10));
        content.addView(text(
                "Token counts are based on Gemini usageMetadata. "
                        + "Unavailable counts are not estimated. "
                        + "No chat messages or API keys are stored here.",
                11, 0xff8296c2, false));

        scroll.addView(content);
        page.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showMenuPreview(String title, String description, String symbol) {
        menuState = 2;
        base();

        LinearLayout header = row();
        TextView back = createBackButton();
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


        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 0.45f));
    }

    @Override public void onBackPressed() {
        if (menuState == 2) {
            showMenu();
        } else if (menuState == 1) {
            showChat();
        } else if (setupPage == 1) {
            showMenu();
        } else if (setupPage == 2) {
            showVerification();
        } else if (setupPage == 0) {
            // Stay on verification; never bypass it with Back.
        } else {
            super.onBackPressed();
        }
    }
}
