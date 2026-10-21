package com.joserayos.aiplayers.voice;

import java.io.IOException;

/** Speech-to-text backend. Implementations must be thread-safe; they run off the game thread. */
public interface SttProvider {
    /** Returns the transcript (may be empty). */
    String transcribe(byte[] wav, String language) throws IOException, InterruptedException;

    static SttProvider create(VoiceConfig c) {
        return switch (c.provider()) {
            case "whisper_cpp" -> new WhisperCppProvider(c);
            case "openai_compat" -> new OpenAiSttProvider(c);
            default -> throw new IllegalArgumentException("unknown voice provider '" + c.provider() + "' (use openai_compat or whisper_cpp)");
        };
    }
}
