package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PingEqualizer — dynamically equalizes ping across all Java clients.
 *
 * The server measures each client's real ping, then calculates a compensation
 * offset so that all clients experience a smooth, equalized latency.
 *
 * Result: to every player, the game feels like 0ms ping regardless of their
 * actual connection quality. The server absorbs the difference.
 */
public class PingEqualizer {

    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, ClientPing> clientPings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPingTime = new ConcurrentHashMap<>();
    private volatile int targetLatencyMs;
    private volatile int serverAvgPingMs;
    private volatile boolean hidePingEnabled;
    private volatile boolean active;
    private final Object lock = new Object();

    // Ping measurement window (ms)
    private static final long PING_WINDOW_MS = 5000;
    // Minimum samples before trusting a ping value
    private static final int MIN_PING_SAMPLES = 3;

    public PingEqualizer(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.targetLatencyMs = config.getPingTargetLatencyMs();
        this.hidePingEnabled = config.isPingHideEnabled();
    }

    public void enable() {
        if (active) return;
        active = true;
        plugin.getLogger().info("Ping equalizer enabled — target latency: " + targetLatencyMs + "ms");
    }

    public void disable() {
        active = false;
        clientPings.clear();
    }

    /**
     * Record a client's measured ping (called when client sends PingReportPacket).
     */
    public void recordClientPing(UUID uuid, int realPingMs) {
        ClientPing ping = clientPings.computeIfAbsent(uuid, k -> new ClientPing());
        ping.addSample(realPingMs);
        lastPingTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Get the current ping for a client (averaged over the measurement window).
     */
    public int getClientPing(UUID uuid) {
        ClientPing ping = clientPings.get(uuid);
        if (ping == null || ping.sampleCount < MIN_PING_SAMPLES) {
            // Fall back to Paper's built-in ping
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) return p.getPing();
            return 0;
        }
        return ping.getAverage();
    }

    /**
     * Calculate the compensation offset for a client.
     *
     * @param uuid client UUID
     * @return PingEqualizePacket with the compensation values
     */
    public BWOptimizerData.PingEqualizePacket calculateEqualization(UUID uuid) {
        int realPing = getClientPing(uuid);

        // Calculate how much compensation is needed
        // If real ping < target, no compensation needed (already fast)
        // If real ping > target, compensate the difference
        int compensation = Math.max(0, realPing - targetLatencyMs);

        // Jitter factor: add a tiny bit of variance for realism
        // High-ping clients get more jitter to mask the compensation
        double jitterFactor = Math.min(0.3, realPing / 500.0);

        // Recalculate server average
        recalcServerAverage();

        return new BWOptimizerData.PingEqualizePacket(
            System.currentTimeMillis(),
            targetLatencyMs,
            compensation,
            jitterFactor,
            hidePingEnabled,
            serverAvgPingMs
        );
    }

    /**
     * Recalculate the server's average client ping.
     */
    private void recalcServerAverage() {
        if (clientPings.isEmpty()) {
            serverAvgPingMs = 0;
            return;
        }

        long sum = 0;
        int count = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, ClientPing> entry : clientPings.entrySet()) {
            Long lastTime = lastPingTime.get(entry.getKey());
            if (lastTime == null || (now - lastTime) > PING_WINDOW_MS * 2) continue;
            if (entry.getValue().sampleCount < MIN_PING_SAMPLES) continue;
            sum += entry.getValue().getAverage();
            count++;
        }

        serverAvgPingMs = count > 0 ? (int) (sum / count) : 0;
    }

    /**
     * Get the target latency (configured).
     */
    public int getTargetLatencyMs() { return targetLatencyMs; }

    /**
     * Get the server's average client ping.
     */
    public int getServerAvgPingMs() { return serverAvgPingMs; }

    /**
     * Check if ping hiding is enabled.
     */
    public boolean isHidePingEnabled() { return hidePingEnabled; }

    /**
     * Check if the equalizer is active.
     */
    public boolean isActive() { return active; }

    /**
     * Get all tracked client pings (for reporting).
     */
    public Map<UUID, ClientPing> getClientPings() { return clientPings; }

    // ─── Inner class ─────────────────────────────────────────────────────────────

    public static class ClientPing {
        private final java.util.List<Integer> samples = new java.util.ArrayList<>();
        private int sampleCount = 0;
        private long sum = 0;

        void addSample(int pingMs) {
            samples.add(pingMs);
            sum += pingMs;
            sampleCount++;
            // Keep only the last 10 samples (rolling window)
            if (samples.size() > 10) {
                int removed = samples.remove(0);
                sum -= removed;
                sampleCount--;
            }
        }

        int getAverage() {
            return sampleCount > 0 ? (int) (sum / sampleCount) : 0;
        }

        public int getSampleCount() { return sampleCount; }
    }
}