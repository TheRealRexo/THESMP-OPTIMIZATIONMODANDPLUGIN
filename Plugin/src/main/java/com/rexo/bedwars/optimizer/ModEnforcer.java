package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ModEnforcer — manages client mod registration and dynamic load offload.
 *
 * Features (v1.1.0):
 *   - Java clients with mod v1.1.0+: receive health display + offload tasks
 *   - Java clients WITHOUT mod: kicked after grace period
 *   - Bedrock clients: handled server-side, may receive offloaded Geyser processing
 *
 * Dynamically splits workload based on:
 *   - Server TPS / load
 *   - Number of capable clients
 */
public class ModEnforcer implements Listener {

    private static final String MOD_VERSION = "1.1.0";
    private static final int KICK_GRACE_SECONDS = 5;

    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, ClientInfo> registeredClients = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTime = new ConcurrentHashMap<>();
    private boolean floodgateAvailable = false;
    private BukkitTask kickTask;
    private final AtomicInteger geyserOffloadCount = new AtomicInteger(0);

    public ModEnforcer(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
        // Probe for Floodgate API on startup
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateAvailable = true;
            plugin.getLogger().info("Floodgate API detected — Bedrock detection active");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Floodgate API not found — Geyser players will be treated as Java");
        }
    }

    public void enable() {
        if (kickTask != null) return;
        kickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkKicks, 0, 20L * KICK_GRACE_SECONDS);
    }

    public void disable() {
        if (kickTask != null) {
            kickTask.cancel();
            kickTask = null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        joinTime.put(uuid, System.currentTimeMillis());

        if (isBedrock(uuid)) {
            plugin.getLogger().fine("Bedrock player joined: " + player.getName());
            // Bedrock players can still receive offloaded work
            return;
        }

        // Java player — check if they have the mod
        if (!registeredClients.containsKey(uuid)) {
            plugin.getLogger().warning("Java player without mod v" + MOD_VERSION + ": " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        registeredClients.remove(uuid);
        joinTime.remove(uuid);
    }

    /**
     * Check if any Java players without the mod should be kicked.
     */
    private void checkKicks() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : joinTime.entrySet()) {
            UUID uuid = entry.getKey();
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (isBedrock(uuid)) continue;
            if (registeredClients.containsKey(uuid)) continue;

            long elapsed = (now - entry.getValue()) / 1000;
            if (elapsed >= KICK_GRACE_SECONDS) {
                player.kickPlayer("§c[BedWarsOptimizer] §7You must install mod v" + MOD_VERSION + " to play.");
                plugin.getLogger().info("Kicked " + player.getName() + " — no mod v" + MOD_VERSION);
            }
        }
    }

    /**
     * Called when a Java client mod sends its handshake packet.
     */
    public void registerClient(UUID playerId, double cpuCapability) {
        registeredClients.put(playerId, new ClientInfo(playerId, cpuCapability));
        joinTime.remove(playerId);
        plugin.getLogger().info("Client mod registered: " + playerId + " (cpu: " + String.format("%.2f", cpuCapability) + ")");

        Player p = plugin.getServer().getPlayer(playerId);
        if (p != null) {
            p.sendMessage("§a§lBedWarsOptimizer §7— mod detected! Load optimization active.");
        }
    }

    /**
     * Get number of registered (mod-equipped) Java clients.
     */
    public int getRegisteredCount() {
        return registeredClients.size();
    }

    /**
     * Get number of known Bedrock players on the server.
     */
    public int getBedrockPlayerCount() {
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> isBedrock(p.getUniqueId()))
                .count();
    }

    /**
     * Estimate CPU capability for a Java client based on hardware (ping excluded in v1.1).
     */
    public double estimateClientCpu(Player player) {
        if (player == null) return 0.5;
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 16) return 1.0;
        if (cores >= 8) return 0.9;
        if (cores >= 6) return 0.7;
        if (cores >= 4) return 0.5;
        return 0.3;
    }

    /**
     * Calculate server load factor (0.0 = idle, 1.0 = max load).
     * Based on TPS deficit and RAM usage.
     */
    public double getServerLoadFactor() {
        double[] tps = Bukkit.getTPS();
        double currentTps = Math.min(tps[0], 20.0);
        double tpsLoad = Math.max(0.0, (20.0 - currentTps) / 20.0);

        long totalMem = Runtime.getRuntime().totalMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        double ramLoad = (double) (totalMem - Runtime.getRuntime().freeMemory()) / maxMem;

        // Weighted combination: TPS matters more
        return Math.min(1.0, tpsLoad * 0.7 + ramLoad * 0.3);
    }

    /**
     * Check if the server has high load and needs to offload Geyser processing.
     */
    public boolean needsGeyserOffload() {
        return getServerLoadFactor() > 0.7 && getBedrockPlayerCount() > 0;
    }

    /**
     * Get the number of Geyser offload tasks completed.
     */
    public int getGeyserOffloadCount() {
        return geyserOffloadCount.get();
    }

    /**
     * Increment Geyser offload counter.
     */
    public void incrementGeyserOffload() {
        geyserOffloadCount.incrementAndGet();
    }

    /**
     * Get the list of registered Java clients with their estimated capabilities.
     */
    public Map<UUID, ClientInfo> getRegisteredClients() {
        return registeredClients;
    }

    // ─── Bedrock detection ───────────────────────────────────────────────────────

    /**
     * Check if a player is a Bedrock (Geyser/Floodgate) player.
     * Returns false if Floodgate is not present (conservative: treat as Java).
     */
    public boolean isBedrock(UUID uuid) {
        return isBedrockPlayer(uuid);
    }

    private boolean isBedrockPlayer(UUID uuid) {
        if (!floodgateAvailable) return false;
        try {
            Object api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                    .getMethod("getInstance")
                    .invoke(null);
            return (boolean) api.getClass().getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, uuid);
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Inner class ─────────────────────────────────────────────────────────────

    public static class ClientInfo {
        final UUID playerId;
        final double cpuCapability; // 0.0-1.0
        long lastReportTime;

        ClientInfo(UUID playerId, double cpuCapability) {
            this.playerId = playerId;
            this.cpuCapability = cpuCapability;
            this.lastReportTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public double getCpuCapability() { return cpuCapability; }
    }
}