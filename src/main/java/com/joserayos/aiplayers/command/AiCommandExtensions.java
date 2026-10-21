package com.joserayos.aiplayers.command;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.brain.AiServices;
import com.joserayos.aiplayers.brain.BrainExecutor;
import com.joserayos.aiplayers.llm.BudgetGuard;
import com.joserayos.aiplayers.llm.ChatMessage;
import com.joserayos.aiplayers.llm.LlmException;
import com.joserayos.aiplayers.llm.LlmRouter;
import com.joserayos.aiplayers.llm.ToolSpec;
import com.joserayos.aiplayers.llm.config.ModelProfile;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Model-profile commands: /ai model, /ai test-profile, /ai reload, /ai budget, /ai profiles. */
final class AiCommandExtensions {
    private AiCommandExtensions() {}

    static void extend(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("model").then(AiCommand.agentArg().then(AiCommand.profileArgument().executes(AiCommandExtensions::model))));
        root.then(Commands.literal("memory").then(AiCommand.agentArg().executes(AiCommandExtensions::memory)
                .then(Commands.literal("clear").executes(AiCommandExtensions::memoryClear))));
        root.then(Commands.literal("gui").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            if (!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, com.joserayos.aiplayers.network.AgentListPayload.TYPE.id())) {
                ctx.getSource().sendFailure(Component.literal("The Agent Manager needs the AI Players mod on your client. Use /ai list instead."));
                return 0;
            }
            com.joserayos.aiplayers.network.AiNetwork.sendList(player, true);
            return 1;
        }));
        root.then(Commands.literal("locations").executes(AiCommandExtensions::locations));
        root.then(Commands.literal("profiles").executes(AiCommandExtensions::profiles));
        root.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(AiCommandExtensions::reload));
        root.then(Commands.literal("budget").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(AiCommandExtensions::budget));
        root.then(Commands.literal("test-profile").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(AiCommand.profileArgument().executes(AiCommandExtensions::testProfile)));
    }

    static List<String> profileNames() {
        return List.copyOf(AiServices.router().profiles().all().keySet());
    }

    static String defaultProfile() {
        return AiServices.router().profiles().defaultProfileId();
    }

    static void onSpawned(AgentEntity agent) {}

    private static int model(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = AiCommand.agent(ctx);
        String profile = StringArgumentType.getString(ctx, "profile");
        if (AiServices.router().profiles().get(profile).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown profile '" + profile + "'. Known: " + String.join(", ", profileNames())));
            return 0;
        }
        a.setProfileId(AiServices.router().profiles().get(profile).get().id());
        AiCommand.ok(ctx, a.getAgentName() + " now uses profile '" + a.getProfileId() + "'.");
        return 1;
    }

    private static int memory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = AiCommand.agent(ctx);
        var mem = com.joserayos.aiplayers.team.AgentMemory.of(ctx.getSource().getServer(), a.getUUID());
        var notes = mem.notes();
        if (notes.isEmpty() && mem.summary().isEmpty()) {
            AiCommand.ok(ctx, a.getAgentName() + " has no memories yet.");
            return 0;
        }
        for (var e : notes.entrySet()) AiCommand.ok(ctx, "- " + e.getKey() + ": " + e.getValue());
        for (String line : mem.summary()) ctx.getSource().sendSystemMessage(Component.literal("  earlier: " + line).withStyle(ChatFormatting.GRAY));
        return notes.size();
    }

    private static int memoryClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = AiCommand.agent(ctx);
        com.joserayos.aiplayers.team.AgentMemory.of(ctx.getSource().getServer(), a.getUUID()).clear();
        a.getAgentBrain().clearHistory();
        AiCommand.ok(ctx, "Cleared " + a.getAgentName() + "'s memory.");
        return 1;
    }

    private static int locations(CommandContext<CommandSourceStack> ctx) {
        var locs = com.joserayos.aiplayers.team.Blackboard.locations();
        if (locs.isEmpty()) AiCommand.ok(ctx, "No shared locations yet.");
        for (var e : locs.entrySet()) {
            var p = e.getValue().pos();
            AiCommand.ok(ctx, "- " + e.getKey() + ": " + p.getX() + " " + p.getY() + " " + p.getZ() + " (" + e.getValue().dimension().identifier() + ")");
        }
        return locs.size();
    }

    private static int profiles(CommandContext<CommandSourceStack> ctx) {
        var profiles = AiServices.router().profiles();
        for (ModelProfile p : profiles.all().values()) {
            AiCommand.ok(ctx, "- " + p.id() + (p.id().equals(profiles.defaultProfileId()) ? " (default)" : "")
                    + ": " + p.provider() + " " + p.model() + " @ " + p.baseUrl()
                    + (p.dailyBudgetUsd() > 0 ? String.format(Locale.ROOT, " | budget $%.2f/day", p.dailyBudgetUsd()) : ""));
        }
        return profiles.all().size();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = AiServices.reload();
        AiCommand.ok(ctx, "Reloaded " + AiServices.modelsFile().getFileName() + ": " + AiServices.router().profiles().all().size()
                + " profile(s), default '" + defaultProfile() + "'.");
        for (String w : warnings) ctx.getSource().sendSystemMessage(Component.literal("warning: " + w).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int budget(CommandContext<CommandSourceStack> ctx) {
        Map<String, BudgetGuard.Totals> totals = AiServices.router().budget().todayTotals();
        if (totals.isEmpty()) {
            AiCommand.ok(ctx, "No model calls today.");
            return 0;
        }
        for (var e : totals.entrySet()) {
            var t = e.getValue();
            double limit = AiServices.router().profiles().get(e.getKey()).map(ModelProfile::dailyBudgetUsd).orElse(0.0);
            AiCommand.ok(ctx, String.format(Locale.ROOT, "- %s: %d request(s), %d in / %d out tokens, ~$%.4f%s",
                    e.getKey(), t.requests(), t.inputTokens(), t.outputTokens(), t.costUsd(),
                    limit > 0 ? String.format(Locale.ROOT, " of $%.2f", limit) : ""));
        }
        AiCommand.ok(ctx, "Details: " + AiServices.router().usageLog().file());
        return totals.size();
    }

    /** Sends "say hi" plus one tool to check connectivity, latency and tool calling. Runs off-thread. */
    private static int testProfile(CommandContext<CommandSourceStack> ctx) {
        String profile = StringArgumentType.getString(ctx, "profile");
        if (AiServices.router().profiles().get(profile).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown profile '" + profile + "'."));
            return 0;
        }
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ToolSpec wave = new ToolSpec("wave", "Wave at the player. Call this tool now.",
                JsonParser.parseString("{\"type\":\"object\",\"properties\":{\"times\":{\"type\":\"integer\"}},\"required\":[\"times\"]}").getAsJsonObject());
        List<ChatMessage> msgs = List.of(
                ChatMessage.system("You are a connectivity test. Reply with a 5 word greeting and call the wave tool with times=1."),
                ChatMessage.user("Say hi and wave."));
        source.sendSystemMessage(Component.literal("Testing profile '" + profile + "'...").withStyle(ChatFormatting.GRAY));
        BrainExecutor.submit(() -> AiServices.router().chat("(test)", profile, msgs, List.of(wave)))
                .whenComplete((res, err) -> server.execute(() -> {
                    if (err != null) {
                        source.sendFailure(Component.literal("Profile '" + profile + "' failed: " + LlmException.from(err).friendlyMessage()));
                        return;
                    }
                    LlmRouter.Result r = res;
                    boolean tools = !r.response().toolCalls().isEmpty();
                    source.sendSystemMessage(Component.literal("Profile '" + r.profile().id() + "' OK in " + r.latencyMs() + " ms. Reply: \""
                            + r.response().text() + "\". Native tool calling: " + (tools ? "yes" : "no (consider \"tool_mode\": \"json_prompt\")"))
                            .withStyle(tools ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                }));
        return 1;
    }
}
