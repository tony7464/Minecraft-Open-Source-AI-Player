package com.joserayos.aiplayers.brain;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.agent.AgentChat;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.brain.tools.ToolArgs;
import com.joserayos.aiplayers.brain.tools.ToolContext;
import com.joserayos.aiplayers.brain.tools.ToolRegistry;
import com.joserayos.aiplayers.brain.tools.ToolResult;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.llm.ChatMessage;
import com.joserayos.aiplayers.llm.JsonActionParser;
import com.joserayos.aiplayers.llm.LlmException;
import com.joserayos.aiplayers.llm.LlmResponse;
import com.joserayos.aiplayers.llm.LlmRouter;
import com.joserayos.aiplayers.llm.ToolCall;
import com.joserayos.aiplayers.llm.ToolSpec;
import com.joserayos.aiplayers.llm.config.ModelProfile;
import com.joserayos.aiplayers.perception.AgentSnapshot;
import com.joserayos.aiplayers.perception.SnapshotBuilder;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The agent's think loop. Inputs (chat, task results, tool follow-ups) are queued on the server thread;
 * at most one model request per agent is in flight; results hop back to the server thread with
 * {@code server.execute}, where tool calls are validated and executed.
 */
public final class AgentBrain {
    private static final int HISTORY_LIMIT = 14;

    /** One queued input. {@code text == null} means "continue after tool results". */
    private record Input(@Nullable String text, boolean fromPlayer, String speaker) {}

    private final AgentEntity agent;
    private final Deque<ChatMessage> history = new ArrayDeque<>();
    private final List<Input> pending = new ArrayList<>();
    private @Nullable CompletableFuture<LlmResponse> inFlight;
    private int generation;
    private long nextThinkAtTick;
    private int chainBudget;
    private boolean jsonRetryUsed;
    private String lastSpeaker = "";
    private String lastReply = "";
    private volatile String lastExchange = "(none yet)";

    public AgentBrain(AgentEntity agent) {
        this.agent = agent;
    }

    // ---- inputs (server thread) ----

    /** A message from a player (already wrapped with {@link PromptBuilder#chatInput}). */
    public void submit(String input) {
        submitFromPlayer(input, "");
    }

    public void submitFromPlayer(String input, String speaker) {
        if (pending.size() >= 10) return;
        pending.add(new Input(input, true, speaker));
        chainBudget = AiPlayersConfig.get(AiPlayersConfig.MAX_CHAINED_THINKS);
        jsonRetryUsed = false;
    }

    /** Automatic input (task results, follow-ups). Ignored once the chain budget for this request is spent. */
    public boolean submitAutomatic(@Nullable String input) {
        if (chainBudget <= 0 || pending.size() >= 10) return false;
        chainBudget--;
        pending.add(new Input(input, false, ""));
        return true;
    }

    /** Called by the entity when a task ends. Only reports once the queue is empty or something failed. */
    public void onTaskResult(AgentTask task, TaskStatus status, String message) {
        if (status == TaskStatus.FAILED && !agent.getExecutor().isIdle()) {
            agent.getExecutor().clear("previous task failed");
        }
        if (!agent.getExecutor().isIdle()) return;
        submitAutomatic("[task_result] " + task.describe() + " " + status.name() + ": " + message);
    }

    public boolean isBusy() { return inFlight != null; }

    public String lastReply() { return lastReply; }

    public List<ChatMessage> history() { return List.copyOf(history); }

    public String debugLastExchange() { return lastExchange; }

    public void clearHistory() { history.clear(); }

    // ---- loop ----

    public void serverTick() {
        if (inFlight != null || pending.isEmpty()) return;
        if (agent.level().getGameTime() < nextThinkAtTick) return;
        startThink();
    }

    /** Kill switch: drop queued input and abandon the in-flight request. */
    public void cancel(String reason) {
        generation++;
        pending.clear();
        chainBudget = 0;
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
        List<String> texts = new ArrayList<>();
        boolean anyPlayer = false;
        for (Input in : pending) {
            if (in.text() != null) texts.add(in.text());
            if (in.fromPlayer()) {
                anyPlayer = true;
                if (!in.speaker().isEmpty()) lastSpeaker = in.speaker();
            }
        }
        pending.clear();
        String newInput = texts.isEmpty() ? null : String.join("\n", texts);

        LlmRouter router = AiServices.router();
        ModelProfile profile;
        try {
            profile = router.select(agent.getProfileId());
        } catch (LlmException e) {
            reportError(e);
            return;
        }
        boolean jsonMode = profile.toolMode() == ModelProfile.ToolMode.JSON_PROMPT;
        List<ToolSpec> tools = ToolRegistry.specsFor(agent.getMode());
        AgentSnapshot snapshot = SnapshotBuilder.capture(agent);
        String system = PromptBuilder.systemPrompt(snapshot, agent.getPersona(), true) + (jsonMode ? JsonActionParser.instructions(tools) : "");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(system));
        messages.addAll(history);
        if (newInput != null) messages.add(ChatMessage.user(newInput));
        if (messages.size() == 1) return; // nothing to say

        int timeout = profile.timeoutSeconds() * LlmRouter.MAX_ATTEMPTS + 10;
        noticeHostedProvider(profile);
        int gen = ++generation;
        agent.setThinking(true);
        ServerPlayer owner = agent.getOwnerPlayer();
        if (owner != null && anyPlayer) {
            owner.sendSystemMessage(Component.literal(agent.getAgentName() + " is thinking...").withStyle(ChatFormatting.GRAY), true);
        }
        String agentName = agent.getAgentName();
        String profileId = agent.getProfileId();
        List<ToolSpec> sentTools = jsonMode ? List.of() : tools;
        lastExchange = describe(messages, null);
        CompletableFuture<LlmResponse> future = BrainExecutor.submit(() -> router.chat(agentName, profileId, messages, sentTools))
                .thenApply(LlmRouter.Result::response)
                .orTimeout(timeout, TimeUnit.SECONDS);
        inFlight = future;
        future.whenComplete((response, error) -> server.execute(() -> onResult(gen, newInput, jsonMode, response, error)));
    }

    private void onResult(int gen, @Nullable String input, boolean jsonMode, @Nullable LlmResponse response, @Nullable Throwable error) {
        if (gen != generation || agent.isRemoved()) return; // cancelled or stale
        inFlight = null;
        agent.setThinking(false);
        nextThinkAtTick = agent.level().getGameTime() + AiPlayersConfig.get(AiPlayersConfig.MIN_SECONDS_BETWEEN_THINKS) * 20L;
        if (error != null || response == null) {
            reportError(LlmException.from(error));
            return;
        }
        lastExchange = describe(List.of(), response) + "\n" + lastExchange;

        String text = response.text();
        List<ToolCall> calls = new ArrayList<>(response.toolCalls());
        boolean nativeCalls = !calls.isEmpty();
        String say = text;
        if (calls.isEmpty() && (jsonMode || JsonActionParser.looksLikeJson(text))) {
            Optional<JsonActionParser.Parsed> parsed = JsonActionParser.parse(text);
            if (parsed.isPresent()) {
                say = parsed.get().say();
                calls.addAll(parsed.get().calls());
            } else if (jsonMode && JsonActionParser.looksLikeJson(text) && !jsonRetryUsed) {
                jsonRetryUsed = true;
                if (input != null) remember(ChatMessage.user(input));
                remember(ChatMessage.assistant(text));
                submitAutomatic("Your last reply was not valid JSON. Reply ONLY with the JSON object described in the instructions.");
                return;
            }
        }

        if (input != null) remember(ChatMessage.user(input));
        int max = AiPlayersConfig.get(AiPlayersConfig.MAX_TOOL_CALLS_PER_THINK);
        remember(nativeCalls ? ChatMessage.assistant(text, calls) : ChatMessage.assistant(text));

        if (!say.isBlank()) {
            lastReply = say;
            AgentChat.say(agent, say);
        }

        boolean followUp = false;
        List<String> jsonResults = new ArrayList<>();
        ToolContext ctx = new ToolContext(lastSpeaker.isEmpty() ? ownerName() : lastSpeaker);
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            ToolResult result = i < max
                    ? ToolRegistry.execute(agent, call.name(), new ToolArgs(call.arguments()), ctx)
                    : ToolResult.error("skipped: at most " + max + " tool calls per reply");
            AiPlayersMod.LOGGER.debug("{} tool {}({}) -> {}", agent.getAgentName(), call.name(), call.arguments(), result.message());
            followUp |= result.needsFollowUp();
            if (nativeCalls) remember(ChatMessage.toolResult(call.id(), call.name(), result.message()));
            else jsonResults.add(call.name() + ": " + result.message());
        }
        if (!jsonResults.isEmpty()) remember(ChatMessage.user("[tool_results] " + String.join(" | ", jsonResults)));
        if (followUp) submitAutomatic(null);
        if (say.isBlank() && calls.isEmpty()) lastReply = "...";
    }

    private String ownerName() {
        ServerPlayer owner = agent.getOwnerPlayer();
        return owner == null ? "" : owner.getGameProfile().name();
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
        for (ChatMessage m : messages) {
            sb.append("--- ").append(m.role()).append(m.toolName() != null ? " " + m.toolName() : "").append(" ---\n").append(m.content()).append('\n');
            if (!m.toolCalls().isEmpty()) sb.append("tool_calls: ").append(m.toolCalls()).append('\n');
        }
        if (response != null) sb.append("=== RESPONSE ===\n").append(response.text()).append('\n').append(response.toolCalls());
        return sb.toString();
    }

    private void remember(ChatMessage m) {
        history.addLast(m);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
        // Never start the history with a dangling tool result or assistant turn.
        while (!history.isEmpty() && history.peekFirst().role() != ChatMessage.Role.USER) history.removeFirst();
    }
}
