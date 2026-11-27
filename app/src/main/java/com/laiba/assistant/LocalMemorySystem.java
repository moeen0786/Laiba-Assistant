package com.laiba.assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocalMemorySystem {

    private static final int HISTORY_LIMIT = 500;
    private static final int MEMORY_LIMIT = 300;
    private static final double MIN_SCORE = 0.68;

    private LocalMemorySystem() {}

    private static final class Candidate {
        final String text;
        final int source;
        final double score;

        Candidate(String text, int source, double score) {
            this.text = text;
            this.source = source;
            this.score = score;
        }
    }

    public static String findAnswer(Context context, String question) {
        if (context == null || question == null) return null;

        String query = question.trim();
        if (query.isEmpty()) return null;

        Set<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) return null;

        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Search all saved memory facts locally.
        JSONArray memories = LaibaChatEngine.memory(context);
        int memoryStart = Math.max(0, memories.length() - MEMORY_LIMIT);

        for (int i = memoryStart; i < memories.length(); i++) {
            String fact = memories.optString(i, "").trim();
            addCandidate(
                    candidates, seen, queryTerms,
                    fact, 0, i - memoryStart,
                    memories.length() - memoryStart);
        }

        // Search all saved chat messages locally.
        JSONArray history = LaibaChatEngine.history(context);
        int historyStart = Math.max(0, history.length() - HISTORY_LIMIT);

        for (int i = historyStart; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;

            String role = item.optString("role", "");
            if (!"user".equals(role) && !"model".equals(role)) continue;

            String message = item.optString("text", "").trim();

            int source = "user".equals(role) ? 1 : 2;

            addCandidate(
                    candidates, seen, queryTerms,
                    message, source, i - historyStart,
                    history.length() - historyStart);
        }

        if (candidates.isEmpty()) return null;

        Candidate best = null;
        Candidate second = null;

        for (Candidate candidate : candidates) {
            if (best == null || candidate.score > best.score) {
                second = best;
                best = candidate;
            } else if (second == null
                    || candidate.score > second.score) {
                second = candidate;
            }
        }

        if (best == null || best.score < MIN_SCORE) return null;

        // Avoid guessing when two different memories match equally well.
        if (second != null
                && Math.abs(best.score - second.score) < 0.025
                && !best.text.equalsIgnoreCase(second.text)) {
            return null;
        }

        String answer = best.text;

        if (answer.length() > 1200) {
            answer = answer.substring(0, 1200).trim() + "...";
        }

        if (best.source == 0) {
            return "مجھے اپنی محفوظ میموری میں یہ معلومات ملیں:\n"
                    + answer;
        }

        if (best.source == 1) {
            return "آپ نے ہماری پچھلی گفتگو میں یہ بات کہی تھی:\n"
                    + answer;
        }

        return "ہماری پچھلی گفتگو میں یہ جواب موجود ہے:\n"
                + answer;
    }

    private static void addCandidate(
            List<Candidate> output,
            Set<String> seen,
            Set<String> queryTerms,
            String text,
            int source,
            int position,
            int total) {

        if (text == null || text.trim().length() < 3) return;

        String key = text.toLowerCase(Locale.ROOT).trim();
        if (!seen.add(key)) return;

        Set<String> candidateTerms = terms(text);
        if (candidateTerms.isEmpty()) return;

        int matches = 0;

        for (String term : queryTerms) {
            if (candidateTerms.contains(term)) matches++;
        }

        if (matches == 0) return;

        double coverage = (double) matches / queryTerms.size();

        if (coverage < MIN_SCORE) return;

        double recency = total <= 1
                ? 0.0
                : ((double) position / (total - 1)) * 0.01;

        double phraseBonus = key.contains(
                queryTerms.iterator().next()) ? 0.01 : 0.0;

        double score = coverage + recency + phraseBonus;

        output.add(new Candidate(text, source, score));
    }

    private static Set<String> terms(String text) {
        Set<String> stop = new HashSet<>(Arrays.asList(
                "what", "which", "when", "where", "who", "why", "how",
                "did", "does", "do", "is", "are", "was", "were",
                "the", "a", "an", "to", "of", "in", "on", "at",
                "for", "and", "or", "but", "with", "about", "from",
                "tell", "show", "please", "me", "you", "your",
                "my", "mine", "our", "ours", "i", "we", "us",
                "have", "has", "had", "been", "be", "being",
                "previous", "earlier", "before", "last", "time",
                "chat", "conversation", "history", "remember",
                "said", "say", "told", "ask", "asked",
                "use", "using", "set", "tell", "was",
                "میرا", "میری", "میرے", "آپ", "تم", "میں",
                "ہم", "ہماری", "ہماری", "مجھے", "آپکا",
                "آپکی", "کیا", "کی", "کے", "کا", "ہیں",
                "ہے", "تھا", "تھی", "تھے", "نے", "کو",
                "سے", "پر", "اور", "بتائیں", "بتاؤ",
                "یاد", "پہلے", "پچھلے", "پچھلی", "گزشتہ"
        ));

        Set<String> result = new HashSet<>();

        String[] words = text.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+");

        for (String word : words) {
            if (word.length() < 2 || stop.contains(word)) continue;

            result.add(canonical(word));
        }

        return result;
    }

    private static String canonical(String word) {
        switch (word) {
            case "phone":
            case "mobile":
            case "handset":
            case "device":
                return "device";

            case "timeouts":
            case "timelimit":
                return "timeout";

            case "repository":
            case "repo":
            case "branch":
                return "project";

            default:
                return word;
        }
    }

    private static boolean isRecallIntent(String message) {
        String q = " " + message.toLowerCase(Locale.ROOT) + " ";

        String[] markers = {
                " my ", " mine ", " i ", " we ", " our ",
                " previous ", " earlier ", " before ",
                " last time ", " remember ", " history ",
                " what did i ", " what did we ",
                " what did you ", " what was i ",
                " what have we ", " do you remember ",
                " میرا ", " میری ", " میرے ",
                " ہماری ", " ہمارا ", " ہمارے ",
                " پچھلے ", " پچھلی ", " پچھلا ",
                " پہلے ", " گزشتہ ", " یاد ہے ",
                " یاد کرو ", " میں نے ", " ہم نے ",
                " ہم نے کیا ", " میں نے کیا ",
                " محفوظ کیا ", " سیو کیا "
        };

        for (String marker : markers) {
            if (q.contains(marker)) return true;
        }

        return false;
    }
}
