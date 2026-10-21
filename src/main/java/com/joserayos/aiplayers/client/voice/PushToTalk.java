package com.joserayos.aiplayers.client.voice;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.voice.SttProvider;
import com.joserayos.aiplayers.voice.VoiceConfig;
import com.joserayos.aiplayers.voice.WavEncoder;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Hold V to talk. The recording is transcribed on this machine's configured STT server; the transcript
 * opens in the chat box for confirmation (or is sent directly if "confirm" is false).
 */
public final class PushToTalk {
    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(AiPlayersMod.MODID, "aiplayers"));
    static final KeyMapping KEY = new KeyMapping("key.aiplayers.push_to_talk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private static VoiceConfig config;
    private static MicRecorder recorder;
    private static boolean busy;

    private PushToTalk() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(KEY);
        });
        NeoForge.EVENT_BUS.addListener(PushToTalk::onTick);
    }

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("aiplayers").resolve("voice.json"); }

    static void status(String text, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(text).withStyle(color));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean down = KEY.isDown() && mc.screen == null;
        if (down && recorder == null && !busy) begin();
        else if (recorder != null && (!KEY.isDown() || recorder.isFull())) finish();
    }

    private static void begin() {
        try {
            config = VoiceConfig.load(file());
        } catch (Exception e) {
            status("Voice config error: " + e.getMessage(), ChatFormatting.RED);
            return;
        }
        if (!config.enabled()) {
            status("Voice is off. Edit config/aiplayers/voice.json (set enabled: true) and point it at a whisper server.", ChatFormatting.YELLOW);
            return;
        }
        try {
            recorder = new MicRecorder(config.maxSeconds());
            recorder.start();
            status("Listening... (release V to send)", ChatFormatting.AQUA);
        } catch (Exception e) {
            recorder = null;
            status("Can't open the microphone: " + e.getMessage(), ChatFormatting.RED);
        }
    }

    private static void finish() {
        byte[] pcm = recorder.stop();
        recorder = null;
        if (pcm.length < 16_000 || WavEncoder.rms16(pcm) < 0.003) { // under half a second, or silence
            status("Didn't hear anything.", ChatFormatting.GRAY);
            return;
        }
        busy = true;
        status("Transcribing...", ChatFormatting.GRAY);
        VoiceConfig c = config;
        byte[] wav = WavEncoder.encode(pcm, 16_000, 1, 16);
        CompletableFuture.supplyAsync(() -> {
            try {
                return SttProvider.create(c).transcribe(wav, c.language());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }).whenComplete((text, err) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            Minecraft mc = Minecraft.getInstance();
            if (err != null) {
                status("Speech-to-text failed: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()), ChatFormatting.RED);
                return;
            }
            if (text == null || text.isBlank()) {
                status("Couldn't make out any words.", ChatFormatting.GRAY);
                return;
            }
            String msg = c.withDefaultTarget(text);
            if (msg.length() > 256) msg = msg.substring(0, 256);
            if (c.confirm()) {
                mc.setScreen(new ChatScreen(msg, false)); // Enter to send, Esc to cancel
            } else if (mc.player != null) {
                mc.player.connection.sendChat(msg);
            }
        }));
    }
}
