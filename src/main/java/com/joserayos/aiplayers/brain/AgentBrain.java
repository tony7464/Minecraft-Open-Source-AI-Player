package com.joserayos.aiplayers.brain;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.agent.AgentChat;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.llm.ChatMessage;
import com.joserayos.aiplayers.llm.LlmException;
import com.joserayos.aiplayers.llm.LlmRouter;
import com.joserayos.aiplayers.llm.config.ModelProfile;
import com.joserayos.aiplayers.llm.LlmResponse;
import com.joserayos.aiplayers.perception.AgentSnapshot;
import com.joserayos.aiplayers.perception.SnapshotBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The agent's think loop. Inputs are queued on the server thread; at most one model request per agent
 * is in flight; results hop back to the server thread with {@code server.execute}.
 */
public final class AgentBrain {
    private static final int HISTORY_LIMIT = 8;

    private final AgentEntity agent;
    private final Deque<ChatMessage> history = new ArrayDeque<>();
    private final List<String> pendingInputs = new ArrayList<>();
    private @Nullable CompletableFuture<LlmResponse> inFlight;
    private int generation;
    private long nextThinkAtMillis;
    private String lastReply = "";
    private volatile String lastExchange = "(none yet)";

    public AgentBrain(AgentEntity agent) {
        this.agent = agent;
    }

    /** Queue input (chat, task results...). Picked up on the next server tick. Server thread only. */
    public void submit(String input) {
        if (pendingInputs.size() < 10) pendingInputs.add(input);
    }

    public boolean isBusy() { return inFlight != null; }

    public String lastReply() { return lastReply; }

    public List<ChatMessage> history() { return List.copyOf(history); }

    public String debugLastExchange() { return lastExchange; }

    public void clearHistory() { history.clear(); }

    /** Called every server tick from the entity. */
    public void serverTick() {
        if (inFlight != null || pendingInputs.isEmpty()) return;
        if (System.currentTimeMillis() < nextThinkAtMillis) return;
        startThink();
    }

    /** Kill switch: drop queued input and abandon the in-flight request. */
    public void cancel(String reason) {
        generation++;
        pendingInputs.clear();
        if (inFlight != null) {
            inFlight.cancel(true);
            inFlight = null;
            AiPlayersMod.LOGGER.debug("Cancelled think for {}: {}", agent.getAgentName(), reason);
        }
        agent.setThinking(false);
    }

    private void startThink() {
        MinecraftServer server = agent.level().getServer();
        if (server == null) return;
        // Debounce: merge everything that arrived while we were busy into one turn.
        String input = String.join("\n", pendingInputs);
        pendingInputs.clear();

        AgentSnapshot snapshot = SnapshotBuilder.capture(agent);
        List<ChatMessage> messages = PromptBuilder.build(snapshot, agent.getPersona(), false, List.copyOf(history), input);
        LlmRouter router = AiServices.router();
        ModelProfile profile;
        try {
            profile = router.select(agent.getProfileId());
        } catch (LlmException e) {
            reportError(e);
            return;
        }
        int timeout = profile.timeoutSeconds() * LlmRouter.MAX_ATTEMPTS + 10;
        noticeHostedProvider(profile);

        int gen = ++generation;
        agent.setThinking(true);
        ServerPlayer owner = agent.getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(Component.literal(agent.getAgentName() + " is thinking...").withStyle(ChatFormatting.GRAY), true);

        String agentName = agent.getAgentName();
        String profileId = agent.getProfileId();
        CompletableFuture<LlmResponse> future = BrainExecutor.submit(() -> router.chat(agentName, profileId, messages, List.of()))
                .thenApply(LlmRouter.Result::response)
                .orTimeout(timeout, TimeUnit.SECONDS);
        inFlight = future;
        lastExchange = describe(messages, null);
        future.whenComplete((response, error) -> server.execute(() -> onResult(gen, input, response, error)));
    }

    private void onResult(int gen, String input, @Nullable LlmResponse response, @Nullable Throwable error) {
        if (gen != generation || agent.isRemoved()) return; // cancelled or stale
        inFlight = null;
        agent.setThinking(false);
        nextThinkAtMillis = System.currentTimeMillis() + AiPlayersConfig.get(AiPlayersConfig.MIN_SECONDS_BETWEEN_THINKS) * 1000L;
        if (error != null || response == null) {
            reportError(LlmException.from(error));
            return;
        }
        lastExchange = describe(List.of(), response) + "\n" + lastExchange;
        String reply = response.text().isBlank() ? "..." : response.text();
        lastReply = reply;
        remember(ChatMessage.user(input));
        remember(ChatMessage.assistant(reply));
        AgentChat.say(agent, reply);
    }

    private void reportError(LlmException e) {
        AiPlayersMod.LOGGER.warn("Agent {} model call failed: {}", agent.getAgentName(), e.getMessage());
        ServerPlayer owner = agent.getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[" + agent.getAgentName() + "] " + e.friendlyMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private void noticeHostedProvider(ModelProfile profile) {
        ServerPlayer owner = agent.getOwnerPlayer();
        if (owner == null || !profile.isHosted()) return;
        if (AiServices.firstUse(owner.getUUID() + "/" + profile.id())) {
            owner.sendSystemMessage(Component.literal("[AI Players] Chat sent to " + agent.getAgentName()
                    + " and nearby game state are sent to " + profile.baseUrl() + " (profile '" + profile.id() + "').")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    static String describe(List<ChatMessage> messages, @Nullable LlmResponse response) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) sb.append("--- ").append(m.role()).append(" ---\n").append(m.content()).append('\n');
        if (response != null) sb.append("=== RESPONSE ===\n").append(response.text()).append('\n').append(response.toolCalls());
        return sb.toString();
    }

    private void remember(ChatMessage m) {
        history.addLast(m);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
    }
}
