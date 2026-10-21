package com.joserayos.aiplayers.brain;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.llm.BudgetGuard;
import com.joserayos.aiplayers.llm.LlmRouter;
import com.joserayos.aiplayers.llm.RateLimiter;
import com.joserayos.aiplayers.llm.UsageLog;
import com.joserayos.aiplayers.llm.config.ModelProfiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.loading.FMLPaths;

/** Server-wide services: model profiles and the LLM router. Created lazily, reloaded with /ai reload. */
public final class AiServices {
    private static volatile LlmRouter router;
    private static final Set<String> privacyNoticesShown = ConcurrentHashMap.newKeySet();

    private AiServices() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(AiPlayersMod.MODID);
    }

    public static Path modelsFile() {
        return configDir().resolve("models.json");
    }

    public static LlmRouter router() {
        LlmRouter r = router;
        if (r == null) {
            synchronized (AiServices.class) {
                if (router == null) {
                    router = new LlmRouter(loadProfiles(), new RateLimiter(System::currentTimeMillis),
                            new BudgetGuard(Clock.systemDefaultZone()),
                            new UsageLog(FMLPaths.GAMEDIR.get().resolve("logs").resolve("aiplayers-usage.csv")),
                            LlmRouter::defaultProvider);
                }
                r = router;
            }
        }
        return r;
    }

    /** Re-read models.json. Returns warnings to show to the user. */
    public static List<String> reload() {
        ModelProfiles profiles = loadProfiles();
        router().setProfiles(profiles);
        return profiles.warnings();
    }

    private static ModelProfiles loadProfiles() {
        try {
            ModelProfiles p = ModelProfiles.loadOrCreate(modelsFile(), System::getenv);
            p.warnings().forEach(w -> AiPlayersMod.LOGGER.warn("models.json: {}", w));
            AiPlayersMod.LOGGER.info("Loaded {} model profile(s) from {}; default '{}'", p.all().size(), modelsFile(), p.defaultProfileId());
            return p;
        } catch (IOException | RuntimeException e) {
            AiPlayersMod.LOGGER.error("Could not read {}: {}. Falling back to built-in defaults.", modelsFile(), e.toString());
            ModelProfiles p = ModelProfiles.parse(ModelProfiles.defaultJson(), System::getenv);
            return new ModelProfiles(p.defaultProfileId(), p.all(), List.of("models.json could not be read: " + e.getMessage()));
        }
    }

    /** Returns true the first time a given owner+profile pair is seen (for the hosted-provider privacy notice). */
    public static boolean firstUse(String ownerAndProfile) {
        return privacyNoticesShown.add(ownerAndProfile);
    }
}
