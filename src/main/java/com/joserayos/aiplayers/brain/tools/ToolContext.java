package com.joserayos.aiplayers.brain.tools;

/** Context for a tool call: who the agent was talking to when it decided to act. */
public record ToolContext(String speakerName) {}
