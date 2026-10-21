package com.joserayos.aiplayers.command;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentManager;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.brain.PromptBuilder;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.perception.SnapshotBuilder;
import com.joserayos.aiplayers.safety.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The /ai command tree. */
public final class AiCommand {
    private static final SimpleCommandExceptionType NO_SUCH_AGENT = new SimpleCommandExceptionType(Component.literal("No agent with that name."));
    private static final SimpleCommandExceptionType NOT_ALLOWED = new SimpleCommandExceptionType(Component.literal("You can't command that agent."));

    public static final SuggestionProvider<CommandSourceStack> AGENT_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(AgentManager.all(ctx.getSource().getServer()).stream().map(AgentEntity::getAgentName), builder);

    private AiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ai");

        root.then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> spawn(ctx, defaultProfile()))
                        .then(profileArgument().executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "profile"))))));

        root.then(Commands.literal("despawn").then(agentArg().executes(AiCommand::despawn)));
        root.then(Commands.literal("list").executes(AiCommand::list));
        root.then(Commands.literal("status").then(agentArg().executes(AiCommand::status)));
        root.then(Commands.literal("tell").then(agentArg().then(Commands.argument("message", StringArgumentType.greedyString()).executes(AiCommand::tell))));
        root.then(Commands.literal("mode").then(agentArg().then(Commands.argument("mode", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AgentMode.values()).map(AgentMode::id), b))
                .executes(AiCommand::mode))));
        root.then(Commands.literal("stop")
                .then(Commands.literal("all").executes(ctx -> stopAll(ctx)))
                .then(agentArg().executes(AiCommand::stop)));
        root.then(Commands.literal("debug").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("snapshot").then(agentArg().executes(AiCommand::debugSnapshot)))
                .then(Commands.literal("prompt").then(agentArg().executes(AiCommand::debugPrompt))));

        AiCommandExtensions.extend(root);
        dispatcher.register(root);
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> agentArg() {
        return Commands.argument("agent", StringArgumentType.word()).suggests(AGENT_NAMES);
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> profileArgument() {
        return Commands.argument("profile", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(AiCommandExtensions.profileNames(), b));
    }

    static String defaultProfile() {
        return AiCommandExtensions.defaultProfile();
    }

    /** Look up an agent and check the caller may command it. */
    static AgentEntity agent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "agent");
        AgentEntity agent = AgentManager.byName(ctx.getSource().getServer(), name).orElseThrow(NO_SUCH_AGENT::create);
        if (!Permissions.canCommand(ctx.getSource(), agent)) throw NOT_ALLOWED.create();
        return agent;
    }

    static void ok(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String profile) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        AgentManager.SpawnResult result = AgentManager.spawn(player, name, profile);
        if (result instanceof AgentManager.SpawnResult.Error err) {
            ctx.getSource().sendFailure(Component.literal(err.message()));
            return 0;
        }
        AgentEntity agent = ((AgentManager.SpawnResult.Ok) result).agent();
        AiCommandExtensions.onSpawned(agent);
        ok(ctx, "Spawned " + name + " using profile '" + profile + "'. Talk to it with @" + name + " <message>.");
        return 1;
    }

    private static int despawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity agent = agent(ctx);
        agent.getAgentBrain().cancel("despawned");
        agent.discard();
        ok(ctx, "Despawned " + agent.getAgentName() + ".");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<AgentEntity> agents = AgentManager.all(ctx.getSource().getServer());
        if (agents.isEmpty()) {
            ok(ctx, "No agents. Spawn one with /ai spawn <name>.");
            return 0;
        }
        for (AgentEntity a : agents) {
            ServerPlayer owner = a.getOwnerPlayer();
            ok(ctx, "- " + a.getAgentName() + " | owner " + (owner != null ? owner.getGameProfile().name() : "offline")
                    + " | profile " + a.getProfileId() + " | mode " + a.getMode().id() + " | " + a.taskSummary());
        }
        return agents.size();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        ok(ctx, a.getAgentName() + ": health " + Math.round(a.getHealth()) + "/" + Math.round(a.getMaxHealth())
                + ", pos " + a.getBlockX() + " " + a.getBlockY() + " " + a.getBlockZ()
                + ", mode " + a.getMode().id() + ", profile " + a.getProfileId()
                + ", " + a.taskSummary() + (a.isThinking() ? ", thinking" : ""));
        if (!a.getAgentBrain().lastReply().isEmpty()) ok(ctx, "Last reply: " + a.getAgentBrain().lastReply());
        return 1;
    }

    private static int tell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        String msg = StringArgumentType.getString(ctx, "message");
        a.getAgentBrain().submitFromPlayer(PromptBuilder.chatInput(ctx.getSource().getTextName(), msg), ctx.getSource().getTextName());
        ok(ctx, "Sent to " + a.getAgentName() + ".");
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        String m = StringArgumentType.getString(ctx, "mode");
        AgentMode mode = AgentMode.parse(m).orElse(null);
        if (mode == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown mode. Use follow, stay, idle, guard or autonomous."));
            return 0;
        }
        a.setMode(mode);
        ok(ctx, a.getAgentName() + " is now in " + mode.id() + " mode.");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        a.stopEverything("stopped by " + ctx.getSource().getTextName());
        ok(ctx, "Stopped " + a.getAgentName() + ".");
        return 1;
    }

    private static int stopAll(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (AgentEntity a : AgentManager.all(ctx.getSource().getServer())) {
            if (Permissions.canCommand(ctx.getSource(), a)) {
                a.stopEverything("stop all");
                n++;
            }
        }
        final int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + count + " agent(s).").withStyle(ChatFormatting.YELLOW), true);
        return n;
    }

    private static int debugSnapshot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        for (String line : SnapshotBuilder.capture(a).toPromptText().split("\n")) ok(ctx, line);
        return 1;
    }

    private static int debugPrompt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AgentEntity a = agent(ctx);
        com.joserayos.aiplayers.AiPlayersMod.LOGGER.info("Last prompt for {}:\n{}", a.getAgentName(), a.getAgentBrain().debugLastExchange());
        ok(ctx, "Last prompt and response for " + a.getAgentName() + " written to the server log.");
        return 1;
    }
}
