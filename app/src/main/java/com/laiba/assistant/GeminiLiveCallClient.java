package com.laiba.assistant;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.util.Base64;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GeminiLiveCallClient {

    public interface Callback {
        void onStatus(String status);
        void onTranscript(String text, boolean fromLaiba);
        void onError(String error);
    }

    private static final String MODEL =
        "gemini-3.8-live-extended-thinking";

    private static final String ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/" +
        "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

    private final Callback callback;
    private final OkHttpClient http;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean playbackRunning = new AtomicBoolean(false);
    private final BlockingQueue<byte[]> playbackQueue =
        new LinkedBlockingQueue<>();
    private Thread playbackThread;

    private WebSocket socket;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread microphoneThread;
    private AudioFocusRequest focusRequest;
    private AudioManager audioManager;
    private boolean audioFocusGranted = false;

    public GeminiLiveCallClient(Callback callback) {
        this.callback = callback;
        this.http = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    }

    public void start(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("Gemini API key is missing.");
            return;
        }

        if (!running.compareAndSet(false, true)) return;

        try {
            android.net.Uri uri = android.net.Uri.parse(ENDPOINT)
                .buildUpon()
                .appendQueryParameter("key", apiKey.trim())
                .build();

            Request request = new Request.Builder()
                .url(uri.toString())
                .build();

            callback.onStatus("Connecting to Gemini Live...");

            socket = http.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    sendSetup();
                }

                @Override
                public void onMessage(WebSocket webSocket, String message) {
                    handleServerMessage(message);
                }

                @Override
                public void onFailure(
                        WebSocket webSocket,
                        Throwable error,
                        Response response) {
                    if (running.get()) {
                        callback.onError(
                            error.getMessage() == null
                                ? "Gemini Live connection failed."
                                : error.getMessage()
                        );
                    }
                    close();
                }

                @Override
                public void onClosed(
                        WebSocket webSocket, int code, String reason) {
                    if (running.get()) {
                        callback.onStatus("Call disconnected.");
                    }
                    close();
                }
            });

        } catch (Exception error) {
            close();
            callback.onError("Could not start Gemini Live.");
        }
    }

    private void sendSetup() {
        try {
            JSONObject setup = new JSONObject();
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

            socket.send(new JSONObject()
                .put("setup", setup).toString());

        } catch (Exception error) {
            callback.onError("Gemini Live setup failed.");
            close();
        }
    }

    private void handleServerMessage(String raw) {
        try {
            JSONObject message = new JSONObject(raw);

            if (message.has("setupComplete")) {
                ready.set(true);
                callback.onStatus("Connected. Laiba is listening.");
                startAudio();
                return;
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

            String status = server.optString("interactionStatus", "");

            if ("IN_PROGRESS".equals(status)) {
                callback.onStatus("Laiba is thinking...");
            } else if ("IDLE".equals(status)) {
                callback.onStatus("Listening...");
            }

        } catch (Exception error) {
            if (running.get()) {
                callback.onError("Could not process Gemini Live response.");
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
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) {
                            close();
                        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stopPlayback();
                        }
                    })
                    .build();

                audioFocusGranted =
                    audioManager.requestAudioFocus(focusRequest) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                audioFocusGranted =
                    audioManager.requestAudioFocus(
                        change -> {
                            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                                close();
                            } else if (change ==
                                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                stopPlayback();
                            }
                        },
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
                byte[] buffer = new byte[3200];

                while (running.get()) {
                    if (muted.get()) {
                        try { Thread.sleep(20); }
                        catch (InterruptedException ignored) { break; }
                        continue;
                    }

                    int count = recorder.read(
                        buffer, 0, buffer.length,
                        AudioRecord.READ_BLOCKING
                    );

                    if (count > 0 && running.get() && ready.get()) {
                        try {
                            JSONObject audio = new JSONObject();
                            audio.put("mimeType", "audio/pcm;rate=16000");
                            audio.put("data", Base64.encodeToString(
                                java.util.Arrays.copyOf(buffer, count),
                                Base64.NO_WRAP));

                            socket.send(new JSONObject()
                                .put("realtimeInput",
                                    new JSONObject().put("audio", audio))
                                .toString());
                        } catch (Exception ignored) {
                            // Connection callbacks handle socket failures.
                        }
                    }
                }
            }, "Laiba-Gemini-Microphone");

            microphoneThread.start();

        } catch (Exception error) {
            callback.onError("Microphone/audio initialization failed: " +
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
                    if (current != null && running.get()) {
                        current.write(
                            pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
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
        if (!running.get() || player == null || pcm.length == 0) return;

        // Keep memory bounded if the network delivers audio faster than playback.
        if (playbackQueue.size() > 40) {
            playbackQueue.poll();
        }
        playbackQueue.offer(pcm);
    }

    private synchronized void stopPlayback() {
        playbackQueue.clear();

        if (player != null) {
            try {
                player.pause();
                player.flush();
                player.play();
            } catch (Exception ignored) {}
        }
    }

    public void setMuted(boolean value) {
        muted.set(value);
        callback.onStatus(value ? "Microphone muted." : "Listening...");
    }

    public boolean isMuted() {
        return muted.get();
    }

    public void close() {
        if (!running.getAndSet(false)) return;

        ready.set(false);

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
                } else {
                    audioManager.abandonAudioFocus(null);
                }
            } catch (Exception ignored) {}

            audioFocusGranted = false;
            focusRequest = null;
            audioManager = null;
        }

        callback.onStatus("Call ended.");
    }
}
