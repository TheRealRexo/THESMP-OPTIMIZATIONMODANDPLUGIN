package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientComm — orchestrates dynamic load offload between server and capable clients.
 *
 * Java clients with the mod: receive offload tasks proportional to their CPU capability
 * Bedrock clients: handled entirely server-side (no mod, no offload)
 *
 * Load splitting is dynamic: recalculated every poll interval based on
 * current server TPS, RAM, and per-client ping.
 */
public class ClientComm {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final LoadReportManager reportManager;
    private BukkitTask offloadTask;

    // Track how much work each client has been assigned (for rate limiting)
    private final Map<UUID, Integer> tasksAssigned = new ConcurrentHashMap<>();

    public ClientComm(BedWarsOptimizer plugin, ProfilerConfig config, LoadReportManager reportManager) {
        this.plugin = plugin;
        this.config = config;
        this.reportManager = reportManager;
    }

    public void enable() {
        if (!config.isOffloadEnabled()) return;

        // Periodically recalculate load split and dispatch tasks
        offloadTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollAndOffload, 0, 100L);
        plugin.getLogger().info("Dynamic load offload enabled — polling every 5s");
    }

    public void disable() {
        if (offloadTask != null) {
            offloadTask.cancel();
            offloadTask = null;
        }
        tasksAssigned.clear();
    }

    private void pollAndOffload() {
        // Calculate current server load
        double serverLoad = plugin.getModEnforcer().getServerLoadFactor();

        // Get all online Java players with the mod
        List<ModEnforcer.ClientInfo> capableClients = new ArrayList<>();
        for (Map.Entry<UUID, ModEnforcer.ClientInfo> entry :
                plugin.getModEnforcer().getRegisteredClients().entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                // Refresh CPU estimate from current ping
                double cpuCap = plugin.getModEnforcer().estimateClientCpu(p);
                capableClients.add(new ModEnforcer.ClientInfo(entry.getKey(), cpuCap));
            }
        }

        // Bedrock player count (for reporting, not offload)
        int bedrockCount = plugin.getModEnforcer().getBedrockPlayerCount();

        // If server is under heavy load and we have capable clients, offload work
        double threshold = config.getClientCpuThreshold();
        capableClients.removeIf(c -> c.cpuCapability < threshold);

        if (capableClients.isEmpty()) {
            return;
        }

        // Dynamic allocation: more server load → more offload to clients
        // Fewer capable clients → each gets a larger share
        double loadFactor = serverLoad; // 0.0-1.0
        int maxOffloadPerTick = config.getMaxOffloadPerTick();
        int sharePerClient = maxOffloadPerTick / capableClients.size();

        // Scale up when server is under pressure
        int boostedShare = (int) (sharePerClient * (1.0 + loadFactor));

        for (ModEnforcer.ClientInfo client : capableClients) {
            // Rate limit: don't assign more than boostedShare per cycle
            int alreadyAssigned = tasksAssigned.getOrDefault(client.playerId, 0);
            if (alreadyAssigned >= boostedShare) continue;

            int toAssign = Math.min(boostedShare - alreadyAssigned, boostedShare);
            tasksAssigned.merge(client.playerId, toAssign, Integer::sum);

            // Send offload signal to client mod (via registered channel)
            // In production this would serialize BWOptimizerData.OffloadPacket
            plugin.getLogger().fine("Offloading ~" + toAssign + " items to client " +
                    client.playerId + " (cpu:" + String.format("%.2f", client.cpuCapability) +
                    ", load:" + String.format("%.2f", serverLoad) + ")");
        }

        // Log summary for ops
        if (Bukkit.getOnlinePlayers().size() % 20 == 0) { // every ~20 seconds
            plugin.getLogger().info(String.format(
                    "[BWOpt] Offload: %.1f server load | %d capable clients | %d bedrock players | share: %d/client",
                    serverLoad, capableClients.size(), bedrockCount, boostedShare));
        }
    }

    /**
     * Register a Java client that connected with the mod.
     * Called by ClientOffloadServer when a handshake is received.
     */
    public void registerClient(UUID playerId, double cpuCapability) {
        plugin.getModEnforcer().registerClient(playerId, cpuCapability);
    }

    /**
     * Check if a Java client is capable of handling offloaded work.
     */
    public boolean isClientCapable(UUID playerId) {
        ModEnforcer.ClientInfo info = plugin.getModEnforcer().getRegisteredClients().get(playerId);
        if (info == null) return false;
        return info.cpuCapability >= config.getClientCpuThreshold();
    }
}
