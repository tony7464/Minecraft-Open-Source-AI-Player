package com.joserayos.aiplayers.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joserayos.aiplayers.llm.FakeHttpServer;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoiceTest {
    static VoiceConfig config(String provider, String baseUrl, String key) {
        return new VoiceConfig(true, provider, baseUrl, "whisper-1", key, "en", true, 15, 10, "Ava");
    }

    @Test
    void wavHeader() {
        byte[] wav = WavEncoder.encode(new byte[3200], 16_000, 1, 16);
        assertEquals(44 + 3200, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4));
        assertEquals("WAVE", new String(wav, 8, 4));
        assertEquals("data", new String(wav, 36, 4));
        assertEquals(0.0, WavEncoder.rms16(new byte[100]));
    }

    @Test
    void defaultConfigParsesAndIsOff() {
        VoiceConfig c = VoiceConfig.parse(VoiceConfig.DEFAULT_JSON, k -> null);
        assertFalse(c.enabled());
        assertEquals("whisper_cpp", c.provider());
        VoiceConfig withKey = VoiceConfig.parse("{\"api_key\":\"${env:GROQ_API_KEY}\",\"max_seconds\":999}", Map.of("GROQ_API_KEY", "abc")::get);
        assertEquals("abc", withKey.apiKey());
        assertEquals(60, withKey.maxSeconds());
    }

    @Test
    void defaultTarget() {
        VoiceConfig c = config("openai_compat", "http://x", "");
        assertEquals("@Ava get wood", c.withDefaultTarget(" get wood "));
        assertEquals("@Bo hi", c.withDefaultTarget("@Bo hi"));
    }

    @Test
    void openAiCompatibleRequest() throws Exception {
        try (FakeHttpServer server = new FakeHttpServer().respond(200, "{\"text\":\" follow me \"}")) {
            String text = SttProvider.create(config("openai_compat", server.baseUrl() + "/v1", "secret")).transcribe(WavEncoder.encode(new byte[10], 16000, 1, 16), "en");
            assertEquals("follow me", text);
            var req = server.requests.getFirst();
            assertEquals("/v1/audio/transcriptions", req.path());
            assertEquals("Bearer secret", req.headers().get("Authorization").getFirst());
            assertTrue(req.body().contains("name=\"model\"") && req.body().contains("whisper-1"));
            assertTrue(req.body().contains("filename=\"speech.wav\""));
        }
    }

    @Test
    void whisperCppRequestAndErrors() throws Exception {
        try (FakeHttpServer server = new FakeHttpServer().respond(200, "{\"text\":\"stop\"}")) {
            assertEquals("stop", SttProvider.create(config("whisper_cpp", server.baseUrl(), "")).transcribe(new byte[44], ""));
            assertEquals("/inference", server.requests.getFirst().path());
            assertFalse(server.requests.getFirst().headers().containsKey("Authorization"));
        }
        try (FakeHttpServer server = new FakeHttpServer().respond(500, "boom")) {
            IOException e = assertThrows(IOException.class, () -> SttProvider.create(config("whisper_cpp", server.baseUrl(), "")).transcribe(new byte[44], ""));
            assertTrue(e.getMessage().contains("500"));
        }
        assertThrows(IllegalArgumentException.class, () -> SttProvider.create(config("nope", "http://x", "")));
    }
}
