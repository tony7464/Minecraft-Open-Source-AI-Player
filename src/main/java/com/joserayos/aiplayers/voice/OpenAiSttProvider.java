package com.joserayos.aiplayers.voice;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI-compatible {@code POST /v1/audio/transcriptions} (OpenAI, Groq, speaches/faster-whisper-server, LocalAI...).
 * base_url should include the /v1 part, like the chat profiles.
 */
public class OpenAiSttProvider implements SttProvider {
    protected final VoiceConfig config;
    protected final HttpClient http;

    public OpenAiSttProvider(VoiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    protected URI endpoint() {
        String base = config.baseUrl().endsWith("/") ? config.baseUrl().substring(0, config.baseUrl().length() - 1) : config.baseUrl();
        return URI.create(base + "/audio/transcriptions");
    }

    protected Multipart body(byte[] wav, String language) {
        Multipart m = new Multipart().file("file", "speech.wav", "audio/wav", wav).field("model", config.model()).field("response_format", "json");
        if (!language.isBlank()) m.field("language", language);
        return m;
    }

    @Override
    public String transcribe(byte[] wav, String language) throws IOException, InterruptedException {
        Multipart m = body(wav, language);
        HttpRequest.Builder req = HttpRequest.newBuilder(endpoint()).timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", m.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(m.build()));
        if (!config.apiKey().isBlank()) req.header("Authorization", "Bearer " + config.apiKey());
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            String body = res.body() == null ? "" : res.body();
            throw new IOException("speech-to-text HTTP " + res.statusCode() + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
        }
        var json = JsonParser.parseString(res.body()).getAsJsonObject();
        return json.has("text") ? json.get("text").getAsString().strip() : "";
    }
}
