package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModEnforcer — manages client mod registration and dynamic load offload.
 *
 * Java clients with the mod: registered → receive offload tasks based on their ping/CPU capability
 * Bedrock clients (no mod): skipped entirely → no enforcement, no kicking
 *
 * Dynamically splits workload based on:
 *   - Client ping (inverse proxy for CPU capability)
 *   - Server TPS / load
 *   - Number of capable clients
 */
public class ModEnforcer implements Listener {

    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, ClientInfo> registeredClients = new ConcurrentHashMap<>();
    private boolean floodgateAvailable = false;

    public ModEnforcer(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
        // Probe for Floodgate API on startup
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateAvailable = true;
            plugin.getLogger().info("Floodgate API detected — Bedrock players will be exempt from mod enforcement");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Floodgate API not found — unable to detect Bedrock players");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Skip Bedrock players — they can't have the Java client mod
        if (isBedrockPlayer(uuid)) {
            plugin.getLogger().fine("Bedrock player detected: " + player.getName() + " — skipping mod enforcement");
            return;
        }

        // Java players start unregistered; they self-register via ClientComm.registerClient()
        plugin.getLogger().fine("Java player joined: " + player.getName() + " — waiting for mod handshake");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        registeredClients.remove(uuid);
    }

    /**
     * Called when a Java client mod sends its handshake packet.
     */
    public void registerClient(UUID playerId, double cpuCapability) {
        registeredClients.put(playerId, new ClientInfo(playerId, cpuCapability));
        plugin.getLogger().info("Client mod registered: " + playerId + " (cpu: " + String.format("%.2f", cpuCapability) + ")");

        Player p = plugin.getServer().getPlayer(playerId);
        if (p != null) {
            p.sendMessage("§a§lBedWarsOptimizer §7— mod detected! Load optimization active.");
        }
    }

    /**
     * Check if a player has the mod registered.
     */
    public boolean hasMod(UUID uuid) {
        return registeredClients.containsKey(uuid);
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
                .filter(p -> isBedrockPlayer(p.getUniqueId()))
                .count();
    }

    /**
     * Estimate CPU capability for a Java client based on ping.
     * Lower ping → higher capability (inverse proxy for connection/PC quality).
     */
    public double estimateClientCpu(Player player) {
        if (player == null) return 0.5;
        int ping = player.getPing();
        if (ping < 30) return 1.0;
        if (ping < 60) return 0.9;
        if (ping < 100) return 0.8;
        if (ping < 150) return 0.7;
        if (ping < 250) return 0.6;
        if (ping < 400) return 0.5;
        if (ping < 600) return 0.4;
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
