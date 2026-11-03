package com.rexo.bedwars.clienthelper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-side ping equalizer — hides real ping and applies server compensation.
 *
 * The server sends PingEqualizePackets that tell the client:
 *   - What the target equalized latency should be
 *   - How much compensation to apply
 *   - Whether to force-display 0ms ping
 *
 * This client mod:
 *   - Intercepts the ping value reported to the game (forces 0ms if hidePing=true)
 *   - Applies pre-input delay compensation so the game feels smooth
 *   - Tracks real ping for verification reports back to server
 */
public class PingEqualizerManager {

    private static final PingEqualizerManager instance = new PingEqualizerManager();

    private final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    private final AtomicReference<BWOptimizerData.PingEqualizePacket> lastPacket =
            new AtomicReference<>(null);
    private final AtomicInteger reportedPing = new AtomicInteger(0);
    private final AtomicLong lastCompensatedInput = new AtomicLong(0);
    private final AtomicLong compensationApplied = new AtomicLong(0);
    private volatile boolean active = false;
    private volatile boolean hidePing = true;
    private volatile int targetLatencyMs = 15;
    private volatile int compensationMs = 0;
    private volatile double jitterFactor = 0.0;

    public static PingEqualizerManager getInstance() {
        return instance;
    }

    /**
     * Initialize the ping equalizer.
     * Called by the loader-specific client helper on mod init.
     */
    public void init() {
        if (active) return;
        active = true;
        System.out.println("[BWOpt] Ping equalizer initialized — target: " + targetLatencyMs + "ms");
    }

    /**
     * Apply a PingEqualizePacket received from the server.
     */
    public void applyPingEqualization(BWOptimizerData.PingEqualizePacket packet) {
        lastPacket.set(packet);
        this.hidePing = packet.hidePing;
        this.targetLatencyMs = packet.targetLatencyMs;
        this.compensationMs = packet.compensationMs;
        this.jitterFactor = packet.jitterFactor;

        // Force the reported ping to 0 if hidePing is enabled
        if (hidePing) {
            reportedPing.set(0);
        }

        System.out.println("[BWOpt] Ping equalized — target: " + targetLatencyMs
                + "ms, compensation: " + compensationMs + "ms"
                + (hidePing ? " (ping hidden)" : ""));
    }

    /**
     * Get the ping value to report to the game.
     * Returns 0 if hidePing is enabled, otherwise the real ping.
     */
    public int getReportedPing() {
        return hidePing ? 0 : reportedPing.get();
    }

    /**
     * Set the real measured ping (called by the network layer).
     */
    public void setRealPing(int realPingMs) {
        reportedPing.set(realPingMs);
    }

    /**
     * Apply input compensation — delays input slightly to smooth out jitter.
     * Returns the number of milliseconds to delay the next input.
     */
    public long applyInputCompensation() {
        if (compensationMs <= 0) return 0;

        long now = System.currentTimeMillis();
        long last = lastCompensatedInput.get();
        long elapsed = now - last;

        // Apply compensation with jitter to avoid predictability
        long jitter = (long) (compensationMs * jitterFactor * (Math.random() * 2 - 1));
        long delay = Math.max(0, compensationMs + jitter);

        // Don't over-compensate: cap at target latency
        delay = Math.min(delay, targetLatencyMs * 2);

        lastCompensatedInput.set(now);
        compensationApplied.addAndGet(delay);

        return delay;
    }

    /**
     * Get the current equalization state for debugging.
     */
    public String getStatus() {
        return String.format("[BWOpt Ping] target=%dms comp=%dms jitter=%.2f hide=%s active=%s",
                targetLatencyMs, compensationMs, jitterFactor, hidePing, active);
    }

    public boolean isActive() { return active; }
    public int getTargetLatencyMs() { return targetLatencyMs; }
    public int getCompensationMs() { return compensationMs; }
    public boolean isHidePing() { return hidePing; }
    public long getTotalCompensationApplied() { return compensationApplied.get(); }
}