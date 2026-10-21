package com.joserayos.aiplayers.brain.tools;

/**
 * Outcome of executing one tool call on the server thread.
 *
 * @param ok          whether the call was accepted
 * @param message     result text fed back to the model
 * @param startedTask true if a task was queued (its result arrives later)
 * @param needsFollowUp true if the model should see this result right away (info tools)
 */
public record ToolResult(boolean ok, String message, boolean startedTask, boolean needsFollowUp) {
    public static ToolResult done(String message) { return new ToolResult(true, message, false, false); }
    public static ToolResult info(String message) { return new ToolResult(true, message, false, true); }
    public static ToolResult task(String message) { return new ToolResult(true, message, true, false); }
    public static ToolResult error(String message) { return new ToolResult(false, "ERROR: " + message, false, true); }
}
