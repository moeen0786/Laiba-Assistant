package com.laiba.assistant;

public final class GeminiApiConfig {

    public static final String CHAT_MODEL =
        "gemini-3.8-flash";

    public static final String LIVE_MODEL =
        "gemini-3.8-live-extended-thinking";

    public static final String CHAT_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + CHAT_MODEL + ":streamGenerateContent?alt=sse";

    public static final String LIVE_WEBSOCKET_ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/"
        + "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";

    private GeminiApiConfig() {}
}
