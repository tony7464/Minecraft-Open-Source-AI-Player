package com.joserayos.aiplayers.brain.tools;

import com.google.gson.JsonObject;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.llm.ToolSpec;

/** A skill exposed to the model. The handler runs on the server thread and must validate its arguments. */
public record Tool(String name, String description, JsonObject parameters, Group group, Handler handler) {
    /** Tool groups decide which tools are offered in which mode (small models get confused by long lists). */
    public enum Group { TALK, MOVE, ITEMS, INFO, GATHER, CRAFT, BUILD, FIGHT, MEMORY, PLAN }

    @FunctionalInterface
    public interface Handler {
        ToolResult run(AgentEntity agent, ToolArgs args, ToolContext ctx);
    }

    public ToolSpec spec() {
        return new ToolSpec(name, description, parameters);
    }
}
