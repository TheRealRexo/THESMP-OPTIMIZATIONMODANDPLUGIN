package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientComm — communicates with client mods to offload CPU-intensive work.
 * Uses a simple TCP-like protocol over UDP (since Bedrock uses UDP).
 * For Java clients, we use a lightweight WebSocket server.
 */
public class ClientComm {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final LoadReportManager reportManager;
    private final Map<UUID, ClientInfo> connectedClients = new ConcurrentHashMap<>();
    private BukkitTask clientPollTask;
    private ClientOffloadServer server;

    // Client capabilities
    public static class ClientInfo {
        final UUID playerId;
        final double cpuCapability; // 0.0-1.0, how much CPU this client can handle
        long lastReportTime;

        ClientInfo(UUID playerId, double cpuCapability) {
            this.playerId = playerId;
            this.cpuCapability = cpuCapability;
            this.lastReportTime = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() { return playerId; }
        public double getCpuCapability() { return cpuCapability; }
    }

    public ClientComm(BedWarsOptimizer plugin, ProfilerConfig config, LoadReportManager reportManager) {
        this.plugin = plugin;
        this.config = config;
        this.reportManager = reportManager;
    }

    public void enable() {
        if (!config.isOffloadEnabled()) return;

        // Start WebSocket server for Java clients
        server = new ClientOffloadServer(this, config);
        server.start();

        // Periodically check for strong clients
        clientPollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollClients, 0, 100L);
    }

    public void disable() {
        if (clientPollTask != null) {
            clientPollTask.cancel();
            clientPollTask = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        connectedClients.clear();
    }

    private void pollClients() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            // Estimate client CPU based on player's average ping (inverse proxy for capability)
            double cpuCapability = estimateClientCpu(player);
            connectedClients.put(uuid, new ClientInfo(uuid, cpuCapability));
        }
    }

    private double estimateClientCpu(Player player) {
        // Simple heuristic: lower ping = better connection = likely stronger client
        // Range: 0.1 (weak/mobile) to 1.0 (strong PC)
        int ping = player.getPing();
        if (ping < 50) return 1.0;
        if (ping < 100) return 0.8;
        if (ping < 200) return 0.6;
        if (ping < 500) return 0.4;
        return 0.2;
    }

    public void registerClient(UUID playerId, double cpuCapability) {
        connectedClients.put(playerId, new ClientInfo(playerId, cpuCapability));
        plugin.getLogger().info("Client registered: " + playerId + " (cpu: " + String.format("%.2f", cpuCapability) + ")");
    }

    public void unregisterClient(UUID playerId) {
        connectedClients.remove(playerId);
    }

    public Map<UUID, ClientInfo> getConnectedClients() {
        return connectedClients;
    }

    /**
     * Send offload tasks to capable clients.
     * This is called periodically to distribute work.
     */
    public void sendOffloadTasks() {
        if (!config.isOffloadEnabled()) return;

        // Get hot entities and tiles from profiler
        var hotEntities = plugin.getProfiler().getHotEntities();
        var hotTiles = plugin.getProfiler().getHotTiles();

        // Find strong clients
        double threshold = config.getClientCpuThreshold();
        var strongClients = connectedClients.values().stream()
            .filter(c -> c.cpuCapability >= threshold)
            .toList();

        if (strongClients.isEmpty()) return;

        // Distribute work
        int itemsPerClient = config.getMaxOffloadPerTick() / strongClients.size();
        for (var client : strongClients) {
            // Send relevant hot spots to this client
            // In a full implementation, this would serialize the task list
            plugin.getLogger().info("Offloading " + itemsPerClient + " items to client " + client.playerId);
        }
    }

    public boolean isClientStrongEnough(UUID playerId) {
        ClientInfo info = connectedClients.get(playerId);
        return info != null && info.cpuCapability >= config.getClientCpuThreshold();
    }
}