package com.joserayos.aiplayers.task;

import com.joserayos.aiplayers.agent.AgentEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A unit of deterministic, tick-based work (a skill). {@link #tick} must do a small amount of work and return;
 * never loop or sleep. Runs on the server thread only.
 */
public interface AgentTask {
    /** Tool-style id, e.g. "go_to". */
    String id();

    /** Human/LLM-readable description including arguments, e.g. "go_to (10, 64, -3)". */
    String describe();

    void start(AgentEntity agent);

    TaskStatus tick(AgentEntity agent);

    /** Called when the task is cancelled or finished; clean up navigation etc. */
    default void stop(AgentEntity agent, String reason) {
        agent.getNavigation().stop();
    }

    /** Progress text such as "6/10 oak_log collected". */
    default String progress() { return ""; }

    /** Result text after SUCCESS, or the reason after FAILED. */
    String resultMessage();

    /** Maximum ticks before the executor fails the task. */
    default int timeoutTicks() { return 3 * 60 * 20; }

    /** Current movement goal, used by the stuck detector (null = not moving). */
    default @Nullable Vec3 movementGoal() { return null; }
}
