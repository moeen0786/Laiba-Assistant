package com.laiba.assistant;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GeminiLiveCallClient {

    public interface Callback {
        void onStatus(String status);
        void onTranscript(String text, boolean fromLaiba);
        void onError(String error);
    }

    private static final String MODEL =
        GeminiApiConfig.LIVE_MODEL;

    private static final String ENDPOINT =
        GeminiApiConfig.LIVE_WEBSOCKET_ENDPOINT;

    private final Callback callback;
    private final OkHttpClient http;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean playbackRunning = new AtomicBoolean(false);
    private final BlockingQueue<byte[]> playbackQueue =
        new ArrayBlockingQueue<>(40);
    private final Object playbackLock = new Object();
    private Thread playbackThread;

    private WebSocket socket;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread microphoneThread;
    private AudioFocusRequest focusRequest;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private volatile boolean audioFocusGranted = false;
    private volatile boolean errorReported = false;
    private volatile String lastServerMessage = "No server response received.";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable setupTimeout = () -> {
        if (running.get() && !ready.get()) {
            String last = lastServerMessage;
            if (last.length() > 900) {
                last = last.substring(0, 900);
            }

            String diagnostic =
                "GEMINI LIVE SETUP DIAGNOSTIC" +
                "\\nModel: " + MODEL +
                "\\nAPI: v1alpha" +
                "\\nStage: WebSocket connected, setupComplete missing" +
                "\\nSocket: " +
                (socket == null ? "Unavailable" : "Still connected") +
                "\\nLast server response: " + last +
                "\\nPossible causes: model access, unsupported setup " +
                "configuration, API key/project eligibility, or server delay.";

            reportError(diagnostic);
            close();
        }
    };

    public GeminiLiveCallClient(Callback callback) {
        this.callback = callback;
        this.http = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    }

    public void start(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            reportError("Gemini API key is missing.");
            return;
        }

        if (!running.compareAndSet(false, true)) return;
        errorReported = false;

        try {
            HttpUrl authenticatedEndpoint = HttpUrl.get(
                ENDPOINT.replaceFirst("^wss://", "https://"))
                .newBuilder()
                .addQueryParameter("key", apiKey.trim())
                .build();

            Request request = new Request.Builder()
                .url(authenticatedEndpoint)
                .build();

            callback.onStatus("Connecting to Gemini Live...");

            socket = http.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    lastServerMessage = "WebSocket opened; waiting for Gemini setup response.";
                    if (!running.get()) {
                        webSocket.close(1000, "Call already ended");
                        return;
                    }
                    mainHandler.removeCallbacks(setupTimeout);
                    mainHandler.postDelayed(setupTimeout, 20000);
                    callback.onStatus("Connected to server. Configuring Gemini...");
                    sendSetup();
                }

                @Override
                public void onMessage(WebSocket webSocket, String message) {
                    lastServerMessage = message;
                    handleServerMessage(message);
                }

                @Override
                public void onFailure(
                        WebSocket webSocket,
                        Throwable error,
                        Response response) {
                    if (running.get()) {
                        StringBuilder detail = new StringBuilder(
                            "Gemini Live connection failed");

                        if (response != null) {
                            detail.append(" (HTTP ")
                                .append(response.code())
                                .append("): ")
                                .append(response.message());

                            try {
                                if (response.body() != null) {
                                    String serverDetail =
                                        response.body().string();

                                    if (serverDetail != null &&
                                            !serverDetail.isEmpty()) {
                                        if (serverDetail.length() > 1200) {
                                            serverDetail =
                                                serverDetail.substring(0, 1200);
                                        }
                                        detail.append(" | Server: ")
                                            .append(serverDetail);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        if (error.getMessage() != null &&
                                !error.getMessage().isEmpty()) {
                            detail.append(" — ").append(error.getMessage());
                        }

                        reportError(detail.toString());
                    }
                    close();
                }

                @Override
                public void onClosed(
                        WebSocket webSocket, int code, String reason) {
                    if (running.get()) {
                        ready.set(false);
                        reportError(
                            "Gemini Live disconnected (" + code + "): " +
                            (reason == null || reason.isEmpty()
                                ? "Connection closed."
                                : reason));
                    }
                    close();
                }
            });

        } catch (Exception error) {
            String detail = error.getMessage();

            close();

            reportError(
                "Could not start Gemini Live: " +
                (detail == null || detail.trim().isEmpty()
                    ? error.getClass().getSimpleName()
                    : detail)
            );
        }
    }

    private void sendSetup() {
        try {
            JSONObject setup = new JSONObject();
            lastServerMessage = "Setup request sent for models/" + MODEL;
            setup.put("model", "models/" + MODEL);

            JSONObject generation = new JSONObject();
            generation.put("responseModalities",
                new JSONArray().put("AUDIO"));

            JSONObject speech = new JSONObject();
            speech.put("voiceConfig", new JSONObject()
                .put("prebuiltVoiceConfig",
                    new JSONObject().put("voiceName", "Aoede")));
            generation.put("speechConfig", speech);

            JSONObject thinking = new JSONObject();
            thinking.put("thinkingLevel", "HIGH");
            generation.put("thinkingConfig", thinking);

            setup.put("generationConfig", generation);
            setup.put("inputAudioTranscription", new JSONObject());
            setup.put("outputAudioTranscription", new JSONObject());

            JSONObject realtime = new JSONObject();
            realtime.put("automaticActivityDetection",
                new JSONObject()
                    .put("disabled", false)
                    .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                    .put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                    .put("silenceDurationMs", 500)
            );
            realtime.put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS");
            setup.put("realtimeInputConfig", realtime);

            if (socket == null || !socket.send(
                    new JSONObject().put("setup", setup).toString())) {
                throw new IllegalStateException(
                    "Gemini setup message could not be sent.");
            }

        } catch (Exception error) {
            reportError("Gemini Live setup failed: " +
                (error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage()));
            close();
        }
    }

    private void handleServerMessage(String raw) {
        try {
            JSONObject message = new JSONObject(raw);

            JSONObject apiError = message.optJSONObject("error");
            if (apiError != null) {
                int code = apiError.optInt("code", 0);
                String status = apiError.optString("status", "UNKNOWN");
                String detail = apiError.optString(
                    "message", "Unknown Gemini Live server error.");

                reportError(
                    "Gemini Live API error " + code +
                    " (" + status + "): " + detail);

                close();
                return;
            }

            if (!ready.get() && !message.has("setupComplete")) {
                String diagnostic = raw.length() > 700
                    ? raw.substring(0, 700)
                    : raw;

                callback.onStatus(
                    "Gemini setup response: " + diagnostic);
            }

            JSONObject goAway = message.optJSONObject("goAway");
            if (goAway != null) {
                String timeLeft = goAway.optString("timeLeft", "");
                callback.onStatus(
                    "Gemini Live session is ending. " +
                    (timeLeft.isEmpty() ? "Please reconnect." :
                        "Time left: " + timeLeft));
            }

            if (message.has("setupComplete")) {
                mainHandler.removeCallbacks(setupTimeout);
                ready.set(true);
                callback.onStatus("Connected. Laiba is listening.");
                startAudio();
                return;
            }

            // Read top-level interaction status independently of serverContent.
            String status = message.optString("interactionStatus", "");

            if ("IN_PROGRESS".equals(status)) {
                callback.onStatus("Laiba is thinking...");
            } else if ("IDLE".equals(status)) {
                callback.onStatus("Listening...");
            }

            JSONObject server = message.optJSONObject("serverContent");
            if (server == null) return;

            JSONObject input = server.optJSONObject("inputTranscription");
            if (input != null) {
                String text = input.optString("text", "");
                if (!text.isEmpty()) callback.onTranscript(text, false);
            }

            JSONObject output = server.optJSONObject("outputTranscription");
            if (output != null) {
                String text = output.optString("text", "");
                if (!text.isEmpty()) callback.onTranscript(text, true);
            }

            if (server.optBoolean("interrupted", false)) {
                stopPlayback();
            }

            JSONObject turn = server.optJSONObject("modelTurn");
            if (turn != null) {
                JSONArray parts = turn.optJSONArray("parts");
                if (parts != null) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.optJSONObject(i);
                        if (part == null) continue;

                        JSONObject inline = part.optJSONObject("inlineData");
                        if (inline == null) continue;

                        String encoded = inline.optString("data", "");
                        if (!encoded.isEmpty()) {
                            playAudio(Base64.decode(encoded, Base64.DEFAULT));
                        }
                    }
                }
            }

        } catch (Exception error) {
            if (running.get()) {
                reportError("Could not process Gemini Live response.");
            }
        }
    }

    private void startAudio() {
        if (!running.get() || !ready.get()) return;

        try {
            int inputRate = 16000;
            int outputRate = 24000;

            int inputBuffer = AudioRecord.getMinBufferSize(
                inputRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            int outputBuffer = AudioTrack.getMinBufferSize(
                outputRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            if (inputBuffer <= 0 || outputBuffer <= 0) {
                throw new IllegalStateException("Audio device unavailable.");
            }

            recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                inputRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(inputBuffer * 2, 4096)
            );

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Microphone initialization failed.");
            }

            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

            AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(outputRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

            player = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(outputBuffer * 2, 8192))
                .build();

            if (player.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("Audio output initialization failed.");
            }

            if (recorderContext == null) {
                throw new IllegalStateException("Audio context unavailable.");
            }

            audioManager = (AudioManager)
                recorderContext.getSystemService(
                    android.content.Context.AUDIO_SERVICE);

            if (audioManager == null) {
                throw new IllegalStateException("Audio manager unavailable.");
            }

            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            focusListener = change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    audioFocusGranted = false;
                    close();
                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                           change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    audioFocusGranted = false;
                    stopPlayback();
                    callback.onStatus("Audio temporarily paused...");
                } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    audioFocusGranted = true;
                    synchronized (playbackLock) {
                        if (running.get() && player != null) {
                            try {
                                player.play();
                            } catch (Exception ignored) {}
                        }
                    }
                    if (running.get()) {
                        callback.onStatus(
                            muted.get() ? "Microphone muted." : "Listening...");
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes focusAttributes =
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                focusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(focusAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();

                audioFocusGranted =
                    audioManager.requestAudioFocus(focusRequest) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                audioFocusGranted =
                    audioManager.requestAudioFocus(
                        focusListener,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }

            if (!audioFocusGranted) {
                throw new IllegalStateException("Audio focus unavailable.");
            }

            audioManager.setSpeakerphoneOn(true);

            player.play();
            recorder.startRecording();

            if (recorder.getRecordingState() !=
                    AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Microphone did not start.");
            }
            startPlaybackWorker();

            microphoneThread = new Thread(() -> {
                byte[] buffer = new byte[1280];

                while (running.get()) {
                    AudioRecord currentRecorder = recorder;
                    if (currentRecorder == null) break;

                    int count = currentRecorder.read(
                        buffer, 0, buffer.length,
                        AudioRecord.READ_BLOCKING
                    );

                    // Check recorder failures before mute/focus filtering.
                    if (count < 0) {
                        if (running.get()) {
                            reportError(
                                "Microphone stream stopped. Please reconnect.");
                            close();
                        }
                        break;
                    }

                    // Discard audio captured while muted or without focus.
                    if (muted.get() || !audioFocusGranted) {
                        continue;
                    }

                    if (count > 0 && running.get() && ready.get() &&
                            socket != null && !muted.get() &&
                            audioFocusGranted) {
                        try {
                            JSONObject audio = new JSONObject();
                            audio.put("mimeType", "audio/pcm;rate=16000");
                            audio.put("data", Base64.encodeToString(
                                java.util.Arrays.copyOf(buffer, count),
                                Base64.NO_WRAP));

                            boolean sent = socket != null && socket.send(
                                new JSONObject()
                                    .put("realtimeInput",
                                        new JSONObject().put("audio", audio))
                                    .toString());

                            if (!sent) {
                                if (running.get()) {
                                    reportError(
                                        "Gemini audio stream disconnected.");
                                    close();
                                }
                                break;
                            }
                        } catch (Exception error) {
                            if (running.get()) {
                                reportError(
                                    "Microphone streaming failed.");
                                close();
                            }
                            break;
                        }
                    }
                }
            }, "Laiba-Gemini-Microphone");

            microphoneThread.start();

        } catch (Exception error) {
            reportError("Microphone/audio initialization failed: " +
                error.getMessage());
            close();
        }
    }

    // Context is supplied by the Activity before starting audio.
    private android.content.Context recorderContext;

    public void setContext(android.content.Context context) {
        recorderContext = context.getApplicationContext();
    }

    private void startPlaybackWorker() {
        if (!playbackRunning.compareAndSet(false, true)) return;

        playbackThread = new Thread(() -> {
            while (running.get() && playbackRunning.get()) {
                try {
                    byte[] pcm = playbackQueue.take();
                    AudioTrack current = player;
                    if (current != null && running.get() &&
                            audioFocusGranted) {
                        synchronized (playbackLock) {
                            if (running.get() && audioFocusGranted &&
                                    current == player) {
                                current.write(
                                    pcm, 0, pcm.length,
                                    AudioTrack.WRITE_BLOCKING);
                            }
                        }
                    }
                } catch (InterruptedException interrupted) {
                    break;
                } catch (Exception ignored) {
                    // Playback can be interrupted when the user speaks.
                }
            }
        }, "Laiba-Live-Audio-Playback");

        playbackThread.start();
    }

    private void playAudio(byte[] pcm) {
        if (!running.get() || !audioFocusGranted ||
                player == null || pcm.length == 0) return;

        // Drop oldest audio when the bounded playback queue is full.
        if (!playbackQueue.offer(pcm)) {
            playbackQueue.poll();
            playbackQueue.offer(pcm);
        }
    }

    private synchronized void stopPlayback() {
        playbackQueue.clear();

        synchronized (playbackLock) {
            if (player != null) {
                try {
                    player.pause();
                    player.flush();
                    if (running.get() && audioFocusGranted) {
                        player.play();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public void setMuted(boolean value) {
        muted.set(value);
        callback.onStatus(value ? "Microphone muted." : "Listening...");
    }

    public boolean isMuted() {
        return muted.get();
    }

    private void reportError(String message) {
        errorReported = true;
        callback.onError(message);
    }

    public void close() {
        if (!running.getAndSet(false)) return;

        ready.set(false);
        mainHandler.removeCallbacks(setupTimeout);

        if (microphoneThread != null) {
            microphoneThread.interrupt();
            microphoneThread = null;
        }

        playbackRunning.set(false);
        playbackQueue.clear();
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        if (socket != null) {
            socket.close(1000, "Call ended");
            socket = null;
        }

        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            recorder = null;
        }

        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }

        if (audioManager != null) {
            try {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else if (focusListener != null) {
                    audioManager.abandonAudioFocus(focusListener);
                }
            } catch (Exception ignored) {}

            audioFocusGranted = false;
            focusRequest = null;
            focusListener = null;
            audioManager = null;
        }

        if (!errorReported) {
            callback.onStatus("Call ended.");
        }
    }
}
