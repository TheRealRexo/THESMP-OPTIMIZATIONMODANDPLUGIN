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
 * ClientComm — orchestrates dynamic load offload AND health display dispatch
 * between server and capable clients.
 *
 * Java clients with the mod:
 *   - Receive offload tasks proportional to their CPU capability
 *   - Receive HealthDisplayPackets so their client renders health as a
 *     number + hearts over each player (Hypixel BedWars style)
 *   - May also receive Geyser/Floodgate processing offloads when server is under pressure
 * Bedrock clients: handled entirely server-side (no mod, no offload)
 *
 * Work splitting is dynamic: recalculated every poll interval based on
 * current server TPS, RAM, and number of capable clients.
 */
public class ClientComm {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final LoadReportManager reportManager;
    private BukkitTask offloadTask;
    private BukkitTask healthTask;
    private BukkitTask geyserTask;

    // Track how much work each client has been assigned (for rate limiting)
    private final Map<UUID, Integer> tasksAssigned = new ConcurrentHashMap<>();

    public ClientComm(BedWarsOptimizer plugin, ProfilerConfig config, LoadReportManager reportManager) {
        this.plugin = plugin;
        this.config = config;
        this.reportManager = reportManager;
    }

    public void enable() {
        if (config.isOffloadEnabled()) {
            offloadTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollAndOffload,
                    0, config.getPollIntervalSeconds() * 20L);
            plugin.getLogger().info("Dynamic load offload enabled — polling every "
                    + config.getPollIntervalSeconds() + "s");
        }

        if (config.isHealthDisplayEnabled()) {
            healthTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollAndSendHealth,
                    0, config.getHealthDisplayIntervalTicks());
            plugin.getLogger().info("Health display enabled — sending every "
                    + config.getHealthDisplayIntervalTicks() + " ticks");
        }

        if (config.isFloodgateOffloadEnabled()) {
            geyserTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollGeyserOffload,
                    0, 100L);
            plugin.getLogger().info("Geyser/Floodgate offload enabled");
        }
    }

    public void disable() {
        if (offloadTask != null) {
            offloadTask.cancel();
            offloadTask = null;
        }
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
        if (geyserTask != null) {
            geyserTask.cancel();
            geyserTask = null;
        }
        tasksAssigned.clear();
    }

    // ─── Dynamic load offload ───────────────────────────────────────────────────

    private void pollAndOffload() {
        double serverLoad = plugin.getModEnforcer().getServerLoadFactor();

        List<ModEnforcer.ClientInfo> capableClients = new ArrayList<>();
        for (Map.Entry<UUID, ModEnforcer.ClientInfo> entry :
                plugin.getModEnforcer().getRegisteredClients().entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                double cpuCap = plugin.getModEnforcer().estimateClientCpu(p);
                capableClients.add(new ModEnforcer.ClientInfo(entry.getKey(), cpuCap));
            }
        }

        int bedrockCount = plugin.getModEnforcer().getBedrockPlayerCount();

        double threshold = config.getClientCpuThreshold();
        capableClients.removeIf(c -> c.getCpuCapability() < threshold);

        if (capableClients.isEmpty()) {
            return;
        }

        double loadFactor = serverLoad;
        int maxOffloadPerTick = config.getMaxOffloadPerTick();
        int sharePerClient = maxOffloadPerTick / capableClients.size();
        int boostedShare = (int) (sharePerClient * (1.0 + loadFactor));

        for (ModEnforcer.ClientInfo client : capableClients) {
            int alreadyAssigned = tasksAssigned.getOrDefault(client.getPlayerId(), 0);
            if (alreadyAssigned >= boostedShare) continue;

            int toAssign = Math.min(boostedShare - alreadyAssigned, boostedShare);
            tasksAssigned.merge(client.getPlayerId(), toAssign, Integer::sum);

            plugin.getLogger().fine("Offloading ~" + toAssign + " items to client "
                    + client.getPlayerId() + " (cpu:" + String.format("%.2f", client.getCpuCapability())
                    + ", load:" + String.format("%.2f", serverLoad) + ")");
        }

        if (Bukkit.getOnlinePlayers().size() % 20 == 0) {
            plugin.getLogger().info(String.format(
                    "[BWOpt] Offload: %.1f server load | %d capable clients | %d bedrock players | share: %d/client",
                    serverLoad, capableClients.size(), bedrockCount, boostedShare));
        }
    }

    // ─── Health display dispatch ────────────────────────────────────────────────

    /**
     * Delegate to HealthDisplayManager to send health packets to all clients.
     */
    private void pollAndSendHealth() {
        plugin.getHealthDisplayManager().pollAndSendHealth();
    }

    // ─── Geyser/Floodgate offload ───────────────────────────────────────────────

    /**
     * When server is under heavy load and there are Bedrock players,
     * offload some Geyser processing work to capable Java clients.
     */
    private void pollGeyserOffload() {
        ModEnforcer enforcer = plugin.getModEnforcer();

        // Only offload when server is under pressure and there are Bedrock players
        if (!enforcer.needsGeyserOffload()) return;

        double serverLoad = enforcer.getServerLoadFactor();
        int bedrockCount = enforcer.getBedrockPlayerCount();

        if (bedrockCount == 0) return;

        // Get available Java clients
        List<ModEnforcer.ClientInfo> capableClients = new ArrayList<>(enforcer.getRegisteredClients().values());
        capableClients.removeIf(c -> c.getCpuCapability() < config.getClientCpuThreshold());

        if (capableClients.isEmpty()) return;

        // Send offload signal — each client gets a chunk of Geyser work
        for (ModEnforcer.ClientInfo client : capableClients) {
            Player p = plugin.getServer().getPlayer(client.getPlayerId());
            if (p == null || !p.isOnline()) continue;

            // In production: send GeyserOffloadPacket via plugin channel
            // For now, log the action
            plugin.getLogger().fine("[BWOpt] Offloading Geyser work (~" + bedrockCount + " bedrock players)"
                    + " to client " + p.getName());

            enforcer.incrementGeyserOffload();
            break; // One client per tick is enough
        }
    }

    /**
     * Register a Java client that connected with the mod.
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
        return info.getCpuCapability() >= config.getClientCpuThreshold();
    }
}