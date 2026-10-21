package com.joserayos.aiplayers.llm;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One message in a conversation, in the mod's internal provider-neutral format.
 *
 * @param role       system, user, assistant or tool
 * @param content    text content (may be empty for assistant messages that only call tools)
 * @param toolCalls  tool calls made by the assistant (assistant role only)
 * @param toolCallId id of the tool call this message answers (tool role only)
 * @param toolName   name of the tool this message answers (tool role only)
 */
public record ChatMessage(Role role, String content, List<ToolCall> toolCalls, @Nullable String toolCallId, @Nullable String toolName) {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public ChatMessage {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage system(String text) { return new ChatMessage(Role.SYSTEM, text, List.of(), null, null); }
    public static ChatMessage user(String text) { return new ChatMessage(Role.USER, text, List.of(), null, null); }
    public static ChatMessage assistant(String text) { return new ChatMessage(Role.ASSISTANT, text, List.of(), null, null); }
    public static ChatMessage assistant(String text, List<ToolCall> calls) { return new ChatMessage(Role.ASSISTANT, text, calls, null, null); }
    public static ChatMessage toolResult(String callId, String toolName, String result) { return new ChatMessage(Role.TOOL, result, List.of(), callId, toolName); }
}
