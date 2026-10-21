package com.joserayos.aiplayers.task;

import com.joserayos.aiplayers.agent.AgentEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs one task at a time from a small queue, with a per-task timeout and a stuck detector
 * (fails when the agent hasn't got closer to its movement goal for {@link #STUCK_TICKS}).
 */
public final class TaskExecutor {
    public static final int MAX_QUEUE = 5;
    public static final int STUCK_TICKS = 200;

    /** Called when a task ends (success or failure, not cancellation). */
    public interface Listener {
        void onTaskFinished(AgentTask task, TaskStatus status, String message);
    }

    private final AgentEntity agent;
    private final Listener listener;
    private final Deque<AgentTask> queue = new ArrayDeque<>();
    private @Nullable AgentTask current;
    private int ticksRunning;
    private double bestGoalDistance;
    private int ticksSinceProgress;

    public TaskExecutor(AgentEntity agent, Listener listener) {
        this.agent = agent;
        this.listener = listener;
    }

    public boolean isIdle() { return current == null && queue.isEmpty(); }

    public @Nullable AgentTask current() { return current; }

    public List<AgentTask> queued() { return List.copyOf(queue); }

    /** Adds a task; returns false if the queue is full. */
    public boolean enqueue(AgentTask task) {
        if (queue.size() >= MAX_QUEUE) return false;
        queue.addLast(task);
        return true;
    }

    /** Cancel the running task and clear the queue. */
    public void clear(String reason) {
        queue.clear();
        if (current != null) {
            current.stop(agent, reason);
            current = null;
        }
    }

    public void tick() {
        if (current == null) {
            current = queue.pollFirst();
            if (current == null) return;
            ticksRunning = 0;
            bestGoalDistance = Double.MAX_VALUE;
            ticksSinceProgress = 0;
            current.start(agent);
        }
        AgentTask task = current;
        TaskStatus status;
        String failure = null;
        try {
            status = task.tick(agent);
        } catch (RuntimeException e) {
            status = TaskStatus.FAILED;
            failure = "internal error: " + e.getMessage();
        }
        ticksRunning++;
        if (status == TaskStatus.RUNNING) {
            if (ticksRunning > task.timeoutTicks()) {
                status = TaskStatus.FAILED;
                failure = "timed out after " + task.timeoutTicks() / 20 + "s (" + task.progress() + ")";
            } else if (isStuck(task)) {
                status = TaskStatus.FAILED;
                failure = "stuck: no progress toward the target for " + STUCK_TICKS / 20 + "s (" + task.progress() + ")";
            }
        }
        if (status != TaskStatus.RUNNING) {
            current = null;
            task.stop(agent, status.name().toLowerCase());
            String msg = failure != null ? failure : task.resultMessage();
            listener.onTaskFinished(task, status, msg);
        }
    }

    private boolean isStuck(AgentTask task) {
        Vec3 goal = task.movementGoal();
        if (goal == null) {
            ticksSinceProgress = 0;
            bestGoalDistance = Double.MAX_VALUE;
            return false;
        }
        double d = agent.position().distanceTo(goal);
        if (d < bestGoalDistance - 1.0) {
            bestGoalDistance = d;
            ticksSinceProgress = 0;
        } else {
            ticksSinceProgress++;
        }
        return ticksSinceProgress > STUCK_TICKS;
    }

    public String summary() {
        if (current == null) return queue.isEmpty() ? "task: none" : "task: starting " + queue.peekFirst().describe();
        String p = current.progress();
        return "task: " + current.describe() + (p.isEmpty() ? "" : " [" + p + "]") + (queue.isEmpty() ? "" : " (+" + queue.size() + " queued)");
    }
}
