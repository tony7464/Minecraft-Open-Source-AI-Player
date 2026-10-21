package com.joserayos.aiplayers.voice;

import java.net.URI;

/** whisper.cpp's bundled HTTP server: {@code POST /inference} with a multipart "file". */
public class WhisperCppProvider extends OpenAiSttProvider {
    public WhisperCppProvider(VoiceConfig config) {
        super(config);
    }

    @Override
    protected URI endpoint() {
        String base = config.baseUrl().endsWith("/") ? config.baseUrl().substring(0, config.baseUrl().length() - 1) : config.baseUrl();
        return URI.create(base + "/inference");
    }

    @Override
    protected Multipart body(byte[] wav, String language) {
        Multipart m = new Multipart().file("file", "speech.wav", "audio/wav", wav).field("response_format", "json").field("temperature", "0.0");
        if (!language.isBlank()) m.field("language", language);
        return m;
    }
}
