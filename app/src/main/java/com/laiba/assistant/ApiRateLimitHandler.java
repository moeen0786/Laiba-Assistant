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
    private static final long MAX_SERVER_DELAY = 86400000L;

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
                    "quota", "rate limit", "rate_limit",
                    "ratelimitexceeded", "resource_exhausted"));

        if (!retryable) {
            return new Policy(false, false, 0,
                    detailedProviderError(status, body));
        }

        long serverDelay = parseRetryAfter(retryAfter);
        if (serverDelay <= 0) {
            serverDelay = parseRetryInfo(body);
        }

        long delay = serverDelay > 0
                ? serverDelay
                : backoffDelay(attempt);

        delay = Math.max(1000L,
                Math.min(MAX_SERVER_DELAY, delay));

        persistCooldown(context, delay);

        String message;

        if (status == 429) {
            message = "Gemini returned HTTP 429. "
                    + "Google reported a rate or quota limit. "
                    + "Daily quota may still be available. "
                    + "Automatic retries are disabled. One request was sent; inspect the Google error before retrying.";
        } else {
            message = detailedProviderError(status, body);
        }

        return new Policy(true, false, delay, message);
    }

    private static String detailedProviderError(
            int httpStatus, String responseBody) {

        StringBuilder output = new StringBuilder();
        output.append("Google API HTTP ").append(httpStatus);

        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject error = root.optJSONObject("error");

            if (error == null) {
                output.append("\\nGoogle did not provide a structured error.");
            } else {
                String status = error.optString("status", "").trim();
                String message = error.optString("message", "").trim();

                if (!status.isEmpty()) {
                    output.append("\\nStatus: ").append(status);
                }

                if (!message.isEmpty()) {
                    output.append("\\nGoogle message: ").append(message);
                }

                JSONArray details = error.optJSONArray("details");
                if (details != null) {
                    for (int i = 0; i < details.length(); i++) {
                        JSONObject detail = details.optJSONObject(i);
                        if (detail == null) continue;

                        String reason = detail.optString("reason", "").trim();
                        String description =
                                detail.optString("description", "").trim();
                        String domain = detail.optString("domain", "").trim();

                        if (!reason.isEmpty()) {
                            output.append("\\nReason: ").append(reason);
                        }
                        if (!description.isEmpty()) {
                            output.append("\\nDetails: ").append(description);
                        }
                        if (!domain.isEmpty()) {
                            output.append("\\nDomain: ").append(domain);
                        }
                    }
                }
            }
        } catch (Exception parseError) {
            output.append("\\nGoogle response could not be parsed.");
        }

        output.append("\\nAutomatic retries are disabled. One request was sent; inspect the Google error before retrying.");

        if (output.length() > 1000) {
            return output.substring(0, 997) + "...";
        }

        return output.toString();
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
        long now = System.currentTimeMillis();
        long until = delay > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + delay;

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

        long safeDelay = Math.max(0L,
                Math.min(MAX_SERVER_DELAY, delay));
        long now = System.currentTimeMillis();
        long deadline = safeDelay > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + safeDelay;

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
            if (Double.isFinite(seconds) && seconds >= 0) {
                return (long) Math.min(
                        MAX_SERVER_DELAY,
                        Math.ceil(seconds * 1000.0));
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));

            Date date = format.parse(input);
            if (date != null) {
                long remaining = date.getTime()
                        - System.currentTimeMillis();
                return Math.max(0L,
                        Math.min(MAX_SERVER_DELAY, remaining));
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

                return Double.isFinite(seconds) && seconds >= 0
                        ? (long) Math.min(MAX_SERVER_DELAY,
                                Math.ceil(seconds * 1000.0)) : 0;
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
