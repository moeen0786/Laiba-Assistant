package com.laiba.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

public class LaibaChatEngine {

    private static final int MAX_API_ATTEMPTS = 5;
    private static final boolean ENABLE_LOCAL_MEMORY_LOOKUP = false;

    private static final String PREFS = "laiba_chat_memory_v1";
    private static final String HISTORY_KEY = "history";
    private static final String MEMORY_KEY = "memory";
    private static final int HISTORY_LIMIT = 500;
    private static final int MEMORY_LIMIT = 300;

    public interface Callback {
        void onStream(String completePartialReply);
        void onReply(String reply);
        void onError(String error);
        void onCancelled();
    }

    public interface ApiKeyProvider {
        String load() throws Exception;
    }

    public static final class Request {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile HttpURLConnection connection;
        private volatile Thread worker;
        private boolean completed;

        public void cancel() {
            HttpURLConnection active;
            Thread running;

            synchronized (this) {
                if (completed || !cancelled.compareAndSet(false, true)) return;
                active = connection;
                connection = null;
                running = worker;
            }

            if (active != null) active.disconnect();
            if (running != null) running.interrupt();
        }

        public synchronized boolean markCompleted() {
            if (cancelled.get()) return false;
            completed = true;
            return true;
        }

        private synchronized boolean attachConnection(
                HttpURLConnection active) {
            if (cancelled.get()) {
                active.disconnect();
                return false;
            }

            connection = active;

            if (cancelled.get()) {
                connection = null;
                active.disconnect();
                return false;
            }

            return true;
        }

        private synchronized void clearConnection(
                HttpURLConnection active) {
            if (connection == active) {
                connection = null;
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized JSONArray history(Context context) {
        try {
            return new JSONArray(prefs(context).getString(HISTORY_KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized JSONArray memory(Context context) {
        try {
            return new JSONArray(prefs(context).getString(MEMORY_KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static synchronized void saveHistory(
            Context context, JSONArray items) {
        JSONArray limited = new JSONArray();
        int start = Math.max(0, items.length() - HISTORY_LIMIT);

        for (int i = start; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) limited.put(item);
        }

        prefs(context).edit()
                .putString(HISTORY_KEY, limited.toString())
                .apply();
    }

    private static synchronized void addMessage(
            Context context, String role, String message) {
        JSONArray items = history(context);
        JSONObject item = new JSONObject();

        try {
            item.put("role", role);
            item.put("text", message);
            items.put(item);
            saveHistory(context, items);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void removeLastMessage(
            Context context, String role, String message) {
        JSONArray items = history(context);
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item != null
                    && role.equals(item.optString("role"))
                    && message.equals(item.optString("text"))) {
                items.remove(i);
                saveHistory(context, items);
                return;
            }
        }
    }

    private static synchronized void saveMemory(
            Context context, String fact) {
        if (fact == null) return;

        fact = fact.trim();
        if (fact.length() < 3 || fact.length() > 500) return;

        String lower = fact.toLowerCase();
        String[] blocked = {
                "password", "passcode", "api key", "secret key",
                "otp", "credit card", "private key"
        };

        for (String word : blocked) {
            if (lower.contains(word)) return;
        }

        JSONArray items = memory(context);

        for (int i = 0; i < items.length(); i++) {
            if (fact.equalsIgnoreCase(items.optString(i))) return;
        }

        items.put(fact);

        JSONArray limited = new JSONArray();
        int start = Math.max(0, items.length() - MEMORY_LIMIT);
        for (int i = start; i < items.length(); i++) {
            limited.put(items.optString(i));
        }

        prefs(context).edit()
                .putString(MEMORY_KEY, limited.toString())
                .apply();
    }

    private static String explicitMemoryText(
            Context context, String message) {

        String lower = message.toLowerCase();
        String[] markers = {
                "remember this:",
                "remember:",
                "save this to memory:",
                "save to memory:",
                "یاد رکھو:",
                "یاد رکھنا:",
                "میموری میں محفوظ کرو:",
                "میموری میں سیو کرو:"
        };

        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                String fact = message.substring(index + marker.length()).trim();
                if (!fact.isEmpty()) return fact;
            }
        }

        String[] commands = {
                "remember this", "save this message",
                "save this to memory", "remember this message",
                "اس میسج کو یاد رکھو",
                "یہ بات یاد رکھو", "اس بات کو یاد رکھو",
                "لائبہ یہ میسج آپ اپنی میموری میں سیو کریں",
                "لائبہ یہ میسج آپ سیف کریں اپنی میموری میں",
                "لائبہ یہ میسج سیو کریں اپنی میموری میں",
                "لائبہ اس میسج کو اپنی میموری میں سیو کریں",
                "لائبہ یہ میسج اپنی میموری میں محفوظ کریں",
                "لائبہ یہ بات اپنی میموری میں محفوظ کریں",
                "اس میسج کو اپنی میموری میں محفوظ کریں",
                "اس میسج کو اپنی میموری میں سیو کریں",
                "یہ بات اپنی میموری میں محفوظ کریں",
                "اس بات کو اپنی میموری میں یاد رکھیں",
                "لائبہ یہ بات یاد رکھیں",
                "یہ بات ہمیشہ یاد رکھنا",
                "اس میسج کو میموری میں سیو کرو",
                "اس بات کو میموری میں محفوظ کرو"
        };

        for (String command : commands) {
            if (lower.contains(command)) {
                JSONArray items = history(context);

                for (int i = items.length() - 1; i >= 0; i--) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null
                            && "user".equals(item.optString("role"))) {
                        String previous = item.optString("text");
                        if (!previous.equals(message)) return previous;
                    }
                }
            }
        }

        return null;
    }

    public static Request send(
            Context context, String apiKey, String message, Callback callback) {
        return sendWithApiKeyProvider(
                context, () -> apiKey, message, callback);
    }

    public static Request sendWithApiKeyProvider(
            Context context,
            ApiKeyProvider apiKeyProvider,
            String message,
            Callback callback) {

        final Context app = context.getApplicationContext();
        final String cleanMessage = message == null ? "" : message.trim();
        final Request request = new Request();
        final android.os.Handler handler =
                new android.os.Handler(app.getMainLooper());

        if (cleanMessage.isEmpty()) {
            handler.post(() -> callback.onError("Please enter a message."));
            return request;
        }

        Thread worker = new Thread(() -> {
            String finalReply = null;
            JSONObject[] usageMetadata = new JSONObject[1];
            JSONObject[] attemptUsageMetadata = new JSONObject[1];
            int attempts = 0;
            boolean usageRecorded = false;

            try {
                if (request.isCancelled()) {
                    handler.post(callback::onCancelled);
                    return;
                }

                // Save-memory commands are also processed off the UI thread.
                String explicitFact = explicitMemoryText(app, cleanMessage);
                if (explicitFact != null) {
                    if (!request.markCompleted()) {
                        handler.post(callback::onCancelled);
                        return;
                    }

                    saveMemory(app, explicitFact);
                    handler.post(() -> callback.onReply(
                            "ٹھیک ہے، میں نے یہ بات اپنی الگ Memory میں محفوظ کر لی ہے۔"));
                    return;
                }

                String localAnswer = null;
                if (ENABLE_LOCAL_MEMORY_LOOKUP) {
                // Search up to 500 chat messages and 300 saved memory facts.
                // This scan runs on this worker, never on Android's UI thread.
                long localSearchStarted =
                        android.os.SystemClock.elapsedRealtime();

                FutureTask<String> memoryTask = new FutureTask<>(
                        () -> LocalMemorySystem.findAnswer(app, cleanMessage));

                Thread memoryThread = new Thread(
                        memoryTask, "Laiba-Memory-500ms");
                memoryThread.setDaemon(true);
                memoryThread.start();

                boolean memoryTimedOut = false;

                try {
                    localAnswer = memoryTask.get(500L, TimeUnit.MILLISECONDS);

                } catch (TimeoutException timeout) {
                    memoryTimedOut = true;
                    memoryTask.cancel(true);

                } catch (ExecutionException lookupError) {
                    android.util.Log.e(
                            "LaibaLocalMemory",
                            "Local search failed; Gemini fallback",
                            lookupError.getCause());

                } catch (InterruptedException interrupted) {
                    memoryTask.cancel(true);

                    if (request.isCancelled()) {
                        handler.post(callback::onCancelled);
                        return;
                    }

                    Thread.interrupted();
                }

                long localSearchMillis =
                        android.os.SystemClock.elapsedRealtime()
                                - localSearchStarted;

                android.util.Log.i(
                        "LaibaLocalMemory",
                        "elapsed_ms=" + localSearchMillis
                                + " budget_ms=500"
                                + " timed_out=" + memoryTimedOut
                                + " history_limit=500 memory_limit=300"
                                + " matched=" + (localAnswer != null));

                }

                if (request.isCancelled()) {
                    handler.post(callback::onCancelled);
                    return;
                }

                if (localAnswer != null) {
                    if (!request.markCompleted()) {
                        handler.post(callback::onCancelled);
                        return;
                    }

                    addMessage(app, "user", cleanMessage);
                    addMessage(app, "model", localAnswer);

                    final String answer = localAnswer;
                    handler.post(() -> callback.onReply(answer));
                    return;
                }

                // No strong local match: save the new question, then use Gemini.
                addMessage(app, "user", cleanMessage);

                // Do not decrypt/load the API key unless Gemini is required.
                String apiKey = apiKeyProvider == null
                        ? null : apiKeyProvider.load();

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new Exception(
                            "Gemini API key is missing. Please add it in Settings.");
                }

                String systemPrompt =
                        "You are Laiba, a helpful personal AI assistant. " +
                        "Support these three user languages: Urdu written in " +
                        "Urdu script, Roman Urdu written in Latin script, and English. " +
                        "Identify the language and script of each incoming message. " +
                        "If the user writes Urdu in Urdu script, reply in Urdu script. " +
                        "If the user writes Roman Urdu, reply in Roman Urdu using Latin " +
                        "letters. If the user writes English, reply in English. " +
                        "For mixed-language messages, follow the user's dominant " +
                        "language and writing style. Do not convert Urdu into Roman " +
                        "Urdu or Roman Urdu into Urdu script unless requested. " +
                        "Keep the same language throughout the reply unless the user " +
                        "asks for translation or another language. " +
                        "Answer the current user message accurately. " +
                        "Do not assume access to previous conversations or " +
                        "private saved memories. Do not invent personal facts. " +
                        "Return ONLY valid JSON with exactly these fields: " +
                        "\"reply\" (string) and \"memories\" (array of strings). " +
                        "The reply must answer the current user message. " +
                        "The memories array should contain only useful, stable, " +
                        "non-sensitive facts worth remembering for future chats. " +
                        "Never save passwords, API keys, OTPs, financial secrets, " +
                        "or sensitive personal information. " +
                        "If there are no useful new memories, return an empty array.";

                // Only the new user message is included in Gemini contents.
                JSONArray contents = buildContents(cleanMessage);
                JSONObject body = new JSONObject();

                JSONObject systemInstruction = new JSONObject();
                JSONArray systemParts = new JSONArray();
                systemParts.put(new JSONObject().put("text", systemPrompt));
                systemInstruction.put("parts", systemParts);
                body.put("systemInstruction", systemInstruction);
                body.put("contents", contents);

                JSONObject generationConfig = new JSONObject();
                generationConfig.put("responseMimeType", "application/json");

                JSONObject responseSchema = new JSONObject();
                responseSchema.put("type", "OBJECT");

                JSONObject properties = new JSONObject();
                properties.put("reply",
                        new JSONObject().put("type", "STRING"));

                JSONObject memorySchema = new JSONObject();
                memorySchema.put("type", "ARRAY");
                memorySchema.put("items",
                        new JSONObject().put("type", "STRING"));
                properties.put("memories", memorySchema);

                responseSchema.put("properties", properties);

                JSONArray required = new JSONArray();
                required.put("reply");
                required.put("memories");
                responseSchema.put("required", required);

                generationConfig.put("responseSchema", responseSchema);
                generationConfig.put("maxOutputTokens", 1024);

                JSONObject thinkingConfig = new JSONObject();
                thinkingConfig.put("thinkingLevel", "minimal");
                generationConfig.put("thinkingConfig", thinkingConfig);
                body.put("generationConfig", generationConfig);

                String endpoint =
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "gemma-4-31b-it:streamGenerateContent?alt=sse";

                StringBuilder raw = new StringBuilder();
                Exception lastError = null;
                // Send the current question immediately.
                // No pre-request cooldown wait or automatic retry.

                for (int attempt = 0; attempt < MAX_API_ATTEMPTS; attempt++) {
                    if (request.isCancelled()) break;

                    raw.setLength(0);
                    attemptUsageMetadata[0] = null;
                    attempts = attempt + 1;

                    try {
                        streamAttempt(
                                endpoint, apiKey.trim(), body, request, raw,
                                handler, callback, attemptUsageMetadata);

                        if (request.isCancelled()) break;

                        String attemptText = raw.toString().trim();
                        if (attemptText.isEmpty()) {
                            throw new IOException(
                                    "Gemini stream ended without a complete response.");
                        }

                        JSONObject attemptResult = new JSONObject(attemptText);
                        if (attemptResult.optString("reply", "").trim().isEmpty()) {
                            throw new IOException(
                                    "Gemini returned an incomplete reply.");
                        }

                        usageMetadata[0] = mergeUsageMetadata(
                                usageMetadata[0], attemptUsageMetadata[0]);
                        lastError = null;
                        break;

                    } catch (Exception failure) {
                        usageMetadata[0] = mergeUsageMetadata(
                                usageMetadata[0], attemptUsageMetadata[0]);
                        if (request.isCancelled()) break;
                        lastError = failure;

                        long retryDelay;

                        if (failure instanceof HttpFailure) {
                            HttpFailure http = (HttpFailure) failure;

                            ApiRateLimitHandler.Policy policy =
                                    ApiRateLimitHandler.evaluate(
                                            app, http.status,
                                            http.responseBody,
                                            http.retryAfter,
                                            attempt);

                            if (!policy.retryable || attempt >= MAX_API_ATTEMPTS - 1) {
                                lastError = new Exception(policy.message);
                                break;
                            }

                            retryDelay = policy.delayMillis;

                        } else {
                            if (attempt >= MAX_API_ATTEMPTS - 1 || !isRetryable(failure)) break;
                            retryDelay = ApiRateLimitHandler.backoffDelay(attempt);
                        }

                        final int retryNumber = attempt + 1;
                        final long waitSeconds = retryDelay / 1000L;

                        handler.post(() -> {
                            if (!request.isCancelled()) {
                                callback.onStream("");
                            }
                        });

                        if (!ApiRateLimitHandler.waitBeforeRetry(
                                request, retryDelay)) {
                            if (request.isCancelled()) break;
                            lastError = new Exception(
                                    "Retry interrupted. Please try again.");
                            break;
                        }
                    }
                }

                if (request.isCancelled()) {
                    if (attempts > 0 && !usageRecorded) {
                        TokenUsageManager.record(
                                app, usageMetadata[0], false, attempts);
                        usageRecorded = true;
                    }
                    removeLastMessage(app, "user", cleanMessage);
                    handler.post(callback::onCancelled);
                    return;
                }

                if (lastError != null) throw lastError;

                String rawText = raw.toString().trim();
                if (rawText.isEmpty()) {
                    throw new Exception("Gemini returned an empty response.");
                }

                JSONObject result = new JSONObject(rawText);
                finalReply = result.optString("reply", "").trim();

                if (finalReply.isEmpty()) {
                    throw new Exception("Gemini returned an empty reply.");
                }

                if (!request.markCompleted()) {
                    removeLastMessage(app, "user", cleanMessage);
                    handler.post(callback::onCancelled);
                    return;
                }

                JSONArray newMemories = result.optJSONArray("memories");
                if (newMemories != null) {
                    for (int i = 0; i < newMemories.length(); i++) {
                        saveMemory(app, newMemories.optString(i));
                    }
                }

                if (!usageRecorded) {
                    TokenUsageManager.record(
                            app, usageMetadata[0], true, attempts);
                    usageRecorded = true;
                }

                // Persist only the completed assistant response.
                addMessage(app, "model", finalReply);
                final String completed = finalReply;
                handler.post(() -> {
                    if (!request.isCancelled()) callback.onReply(completed);
                });

            } catch (Exception failure) {
                if (attempts > 0 && !usageRecorded) {
                    TokenUsageManager.record(
                            app, usageMetadata[0], false, attempts);
                    usageRecorded = true;
                }

                if (request.isCancelled()) {
                    removeLastMessage(app, "user", cleanMessage);
                    handler.post(callback::onCancelled);
                    return;
                }

                removeLastMessage(app, "user", cleanMessage);
                String error = failure.getMessage() == null
                        ? "Unable to contact Gemini." : failure.getMessage();

                handler.post(() -> {
                    if (!request.isCancelled()) callback.onError(error);
                });

            } finally {
                request.connection = null;
            }
        }, "Laiba-Gemini-Streaming");

        request.worker = worker;
        worker.start();
        return request;
    }

    private static void streamAttempt(
            String endpoint,
            String apiKey,
            JSONObject body,
            Request request,
            StringBuilder raw,
            android.os.Handler handler,
            Callback callback,
            JSONObject[] usageMetadata) throws Exception {

        HttpURLConnection connection = null;
        String timeoutStage = "opening Google connection";

        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();

            if (!request.attachConnection(connection)) {
                return;
            }

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("x-goog-api-key", apiKey);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);

            timeoutStage = "sending request body to Google";
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
                output.flush();
            }

            if (request.isCancelled()) return;

            timeoutStage = "waiting for Google's response headers";
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                timeoutStage = "reading Google's error response";
                InputStream errorStream = connection.getErrorStream();
                String responseBody = readStream(errorStream);
                String detail = responseBody;

                try {
                    JSONObject errorJson = new JSONObject(detail);
                    JSONObject errorObject = errorJson.optJSONObject("error");
                    if (errorObject != null) {
                        detail = errorObject.optString("message", detail);
                    }
                } catch (Exception ignored) {
                }

                throw new HttpFailure(
                        status,
                        "Gemini API (" + status + "): " + detail,
                        responseBody,
                        connection.getHeaderField("Retry-After"));
            }

            StringBuilder partialJsonReply = new StringBuilder();
            timeoutStage = "receiving Gemini streaming data";

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (request.isCancelled()) return;
                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    JSONObject event;
                    try {
                        event = new JSONObject(data);
                    } catch (Exception malformedEvent) {
                        continue;
                    }

                    JSONObject receivedUsage =
                            event.optJSONObject("usageMetadata");
                    if (receivedUsage != null) {
                        usageMetadata[0] = receivedUsage;
                    }

                    JSONArray candidates = event.optJSONArray("candidates");
                    if (candidates == null || candidates.length() == 0) continue;

                    JSONObject candidate = candidates.optJSONObject(0);
                    JSONObject content = candidate == null
                            ? null : candidate.optJSONObject("content");
                    JSONArray parts = content == null
                            ? null : content.optJSONArray("parts");

                    if (parts == null) continue;

                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.optJSONObject(i);
                        if (part == null) continue;

                        String chunk = part.optString("text", "");
                        if (!chunk.isEmpty()) raw.append(chunk);
                    }

                    String visible = extractJsonStringField(raw.toString(), "reply");
                    if (!visible.equals(partialJsonReply.toString())) {
                        partialJsonReply.setLength(0);
                        partialJsonReply.append(visible);

                        final String snapshot = visible;
                        handler.post(() -> {
                            if (!request.isCancelled()) callback.onStream(snapshot);
                        });
                    }
                }
            }

            if (!request.isCancelled() && raw.length() == 0) {
                throw new IOException("Gemini stream ended without response text.");
            }

        } catch (SocketTimeoutException timeout) {
            SocketTimeoutException detailed = new SocketTimeoutException(
                    "Gemini timed out while " + timeoutStage
                            + ". Connect timeout: 15 seconds; read timeout: 120 seconds. "
                            + "Check internet connection or Google model response.");
            detailed.initCause(timeout);
            throw detailed;
        } finally {
            if (connection != null) {
                request.clearConnection(connection);
                connection.disconnect();
            }
        }
    }

    private static final class HttpFailure extends IOException {
        final int status;
        final String responseBody;
        final String retryAfter;

        HttpFailure(int status, String message,
                    String responseBody, String retryAfter) {
            super(message);
            this.status = status;
            this.responseBody = responseBody;
            this.retryAfter = retryAfter;
        }
    }

    private static JSONObject mergeUsageMetadata(
            JSONObject total, JSONObject attempt) {

        if (attempt == null) return total;

        try {
            JSONObject merged = total == null
                    ? new JSONObject()
                    : new JSONObject(total.toString());

            String[] fields = {
                    "promptTokenCount",
                    "candidatesTokenCount",
                    "totalTokenCount",
                    "cachedContentTokenCount",
                    "thoughtsTokenCount"
            };

            for (String field : fields) {
                if (attempt.has(field)) {
                    long previous = merged.optLong(field, 0);
                    long current = attempt.optLong(field, 0);
                    merged.put(field, previous + current);
                }
            }

            return merged;
        } catch (Exception ignored) {
            return total == null ? attempt : total;
        }
    }

    private static boolean isRetryable(Exception error) {
        if (error instanceof HttpFailure) {
            int code = ((HttpFailure) error).status;
            return code == 408 || code == 429 || code == 500
                    || code == 502 || code == 503 || code == 504;
        }
        return error instanceof SocketTimeoutException
                || error instanceof IOException
                || error instanceof org.json.JSONException;
    }

    // Reads the "reply" JSON string while it is still being generated.
    // Incomplete escape sequences are held until the next stream chunk.
    private static String extractJsonStringField(String json, String field) {
        String marker = "\"" + field + "\"";
        int key = json.indexOf(marker);
        if (key < 0) return "";

        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) return "";

        int quote = colon + 1;
        while (quote < json.length()
                && Character.isWhitespace(json.charAt(quote))) quote++;

        if (quote >= json.length() || json.charAt(quote) != '"') return "";

        StringBuilder decoded = new StringBuilder();

        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"') break;

            if (c != '\\') {
                decoded.append(c);
                continue;
            }

            if (i + 1 >= json.length()) break;
            char escaped = json.charAt(++i);

            switch (escaped) {
                case '"': decoded.append('"'); break;
                case '\\': decoded.append('\\'); break;
                case '/': decoded.append('/'); break;
                case 'b': decoded.append('\b'); break;
                case 'f': decoded.append('\f'); break;
                case 'n': decoded.append('\n'); break;
                case 'r': decoded.append('\r'); break;
                case 't': decoded.append('\t'); break;
                case 'u':
                    if (i + 4 >= json.length()) return decoded.toString();
                    try {
                        decoded.append((char) Integer.parseInt(
                                json.substring(i + 1, i + 5), 16));
                        i += 4;
                    } catch (NumberFormatException invalidEscape) {
                        return decoded.toString();
                    }
                    break;
                default:
                    decoded.append(escaped);
                    break;
            }
        }

        return decoded.toString();
    }

    // Gemini receives only the current user message.
    // Local history and saved memory are searched before this method is called.
    private static JSONArray buildContents(String currentMessage)
            throws Exception {

        JSONObject entry = new JSONObject();
        entry.put("role", "user");

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", currentMessage));
        entry.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(entry);
        return contents;
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}
