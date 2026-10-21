package com.joserayos.aiplayers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Global settings, stored in config/aiplayers-common.toml and editable in the NeoForge config screen. */
public final class AiPlayersConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static { BUILDER.push("agents"); }

    public static final ModConfigSpec.IntValue MAX_AGENTS = BUILDER
            .comment("Maximum number of AI agents per server.")
            .defineInRange("maxAgents", 5, 1, 64);
    public static final ModConfigSpec.IntValue TELEPORT_DISTANCE = BUILDER
            .comment("In FOLLOW mode, teleport to the owner when farther than this many blocks (0 disables).")
            .defineInRange("teleportDistance", 32, 0, 256);
    public static final ModConfigSpec.BooleanValue IDLE_WHEN_OWNER_OFFLINE = BUILDER
            .comment("Agents stop following and stop thinking when their owner is offline.")
            .define("idleWhenOwnerOffline", true);
    public static final ModConfigSpec.BooleanValue OPS_CAN_COMMAND_ANY_AGENT = BUILDER
            .comment("Operators (permission level 2) can command agents they don't own.")
            .define("opsCanCommandAnyAgent", true);

    static { BUILDER.pop(); BUILDER.push("brain"); }

    public static final ModConfigSpec.IntValue MIN_SECONDS_BETWEEN_THINKS = BUILDER
            .comment("Minimum seconds between two model calls for the same agent.")
            .defineInRange("minSecondsBetweenThinks", 3, 0, 600);
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_REQUESTS = BUILDER
            .comment("Maximum model requests in flight across all agents.")
            .defineInRange("maxConcurrentRequests", 4, 1, 64);
    public static final ModConfigSpec.IntValue MAX_REPLY_CHARS = BUILDER
            .comment("Agent chat replies are cut to this many characters.")
            .defineInRange("maxReplyChars", 256, 32, 2000);
    public static final ModConfigSpec.IntValue MAX_TOOL_CALLS_PER_THINK = BUILDER
            .comment("Maximum tool calls accepted from one model response.")
            .defineInRange("maxToolCallsPerThink", 4, 1, 16);

    static { BUILDER.pop(); BUILDER.push("model"); }

    public static final ModConfigSpec.ConfigValue<String> BASE_URL = BUILDER
            .comment("OpenAI-compatible base URL. Ollama: http://localhost:11434/v1, LM Studio: http://localhost:1234/v1")
            .define("baseUrl", "http://localhost:11434/v1");
    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("Model id, e.g. qwen3:8b for Ollama.")
            .define("model", "qwen3:8b");
    public static final ModConfigSpec.ConfigValue<String> API_KEY_ENV = BUILDER
            .comment("Name of the environment variable holding the API key (leave empty for local servers).")
            .define("apiKeyEnv", "");
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS = BUILDER
            .comment("Request timeout in seconds.")
            .defineInRange("timeoutSeconds", 60, 5, 600);

    static { BUILDER.pop(); }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AiPlayersConfig() {}

    /** Safe read that falls back to the default before the config is loaded (e.g. in unit tests). */
    public static int get(ModConfigSpec.IntValue value) {
        try { return value.getAsInt(); } catch (IllegalStateException e) { return value.getDefault(); }
    }

    public static String get(ModConfigSpec.ConfigValue<String> value) {
        try { return value.get(); } catch (IllegalStateException e) { return value.getDefault(); }
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        try { return value.getAsBoolean(); } catch (IllegalStateException e) { return value.getDefault(); }
    }
}
