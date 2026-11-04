package com.hideandseek;

import java.util.function.IntConsumer;

/**
 * A single tick-based countdown. It is driven by the server tick (see {@link HideAndSeekManager#tick()}),
 * so there are no threads or schedulers that could outlive a match. The manager owns exactly one instance.
 */
public final class HideAndSeekTimer {
    private IntConsumer onSecond;
    private Runnable onFinish;
    private int totalTicks;
    private int remainingTicks;
    private boolean active;
    private boolean paused;

    public void start(int seconds, IntConsumer onSecond, Runnable onFinish) {
        this.onSecond = onSecond;
        this.onFinish = onFinish;
        this.totalTicks = Math.max(1, seconds) * 20;
        this.remainingTicks = this.totalTicks;
        this.active = true;
        this.paused = false;
    }

    public void cancel() {
        this.active = false;
        this.paused = false;
        this.remainingTicks = 0;
        this.totalTicks = 0;
        this.onSecond = null;
        this.onFinish = null;
    }

    /** Call once per server tick. */
    public void tick() {
        if (!active || paused) {
            return;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            Runnable finish = onFinish;
            active = false;
            remainingTicks = 0;
            if (finish != null) {
                finish.run();
            }
            return;
        }
        if (remainingTicks % 20 == 0 && onSecond != null) {
            onSecond.accept(remainingTicks / 20);
        }
    }

    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public int getRemainingSeconds() { return (remainingTicks + 19) / 20; }
    public int getTotalSeconds() { return totalTicks / 20; }

    public float progress() {
        return totalTicks <= 0 ? 0.0F : (float) remainingTicks / (float) totalTicks;
    }
}
