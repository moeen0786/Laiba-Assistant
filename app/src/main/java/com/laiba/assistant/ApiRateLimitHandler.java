package com.laiba.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

public final class ApiRateLimitHandler {

    private static final String PREFS = "laiba_api_rate_limit_v1";
    private static final String COOLDOWN = "cooldown_until";

    private static final long BASE_DELAY = 1000L;
    private static final long MAX_BACKOFF = 60000L;

    private ApiRateLimitHandler() {}

    public static final class Policy {
        public final boolean retryable;
        public final boolean quotaExhausted;
        public final long delayMillis;
        public final String message;

        private Policy(boolean retryable, boolean quotaExhausted,
                       long delayMillis, String message) {
            this.retryable = retryable;
            this.quotaExhausted = quotaExhausted;
            this.delayMillis = delayMillis;
            this.message = message;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
    }

    public static Policy evaluate(
            Context context, int status,
            String responseBody, String retryAfter) {
        return evaluate(context, status, responseBody, retryAfter, 0);
    }

    public static Policy evaluate(
            Context context, int status,
            String responseBody, String retryAfter, int attempt) {

        String body = responseBody == null ? "" : responseBody;
        String lower = body.toLowerCase(Locale.ROOT);

        String reason = extractReason(body).toLowerCase(Locale.ROOT);
        String combined = lower + " " + reason;

        boolean quota = containsAny(combined,
                "per_day", "per day", "daily quota",
                "daily limit", "limit: 0", "quota exhausted",
                "free_tier_daily", "requests_per_day",
                "tokens_per_day");

        if (status == 403 && !containsAny(combined,
                "quota", "rate limit", "resource_exhausted",
                "requests_per_minute", "tokens_per_minute")) {
            return new Policy(false, false, 0,
                    "Gemini access denied. Check API key and permissions.");
        }

        if (quota) {
            return new Policy(false, true, 0,
                    "Gemini API quota exhausted. Check your quota and billing limits.");
        }

        boolean retryable = status == 408 || status == 429
                || status == 500 || status == 502
                || status == 503 || status == 504
                || (status == 403 && containsAny(combined,
                    "quota", "rate limit", "resource_exhausted"));

        if (!retryable) {
            return new Policy(false, false, 0,
                    "Gemini API request failed (HTTP " + status + ").");
        }

        long serverDelay = parseRetryAfter(retryAfter);
        if (serverDelay <= 0) {
            serverDelay = parseRetryInfo(body);
        }

        long delay = serverDelay > 0
                ? serverDelay
                : backoffDelay(attempt);

        delay = Math.max(1000L, delay);

        persistCooldown(context, delay);

        return new Policy(true, false, delay,
                "Gemini rate limit reached. Retrying after "
                        + (delay / 1000L) + " seconds.");
    }

    public static long backoffDelay(int attempt) {
        int exponent = Math.min(Math.max(attempt, 0), 6);

        long exponential = Math.min(
                MAX_BACKOFF, BASE_DELAY * (1L << exponent));

        long jitter = ThreadLocalRandom.current()
                .nextLong(0, Math.max(1L, exponential / 4L));

        return Math.min(MAX_BACKOFF, exponential + jitter);
    }

    public static long remainingCooldown(Context context) {
        try {
            long until = prefs(context).getLong(COOLDOWN, 0L);
            long remaining = until - System.currentTimeMillis();

            if (remaining <= 0) {
                prefs(context).edit().remove(COOLDOWN).apply();
                return 0;
            }

            return remaining;
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private static void persistCooldown(Context context, long delay) {
        long until = System.currentTimeMillis() + delay;

        synchronized (ApiRateLimitHandler.class) {
            long existing = prefs(context).getLong(COOLDOWN, 0L);
            prefs(context).edit()
                    .putLong(COOLDOWN, Math.max(existing, until))
                    .apply();
        }
    }

    public static boolean waitForCooldown(
            Context context, LaibaChatEngine.Request request) {

        while (!request.isCancelled()) {
            long remaining = remainingCooldown(context);

            if (remaining <= 0) return true;

            try {
                Thread.sleep(Math.min(remaining, 500L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    public static boolean waitBeforeRetry(
            LaibaChatEngine.Request request, long delay) {

        long deadline = System.currentTimeMillis() + delay;

        while (!request.isCancelled()) {
            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) return true;

            try {
                Thread.sleep(Math.min(remaining, 500L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private static long parseRetryAfter(String value) {
        if (value == null || value.trim().isEmpty()) return 0;

        String input = value.trim();

        try {
            double seconds = Double.parseDouble(input);
            if (seconds >= 0) {
                return (long) Math.ceil(seconds * 1000.0);
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));

            Date date = format.parse(input);
            if (date != null) {
                return Math.max(0,
                        date.getTime() - System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private static long parseRetryInfo(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error == null) return 0;

            JSONArray details = error.optJSONArray("details");
            if (details == null) return 0;

            for (int i = 0; i < details.length(); i++) {
                JSONObject detail = details.optJSONObject(i);
                if (detail == null) continue;

                String type = detail.optString("@type", "");

                if (!type.contains("RetryInfo")) continue;

                String duration = detail.optString("retryDelay", "");
                if (!duration.endsWith("s")) continue;

                double seconds = Double.parseDouble(
                        duration.substring(0, duration.length() - 1));

                return seconds >= 0
                        ? (long) Math.ceil(seconds * 1000.0) : 0;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private static String extractReason(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");

            if (error == null) return "";

            StringBuilder result = new StringBuilder();

            result.append(error.optString("status", ""))
                    .append(' ')
                    .append(error.optString("message", ""));

            JSONArray details = error.optJSONArray("details");

            if (details != null) {
                for (int i = 0; i < details.length(); i++) {
                    JSONObject detail = details.optJSONObject(i);
                    if (detail == null) continue;

                    result.append(' ')
                            .append(detail.optString("reason", ""))
                            .append(' ')
                            .append(detail.optString("quotaId", ""))
                            .append(' ')
                            .append(detail.optString("quotaMetric", ""))
                            .append(' ')
                            .append(detail.optString("description", ""))
                            .append(' ')
                            .append(detail.optString("subject", ""));
                }
            }

            return result.toString();

        } catch (Exception error) {
            return body;
        }
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) return true;
        }
        return false;
    }
}
