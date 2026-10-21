package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.joserayos.aiplayers.llm.config.ModelProfile;
import com.joserayos.aiplayers.llm.config.ModelProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelProfilesTest {
    private static final Map<String, String> ENV = Map.of("XAI_API_KEY", "xai-secret");

    @Test
    void defaultFileParsesAndResolvesEnv() {
        ModelProfiles p = ModelProfiles.parse(ModelProfiles.defaultJson(), ENV::get);
        assertEquals("local-qwen", p.defaultProfileId());
        assertTrue(p.warnings().isEmpty(), p.warnings().toString());
        ModelProfile grok = p.get("grok").orElseThrow();
        assertEquals("xai-secret", grok.apiKey());
        assertTrue(grok.isHosted());
        assertEquals("local-qwen", grok.fallbackProfile());
        ModelProfile local = p.get("local-qwen").orElseThrow();
        assertFalse(local.isHosted());
        assertEquals(ModelProfile.ToolMode.NATIVE, local.toolMode());
        assertEquals(ModelProfile.ToolMode.JSON_PROMPT, p.get("lmstudio").orElseThrow().toolMode());
        assertEquals("", p.get("claude").orElseThrow().apiKey(), "unset env var resolves to empty");
        assertEquals("anthropic", p.get("claude").orElseThrow().provider());
    }

    @Test
    void unknownProfileFallsBackToDefault() {
        ModelProfiles p = ModelProfiles.parse(ModelProfiles.defaultJson(), ENV::get);
        assertEquals("local-qwen", p.resolve("nope").id());
        assertEquals("grok", p.resolve("GROK").id());
    }

    @Test
    void badProviderIsSkippedWithWarning() {
        ModelProfiles p = ModelProfiles.parse("""
                {"default_profile":"x","profiles":{"x":{"provider":"weird"},"y":{"provider":"mock"}}}""", ENV::get);
        assertEquals("y", p.defaultProfileId());
        assertEquals(2, p.warnings().size());
    }

    @Test
    void emptyFileGivesMockProfile() {
        ModelProfiles p = ModelProfiles.parse("{}", ENV::get);
        assertEquals("mock", p.defaultProfileId());
    }

    @Test
    void loadOrCreateWritesExampleFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("aiplayers/models.json");
        ModelProfiles p = ModelProfiles.loadOrCreate(file, ENV::get);
        assertTrue(Files.exists(file));
        assertFalse(Files.readString(file).contains("xai-secret"), "secrets must never be written to disk");
        assertEquals("local-qwen", p.defaultProfileId());
    }

    @Test
    void costMath() {
        ModelProfile p = ModelProfiles.parse("""
                {"profiles":{"a":{"price_per_million_input":2.0,"price_per_million_output":10.0}}}""", ENV::get).resolve("a");
        assertEquals(0.002 + 0.001, p.costUsd(1000, 100), 1e-9);
    }
}
