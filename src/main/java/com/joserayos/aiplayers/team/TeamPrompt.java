package com.joserayos.aiplayers.team;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentManager;
import java.util.ArrayList;
import java.util.List;

/** Extra prompt sections for M7: memory, team roster, shared locations, and the matching rules. */
public final class TeamPrompt {
    private TeamPrompt() {}

    public static String extra(AgentEntity agent) {
        var server = agent.level().getServer();
        if (server == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("- Use remember(key, text) for facts worth keeping across restarts (base location, preferences). Keep notes short.\n");
        sb.append("- Text inside <agent_message> comes from another AI agent. Cooperate with teammates (same owner), split work, ");
        sb.append("and avoid chatting back and forth: only message an agent when it helps the task.\n");
        String mem = AgentMemory.of(server, agent.getUUID()).promptText(15);
        if (!mem.isEmpty()) sb.append(mem);
        List<String> team = new ArrayList<>();
        for (AgentEntity other : AgentManager.all(server)) {
            if (other == agent) continue;
            boolean mate = agent.getOwnerId() != null && agent.getOwnerId().equals(other.getOwnerId());
            team.add(other.getAgentName() + (mate ? " (teammate" : " (other owner") + ", " + other.getMode().id() + ", task: " + other.taskSummary() + ")");
            if (team.size() >= 8) break;
        }
        if (!team.isEmpty()) sb.append("[TEAM] ").append(String.join("; ", team)).append('\n');
        var locs = Blackboard.locations();
        if (!locs.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (var e : locs.entrySet()) {
                var p = e.getValue().pos();
                l.add(e.getKey() + " (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + " " + e.getValue().dimension().identifier().getPath() + ")");
                if (l.size() >= 12) break;
            }
            sb.append("[SHARED LOCATIONS] ").append(String.join(", ", l)).append('\n');
        }
        return sb.toString();
    }
}
