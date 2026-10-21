package com.joserayos.aiplayers.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads models.json: named model profiles plus a default. */
public final class ModelProfiles {
    private static final Pattern ENV = Pattern.compile("\\$\\{env:([A-Za-z0-9_]+)}");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String defaultProfile;
    private final Map<String, ModelProfile> profiles;
    private final List<String> warnings;

    public ModelProfiles(String defaultProfile, Map<String, ModelProfile> profiles, List<String> warnings) {
        this.defaultProfile = defaultProfile;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.warnings = List.copyOf(warnings);
    }

    public String defaultProfileId() { return defaultProfile; }

    public Map<String, ModelProfile> all() { return profiles; }

    public List<String> warnings() { return warnings; }

    public Optional<ModelProfile> get(String id) {
        if (id == null) return Optional.empty();
        ModelProfile p = profiles.get(id);
        if (p != null) return Optional.of(p);
        return profiles.values().stream().filter(x -> x.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The named profile, or the default one if it doesn't exist. */
    public ModelProfile resolve(String id) {
        return get(id).or(() -> get(defaultProfile)).orElseGet(() -> profiles.values().iterator().next());
    }

    /** Load from a file, writing the example file first if it doesn't exist. */
    public static ModelProfiles loadOrCreate(Path file, Function<String, String> env) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson(), StandardCharsets.UTF_8);
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8), env);
    }

    public static ModelProfiles parse(String json, Function<String, String> env) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<String> warnings = new ArrayList<>();
        Map<String, ModelProfile> out = new LinkedHashMap<>();
        JsonObject profiles = root.has("profiles") ? root.getAsJsonObject("profiles") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            try {
                out.put(e.getKey(), parseProfile(e.getKey(), e.getValue().getAsJsonObject(), env, warnings));
            } catch (RuntimeException ex) {
                warnings.add("profile '" + e.getKey() + "' skipped: " + ex.getMessage());
            }
        }
        if (out.isEmpty()) {
            warnings.add("no valid profiles found; using a built-in mock profile");
            out.put("mock", parseProfile("mock", JsonParser.parseString("{\"provider\":\"mock\"}").getAsJsonObject(), env, warnings));
        }
        String def = str(root, "default_profile", "");
        if (!out.containsKey(def)) {
            String first = out.keySet().iterator().next();
            if (!def.isEmpty()) warnings.add("default_profile '" + def + "' not found; using '" + first + "'");
            def = first;
        }
        return new ModelProfiles(def, out, warnings);
    }

    static ModelProfile parseProfile(String id, JsonObject o, Function<String, String> env, List<String> warnings) {
        String provider = str(o, "provider", "openai_compat").toLowerCase(Locale.ROOT);
        if (!List.of("openai_compat", "anthropic", "mock").contains(provider)) {
            throw new IllegalArgumentException("unknown provider '" + provider + "' (use openai_compat, anthropic or mock)");
        }
        String defaultUrl = switch (provider) {
            case "anthropic" -> "https://api.anthropic.com";
            case "mock" -> "mock://";
            default -> "http://localhost:11434/v1";
        };
        String apiKey = resolveEnv(str(o, "api_key", ""), env, id, warnings);
        String toolMode = str(o, "tool_mode", "native").toLowerCase(Locale.ROOT);
        return new ModelProfile(
                id, provider,
                resolveEnv(str(o, "base_url", defaultUrl), env, id, warnings),
                str(o, "model", "mock".equals(provider) ? "mock" : ""),
                apiKey,
                toolMode.startsWith("json") ? ModelProfile.ToolMode.JSON_PROMPT : ModelProfile.ToolMode.NATIVE,
                num(o, "temperature", 0.4),
                (int) num(o, "max_output_tokens", 512),
                (int) num(o, "timeout_seconds", 60),
                (int) num(o, "requests_per_minute", "mock".equals(provider) ? 6000 : 20),
                num(o, "daily_budget_usd", 0),
                num(o, "price_per_million_input", 0),
                num(o, "price_per_million_output", 0),
                str(o, "fallback_profile", ""));
    }

    /** Replace every ${env:NAME} with the environment value (empty + warning if unset). */
    public static String resolveEnv(String value, Function<String, String> env, String profileId, List<String> warnings) {
        Matcher m = ENV.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = env.apply(m.group(1));
            if (v == null) v = ""; // reported when the profile is actually used (see LlmRouter)
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static double num(JsonObject o, String k, double def) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** The example models.json written on first launch. Contains no secrets. */
    public static String defaultJson() {
        return """
                {
                  "_readme": "Model profiles for AI Players. Each agent uses one profile (/ai model <agent> <profile>). Put API keys in environment variables and reference them as ${env:NAME}. Run /ai reload after editing.",
                  "default_profile": "local-qwen",
                  "profiles": {
                    "local-qwen": {
                      "provider": "openai_compat",
                      "base_url": "http://localhost:11434/v1",
                      "model": "qwen3:8b",
                      "api_key": "",
                      "tool_mode": "native",
                      "temperature": 0.4,
                      "max_output_tokens": 512,
                      "timeout_seconds": 60,
                      "requests_per_minute": 20,
                      "price_per_million_input": 0,
                      "price_per_million_output": 0
                    },
                    "lmstudio": {
                      "provider": "openai_compat",
                      "base_url": "http://localhost:1234/v1",
                      "model": "your-loaded-model-id",
                      "tool_mode": "json_prompt"
                    },
                    "llamacpp": {
                      "provider": "openai_compat",
                      "base_url": "http://localhost:8080/v1",
                      "model": "local",
                      "tool_mode": "json_prompt"
                    },
                    "grok": {
                      "provider": "openai_compat",
                      "base_url": "https://api.x.ai/v1",
                      "model": "<model id from xAI docs>",
                      "api_key": "${env:XAI_API_KEY}",
                      "daily_budget_usd": 1.00,
                      "price_per_million_input": 0,
                      "price_per_million_output": 0,
                      "fallback_profile": "local-qwen"
                    },
                    "openai": {
                      "provider": "openai_compat",
                      "base_url": "https://api.openai.com/v1",
                      "model": "<model id>",
                      "api_key": "${env:OPENAI_API_KEY}",
                      "daily_budget_usd": 1.00
                    },
                    "openrouter": {
                      "provider": "openai_compat",
                      "base_url": "https://openrouter.ai/api/v1",
                      "model": "<model id>",
                      "api_key": "${env:OPENROUTER_API_KEY}",
                      "daily_budget_usd": 1.00
                    },
                    "gemini": {
                      "provider": "openai_compat",
                      "base_url": "https://generativelanguage.googleapis.com/v1beta/openai",
                      "model": "<model id>",
                      "api_key": "${env:GEMINI_API_KEY}",
                      "daily_budget_usd": 1.00
                    },
                    "claude": {
                      "provider": "anthropic",
                      "base_url": "https://api.anthropic.com",
                      "model": "<model id from Anthropic docs>",
                      "api_key": "${env:ANTHROPIC_API_KEY}",
                      "daily_budget_usd": 1.00
                    },
                    "mock": {
                      "provider": "mock"
                    }
                  }
                }
                """;
    }
}
