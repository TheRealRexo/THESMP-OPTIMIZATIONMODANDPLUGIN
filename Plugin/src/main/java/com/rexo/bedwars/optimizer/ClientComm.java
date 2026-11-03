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
 * ClientComm — orchestrates dynamic load offload AND ping equalization
 * between server and capable clients.
 *
 * Java clients with the mod:
 *   - Receive offload tasks proportional to their CPU capability
 *   - Receive PingEqualizePackets so their displayed/experienced ping is equalized
 * Bedrock clients: handled entirely server-side (no mod, no offload, no ping-hiding)
 *
 * Load splitting is dynamic: recalculated every poll interval based on
 * current server TPS, RAM, and per-client ping.
 */
public class ClientComm {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final LoadReportManager reportManager;
    private BukkitTask offloadTask;
    private BukkitTask pingTask;

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

        if (config.isPingEqualizationEnabled() && plugin.getPingEqualizer().isActive()) {
            pingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollAndEqualizePing,
                    0, config.getPingPollIntervalSeconds() * 20L);
            plugin.getLogger().info("Ping equalization enabled — recalculating every "
                    + config.getPingPollIntervalSeconds() + "s");
        }
    }

    public void disable() {
        if (offloadTask != null) {
            offloadTask.cancel();
            offloadTask = null;
        }
        if (pingTask != null) {
            pingTask.cancel();
            pingTask = null;
        }
        tasksAssigned.clear();
    }

    // ─── Dynamic load offload ───────────────────────────────────────────────────

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
        capableClients.removeIf(c -> c.getCpuCapability() < threshold);

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
            int alreadyAssigned = tasksAssigned.getOrDefault(client.getPlayerId(), 0);
            if (alreadyAssigned >= boostedShare) continue;

            int toAssign = Math.min(boostedShare - alreadyAssigned, boostedShare);
            tasksAssigned.merge(client.getPlayerId(), toAssign, Integer::sum);

            // Send offload signal to client mod (via registered channel)
            // In production this would serialize BWOptimizerData.OffloadPacket
            plugin.getLogger().fine("Offloading ~" + toAssign + " items to client " +
                    client.getPlayerId() + " (cpu:" + String.format("%.2f", client.getCpuCapability()) +
                    ", load:" + String.format("%.2f", serverLoad) + ")");
        }

        // Log summary for ops
        if (Bukkit.getOnlinePlayers().size() % 20 == 0) {
            plugin.getLogger().info(String.format(
                    "[BWOpt] Offload: %.1f server load | %d capable clients | %d bedrock players | share: %d/client",
                    serverLoad, capableClients.size(), bedrockCount, boostedShare));
        }
    }

    // ─── Ping equalization ──────────────────────────────────────────────────────

    /**
     * Recalculate ping compensation for every online Java client with the mod
     * and send a PingEqualizePacket.
     */
    private void pollAndEqualizePing() {
        PingEqualizer eq = plugin.getPingEqualizer();
        if (!eq.isActive()) return;

        int updated = 0;
        for (Map.Entry<UUID, ModEnforcer.ClientInfo> entry :
                plugin.getModEnforcer().getRegisteredClients().entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;

            // Bedrock players are never touched
            if (plugin.getModEnforcer().isBedrock(entry.getKey())) continue;

            // Record the server-side ping measurement for this client
            eq.recordClientPing(p.getUniqueId(), p.getPing());

            // Calculate and send the equalization packet
            BWOptimizerData.PingEqualizePacket packet = eq.calculateEqualization(p.getUniqueId());
            sendPingEqualizePacket(p, packet);
            updated++;
        }

        if (updated > 0 && plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
            plugin.getLogger().fine("[BWOpt] Ping equalized for " + updated +
                    " clients (avg: " + eq.getServerAvgPingMs() + "ms, target: " +
                    eq.getTargetLatencyMs() + "ms)");
        }
    }

    /**
     * Send a ping equalization packet to a client via the plugin channel.
     * Falls back to a logged no-op if the channel isn't available (offline dev).
     */
    private void sendPingEqualizePacket(Player player, BWOptimizerData.PingEqualizePacket packet) {
        // Serialize to a simple key=value string for the plugin channel
        StringBuilder sb = new StringBuilder(128);
        sb.append("v=2&plugin=BedWarsOptimizer&type=ping_eq");
        sb.append("&server_ts=").append(packet.serverTimestamp);
        sb.append("&target=").append(packet.targetLatencyMs);
        sb.append("&comp=").append(packet.compensationMs);
        sb.append("&jitter=").append(String.format(java.util.Locale.ROOT, "%.3f", packet.jitterFactor));
        sb.append("&hide=").append(packet.hidePing ? 1 : 0);
        sb.append("&avg=").append(packet.serverAvgPingMs);

        try {
            player.sendPluginMessage(plugin, "bedwars:equalize",
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Channel not registered on client — log at fine level
            plugin.getLogger().fine("Could not send ping equalize to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Called when a client mod sends a ping report back (real measured ping).
     */
    public void handlePingReport(UUID playerId, int realPingMs, int clientTickRate) {
        PingEqualizer eq = plugin.getPingEqualizer();
        if (eq.isActive()) {
            eq.recordClientPing(playerId, realPingMs);
            plugin.getLogger().fine("Ping report: " + playerId + " = " + realPingMs +
                    "ms (tick rate: " + clientTickRate + ")");
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
        return info.getCpuCapability() >= config.getClientCpuThreshold();
    }
}