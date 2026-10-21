package com.joserayos.aiplayers.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side voice settings from {@code config/aiplayers/voice.json}. Speech-to-text runs on the player's
 * own machine, so API keys never reach the server. Keys go in environment variables: {@code "${env:NAME}"}.
 */
public record VoiceConfig(boolean enabled, String provider, String baseUrl, String model, String apiKey, String language,
                          boolean confirm, int maxSeconds, int timeoutSeconds, String defaultAgent) {
    private static final Pattern ENV = Pattern.compile("\\$\\{env:([A-Za-z0-9_]+)}");

    public static final String DEFAULT_JSON = """
            {
              "_readme": "Push-to-talk voice commands (hold V). Start a local whisper server, set enabled to true, then rejoin. provider: whisper_cpp (whisper.cpp server, base_url without /v1) or openai_compat (OpenAI, Groq, speaches...; base_url with /v1). Put keys in env vars: ${env:NAME}.",
              "enabled": false,
              "provider": "whisper_cpp",
              "base_url": "http://127.0.0.1:8080",
              "model": "whisper-1",
              "api_key": "",
              "language": "en",
              "confirm": true,
              "max_seconds": 15,
              "timeout_seconds": 30,
              "default_agent": ""
            }
            """;

    public static VoiceConfig parse(String json, Function<String, String> env) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return new VoiceConfig(
                bool(o, "enabled", false), str(o, "provider", "whisper_cpp", env), str(o, "base_url", "http://127.0.0.1:8080", env),
                str(o, "model", "whisper-1", env), str(o, "api_key", "", env), str(o, "language", "en", env), bool(o, "confirm", true),
                Math.max(1, Math.min(60, num(o, "max_seconds", 15))), Math.max(3, Math.min(300, num(o, "timeout_seconds", 30))),
                str(o, "default_agent", "", env));
    }

    /** Loads the file, writing the default first if it doesn't exist. */
    public static VoiceConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_JSON);
        }
        return parse(Files.readString(file), System::getenv);
    }

    static String str(JsonObject o, String k, String def, Function<String, String> env) {
        String v = o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
        Matcher m = ENV.matcher(v);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String e = env.apply(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(e == null ? "" : e));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }

    static int num(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }

    /** "@Ava " prefix for transcripts that don't name an agent. */
    public String withDefaultTarget(String transcript) {
        String t = transcript.strip();
        if (defaultAgent.isBlank() || t.startsWith("@")) return t;
        return "@" + defaultAgent + " " + t;
    }
}
