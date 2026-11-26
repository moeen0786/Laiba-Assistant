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

public class LaibaChatEngine {

    private static final String PREFS = "laiba_chat_memory_v1";
    private static final String HISTORY_KEY = "history";
    private static final String MEMORY_KEY = "memory";
    private static final int HISTORY_LIMIT = 200;
    private static final int MEMORY_LIMIT = 300;

    public interface Callback {
        void onReply(String reply);
        void onError(String error);
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

    public static void send(
            Context context, String apiKey, String message, Callback callback) {

        final Context app = context.getApplicationContext();
        final String cleanMessage = message == null ? "" : message.trim();

        if (cleanMessage.isEmpty()) {
            callback.onError("Please enter a message.");
            return;
        }

        String explicitFact = explicitMemoryText(app, cleanMessage);

        if (explicitFact != null) {
            saveMemory(app, explicitFact);
            callback.onReply("ٹھیک ہے، میں نے یہ بات اپنی الگ Memory میں محفوظ کر لی ہے۔");
            return;
        }

        addMessage(app, "user", cleanMessage);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new Exception("Gemini API key is missing. Please add it in Settings.");
                }

                JSONArray savedMemory = memory(app);
                StringBuilder memoryText = new StringBuilder();

                for (int i = 0; i < savedMemory.length(); i++) {
                    memoryText.append("- ")
                            .append(savedMemory.optString(i))
                            .append("\n");
                }

                String systemPrompt =
                        "You are Laiba, a helpful personal AI assistant. " +
                        "Reply naturally in the language used by the user. " +
                        "Use the supplied saved memory when relevant. " +
                        "Return ONLY valid JSON with exactly these fields: " +
                        "\"reply\" (string) and \"memories\" (array of strings). " +
                        "The reply must be the actual answer to the user. " +
                        "The memories array should contain only useful, stable, " +
                        "non-sensitive facts worth remembering for future chats. " +
                        "Never save passwords, API keys, OTPs, financial secrets, " +
                        "or sensitive personal information. Do not invent facts. " +
                        "If there are no useful new memories, return an empty array.\n\n" +
                        "SAVED MEMORY:\n" + memoryText;

                JSONArray contents = buildContents(history(app));

                JSONObject body = new JSONObject();

                JSONObject systemInstruction = new JSONObject();
                JSONArray systemParts = new JSONArray();
                systemParts.put(new JSONObject().put("text", systemPrompt));
                systemInstruction.put("parts", systemParts);
                body.put("systemInstruction", systemInstruction);
                body.put("contents", contents);

                JSONObject generationConfig = new JSONObject();
                generationConfig.put("responseMimeType", "application/json");
                generationConfig.put("temperature", 0.7);
                generationConfig.put("maxOutputTokens", 2048);
                body.put("generationConfig", generationConfig);

                URL url = new URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "gemini-3.8-flash:generateContent");

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-goog-api-key", apiKey.trim());

                byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(request);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String response = readStream(stream);

                if (status < 200 || status >= 300) {
                    String detail = response;
                    try {
                        JSONObject error = new JSONObject(response);
                        detail = error.optJSONObject("error") != null
                                ? error.getJSONObject("error").optString("message", response)
                                : response;
                    } catch (Exception ignored) {
                    }
                    throw new Exception("Gemini API (" + status + "): " + detail);
                }

                JSONObject root = new JSONObject(response);
                JSONArray candidates = root.optJSONArray("candidates");

                if (candidates == null || candidates.length() == 0) {
                    throw new Exception("Gemini returned an empty response.");
                }

                JSONObject content = candidates.getJSONObject(0)
                        .optJSONObject("content");

                JSONArray parts = content == null
                        ? null : content.optJSONArray("parts");

                if (parts == null || parts.length() == 0) {
                    throw new Exception("Gemini response did not contain text.");
                }

                String raw = parts.getJSONObject(0).optString("text", "").trim();
                if (raw.isEmpty()) {
                    throw new Exception("Gemini returned an empty message.");
                }

                String reply = raw;

                try {
                    String jsonText = raw
                            .replaceFirst("^```json\\s*", "")
                            .replaceFirst("^```\\s*", "")
                            .replaceFirst("\\s*```$", "")
                            .trim();

                    JSONObject result = new JSONObject(jsonText);
                    reply = result.optString("reply", raw).trim();

                    JSONArray newMemories = result.optJSONArray("memories");
                    if (newMemories != null) {
                        for (int i = 0; i < newMemories.length(); i++) {
                            saveMemory(app, newMemories.optString(i));
                        }
                    }
                } catch (Exception ignored) {
                    reply = raw;
                }

                if (reply.isEmpty()) reply = raw;

                addMessage(app, "model", reply);
                final String finalReply = reply;

                android.os.Handler handler =
                        new android.os.Handler(app.getMainLooper());
                handler.post(() -> callback.onReply(finalReply));

            } catch (Exception e) {
                removeLastMessage(app, "user", cleanMessage);
                String error = e.getMessage() == null
                        ? "Unable to contact Gemini." : e.getMessage();

                android.os.Handler handler =
                        new android.os.Handler(app.getMainLooper());
                handler.post(() -> callback.onError(error));

            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static JSONArray buildContents(JSONArray history) throws Exception {
        JSONArray contents = new JSONArray();
        int start = Math.max(0, history.length() - 30);

        for (int i = start; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;

            String role = "model".equals(item.optString("role"))
                    ? "model" : "user";
            String text = item.optString("text", "");
            if (text.trim().isEmpty()) continue;

            if (contents.length() > 0) {
                JSONObject previous = contents.optJSONObject(contents.length() - 1);

                if (previous != null && role.equals(previous.optString("role"))) {
                    JSONArray previousParts = previous.optJSONArray("parts");
                    if (previousParts != null) {
                        previousParts.put(new JSONObject().put("text", text));
                        continue;
                    }
                }
            }

            JSONObject entry = new JSONObject();
            entry.put("role", role);

            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", text));
            entry.put("parts", parts);

            contents.put(entry);
        }

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
