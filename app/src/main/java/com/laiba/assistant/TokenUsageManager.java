package com.laiba.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public final class TokenUsageManager {

    private static final String PREFS = "laiba_token_usage_v1";
    private static final String RECORDS = "records";
    private static final int LIMIT = 1000;
    private static final String MODEL = "gemma-4-31b-it";

    private TokenUsageManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void record(
            Context context,
            JSONObject metadata,
            boolean success,
            int attempts) {

        try {
            JSONArray old = records(context);
            JSONArray updated = new JSONArray();

            JSONObject item = new JSONObject();
            item.put("timestamp", System.currentTimeMillis());
            item.put("model", MODEL);
            item.put("success", success);
            item.put("attempts", Math.max(0, attempts));

            // -1 means Gemini did not provide that count.
            item.put("inputTokens", metadata == null
                    ? -1 : metadata.optInt("promptTokenCount", -1));

            item.put("outputTokens", metadata == null
                    ? -1 : metadata.optInt("candidatesTokenCount", -1));

            item.put("totalTokens", metadata == null
                    ? -1 : metadata.optInt("totalTokenCount", -1));

            item.put("cachedTokens", metadata == null
                    ? -1 : metadata.optInt("cachedContentTokenCount", -1));

            item.put("thoughtsTokens", metadata == null
                    ? -1 : metadata.optInt("thoughtsTokenCount", -1));

            int start = Math.max(0, old.length() - LIMIT + 1);

            for (int i = start; i < old.length(); i++) {
                JSONObject previous = old.optJSONObject(i);
                if (previous != null) updated.put(previous);
            }

            updated.put(item);

            prefs(context).edit()
                    .putString(RECORDS, updated.toString())
                    .apply();

        } catch (Exception ignored) {
        }
    }

    public static synchronized JSONArray records(Context context) {
        try {
            return new JSONArray(
                    prefs(context).getString(RECORDS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized JSONObject summary(Context context) {
        JSONObject result = new JSONObject();

        long now = System.currentTimeMillis();

        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long dayStart = day.getTimeInMillis();

        Calendar week = (Calendar) day.clone();
        int weekday = week.get(Calendar.DAY_OF_WEEK);
        int offset = (weekday + 5) % 7;
        week.add(Calendar.DAY_OF_MONTH, -offset);
        long weekStart = week.getTimeInMillis();

        Calendar month = (Calendar) day.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        long monthStart = month.getTimeInMillis();

        long dayInput = 0, dayOutput = 0, dayTotal = 0;
        long weekInput = 0, weekOutput = 0, weekTotal = 0;
        long monthInput = 0, monthOutput = 0, monthTotal = 0;

        int requests = 0, failures = 0, successful = 0;
        int unknownUsage = 0;

        JSONArray items = records(context);

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            long timestamp = item.optLong("timestamp", 0);
            boolean ok = item.optBoolean("success", false);

            requests++;
            if (ok) successful++;
            else failures++;

            long input = item.optLong("inputTokens", -1);
            long output = item.optLong("outputTokens", -1);
            long total = item.optLong("totalTokens", -1);

            if (total < 0) {
                unknownUsage++;
                continue;
            }

            if (timestamp >= dayStart) {
                dayInput += Math.max(0, input);
                dayOutput += Math.max(0, output);
                dayTotal += total;
            }

            if (timestamp >= weekStart) {
                weekInput += Math.max(0, input);
                weekOutput += Math.max(0, output);
                weekTotal += total;
            }

            if (timestamp >= monthStart) {
                monthInput += Math.max(0, input);
                monthOutput += Math.max(0, output);
                monthTotal += total;
            }
        }

        try {
            result.put("requests", requests);
            result.put("successful", successful);
            result.put("failures", failures);
            result.put("unknownUsage", unknownUsage);

            result.put("dayInput", dayInput);
            result.put("dayOutput", dayOutput);
            result.put("dayTotal", dayTotal);

            result.put("weekInput", weekInput);
            result.put("weekOutput", weekOutput);
            result.put("weekTotal", weekTotal);

            result.put("monthInput", monthInput);
            result.put("monthOutput", monthOutput);
            result.put("monthTotal", monthTotal);

            result.put("storedRecords", items.length());

        } catch (Exception ignored) {
        }

        return result;
    }

    public static synchronized void clear(Context context) {
        prefs(context).edit().remove(RECORDS).apply();
    }
}
