package xyz.mashtoolz.wtz.features.tts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javazoom.jl.decoder.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig.TTSVoice;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;

public class ShoutTTS {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-ShoutTTS");

    private static final String[] API_URLS = {
            "https://api16-normal-c-useast1a.tiktokv.com/media/api/text/speech/invoke/",
            "https://api16-normal-useast5.us.tiktokv.com/media/api/text/speech/invoke/",
            "https://api19-normal-c-useast1a.tiktokv.com/media/api/text/speech/invoke/",
            "https://api16-normal-v6.tiktokv.com/media/api/text/speech/invoke/",
            "https://api16-normal-c-useast2a.tiktokv.com/media/api/text/speech/invoke/",
            "https://api16-normal-c-alisg.tiktokv.com/media/api/text/speech/invoke/"
    };
    private static volatile String workingUrl = null;
    private static final String USER_AGENT = "com.zhiliaoapp.musically/2022600030 (Linux; U; Android 7.1.2; es_ES; SM-G988N; Build/NRD90M;tt-ok/3.12.13.1)";

    private static volatile boolean initializing = false;
    private static volatile SourceDataLine activeLine = null;
    private static String lastMessage = "";
    private static long lastMessageTime = 0;
    private static final Queue<SpeechRequest> speechQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean queueRunning = new AtomicBoolean(false);
    private static final AtomicBoolean skipCurrent = new AtomicBoolean(false);

    public static void onTokenChanged() {
        workingUrl = null;
        if (initializing) return;
        String sessionId = WTZClient.CONFIG.shoutTTSToken;
        if (sessionId.isBlank()) return;

        initializing = true;
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String query = "?text_speaker=en_us_002&req_text=test&speaker_map_type=0&aid=1233";

                for (String baseUrl : API_URLS) {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + query))
                                .header("User-Agent", USER_AGENT)
                                .header("Cookie", "sessionid=" + sessionId)
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        JsonObject json = new Gson().fromJson(response.body(), JsonObject.class);

                        if (json.get("status_code").getAsInt() == 0) {
                            workingUrl = baseUrl;
                            WTZClient.client().execute(() -> ChatHelper.send("Shout TTS Ready", 0x9146FF));
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
                WTZClient.client().execute(() -> ChatHelper.sendError("Shout TTS: Invalid session ID — relog to fix"));
            } finally {
                initializing = false;
            }
        });
    }

    public static void trySpeak(String message) {
        if (!WTZClient.CONFIG.shoutTTSEnabled) return;

        String raw = message.strip();
        int idx = raw.indexOf("shouts:");
        if (idx == -1) return;

        String text = raw.substring(idx + "shouts:".length()).strip();
        if (text.isEmpty()) return;

        text = text.replaceAll("\n\\S+\\s?", " ").replaceAll("§.", "").strip();
        if (text.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (text.equals(lastMessage) && now - lastMessageTime < 1000) return;
        lastMessage = text;
        lastMessageTime = now;

        enqueueSpeech(new SpeechRequest(text, null));
    }

    public static void previewVoice() {
        TTSVoice voice = WTZClient.CONFIG.shoutTTSVoice;
        if (voice == TTSVoice.RANDOM) return;
        enqueueSpeech(new SpeechRequest("I love Wynncraft", voice.getId()));
    }

    private static void enqueueSpeech(SpeechRequest request) {
        speechQueue.add(request);
        startQueueWorker();
    }

    private static void startQueueWorker() {
        if (!queueRunning.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(ShoutTTS::drainQueue);
    }

    private static void drainQueue() {
        try {
            SpeechRequest request;
            while ((request = speechQueue.poll()) != null) {
                skipCurrent.set(false);
                if (request.voice() == null) {
                    speakNow(request.text());
                } else {
                    speakNow(request.text(), request.voice());
                }
            }
        } finally {
            queueRunning.set(false);
            if (!speechQueue.isEmpty()) {
                startQueueWorker();
            }
        }
    }

    private static void speakNow(String text) {
        TTSVoice configVoice = WTZClient.CONFIG.shoutTTSVoice;
        String voice;
        if (configVoice == TTSVoice.RANDOM) {
            TTSVoice[] voices = TTSVoice.values();
            voice = voices[1 + ThreadLocalRandom.current().nextInt(voices.length - 1)].getId();
        } else {
            voice = configVoice.getId();
        }
        speakNow(text, voice);
    }

    private static void speakNow(String text, String voice) {
        try {
            if (workingUrl == null) {
                logInvalidSession();
                return;
            }

            String sessionId = WTZClient.CONFIG.shoutTTSToken;
            if (sessionId.isBlank()) return;

            String reqText = URLEncoder.encode(
                    text.replace("+", "plus").replace("&", "and"),
                    StandardCharsets.UTF_8
            );

            String url = workingUrl + "?text_speaker=" + voice
                    + "&req_text=" + reqText
                    + "&speaker_map_type=0&aid=1233";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", "sessionid=" + sessionId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject json = new Gson().fromJson(response.body(), JsonObject.class);

            if (json.get("status_code").getAsInt() != 0) {
                logInvalidSession();
                return;
            }

            String b64 = json.getAsJsonObject("data").get("v_str").getAsString();
            byte[] mp3Bytes = Base64.getDecoder().decode(b64);

            if (skipCurrent.getAndSet(false)) return;

            playAudio(mp3Bytes);
        } catch (Exception e) {
            logInvalidSession();
            LOGGER.warn("Shout TTS failure", e);
        }
    }

    private static float getVolume() {
        return WTZClient.CONFIG.shoutTTSVolume / 100f;
    }

    public static void stopPlayback() {
        if (queueRunning.get()) {
            skipCurrent.set(true);
        }

        SourceDataLine line = activeLine;
        if (line != null) {
            line.stop();
            line.close();
            activeLine = null;
        }
    }

    private static void playAudio(byte[] mp3Bytes) {
        try {
            float volume = getVolume();
            if (volume <= 0) return;

            Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3Bytes));
            Decoder decoder = new Decoder();

            Header frame = bitstream.readFrame();
            if (frame == null) return;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
            AudioFormat format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);

            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
            activeLine = line;

            do {
                if (activeLine != line) break;

                short[] samples = output.getBuffer();
                int len = output.getBufferLength();

                byte[] buf = new byte[len * 2];
                for (int i = 0; i < len; i++) {
                    short s = (short) (samples[i] * volume);
                    buf[i * 2] = (byte) s;
                    buf[i * 2 + 1] = (byte) (s >> 8);
                }
                line.write(buf, 0, len * 2);

                bitstream.closeFrame();
                frame = bitstream.readFrame();
                if (frame != null) {
                    output = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                }
            } while (frame != null);

            if (activeLine == line) {
                line.drain();
                line.close();
                activeLine = null;
            }
            bitstream.close();
        } catch (Exception e) {
            LOGGER.warn("Shout TTS failure", e);
        }
    }

    private static void logInvalidSession() {
        WTZClient.client().execute(() -> ChatHelper.sendInfo("Shout TTS: Invalid session ID — relog to fix"));
    }

    private record SpeechRequest(String text, String voice) {
    }
}
