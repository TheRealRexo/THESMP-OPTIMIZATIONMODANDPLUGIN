package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HealthDisplayManager — reads player health server-side and sends
 * HealthDisplayPackets to each client so the client mod renders
 * health as a number + hearts over the player (Hypixel BedWars style).
 *
 * Health calculation and rendering is done by the client of the player
 * whose health is being displayed — NOT the observing player's client.
 * This offloads the bossbar/render work from the server tick loop.
 */
public class HealthDisplayManager {

    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, Long> lastHealthSend = new ConcurrentHashMap<>();
    private final Map<UUID, Double> cachedHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cachedAbsorption = new ConcurrentHashMap<>();
    private volatile boolean active = false;
    private volatile int sendIntervalTicks = 2; // send every 2 ticks (100ms)

    public HealthDisplayManager(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable() {
        if (active) return;
        active = true;
        plugin.getLogger().info("Health display manager enabled — sending every " + sendIntervalTicks + " ticks");
    }

    public void disable() {
        active = false;
        lastHealthSend.clear();
        cachedHealth.clear();
        cachedAbsorption.clear();
    }

    public boolean isActive() { return active; }

    /**
     * Called periodically to send health display packets to all online Java clients.
     * For each player, we send a HealthDisplayPacket so their OWN client renders
     * the health number + hearts above them.
     */
    public void pollAndSendHealth() {
        if (!active) return;

        long now = System.currentTimeMillis();
        int sent = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Skip Bedrock players — they get health from the server bossbar
            if (plugin.getModEnforcer().isBedrock(uuid)) continue;

            // Skip players without the mod
            if (!plugin.getModEnforcer().getRegisteredClients().containsKey(uuid)) continue;

            double health = player.getHealth();
            double maxHealth = player.getMaxHealth();
            int absorption = (int) Math.ceil(player.getAbsorptionAmount());

            // Rate-limit: only send if health changed or interval elapsed
            Long lastSent = lastHealthSend.get(uuid);
            Double prevHealth = cachedHealth.get(uuid);
            Integer prevAbsorption = cachedAbsorption.get(uuid);

            boolean healthChanged = prevHealth == null || Math.abs(prevHealth - health) > 0.01;
            boolean absorptionChanged = prevAbsorption == null || prevAbsorption != absorption;
            boolean intervalElapsed = lastSent == null || (now - lastSent) >= (sendIntervalTicks * 50L);

            if (!healthChanged && !absorptionChanged && !intervalElapsed) continue;

            // Build and send packet
            BWOptimizerData.HealthDisplayPacket packet = new BWOptimizerData.HealthDisplayPacket(
                now,
                uuid.toString(),
                health,
                maxHealth,
                absorption,
                true,  // showHearts
                true,  // showNumber
                health <= 10 ? "XX" : "XX.X" // compact format for low health
            );

            sendHealthPacket(player, packet);

            // Cache
            lastHealthSend.put(uuid, now);
            cachedHealth.put(uuid, health);
            cachedAbsorption.put(uuid, absorption);
            sent++;
        }

        if (sent > 0 && plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
            plugin.getLogger().fine("[BWOpt] Health display sent to " + sent + " clients");
        }
    }

    /**
     * Send a HealthDisplayPacket to a specific player's client.
     */
    private void sendHealthPacket(Player player, BWOptimizerData.HealthDisplayPacket packet) {
        // Serialize to key=value string for the plugin channel
        StringBuilder sb = new StringBuilder(256);
        sb.append("v=2&plugin=BedWarsOptimizer&type=health_display");
        sb.append("&uuid=").append(packet.playerUuid);
        sb.append("&health=").append(String.format(java.util.Locale.ROOT, "%.1f", packet.health));
        sb.append("&max=").append(String.format(java.util.Locale.ROOT, "%.1f", packet.maxHealth));
        sb.append("&absorption=").append(packet.absorption);
        sb.append("&hearts=").append(packet.showHearts ? 1 : 0);
        sb.append("&number=").append(packet.showNumber ? 1 : 0);
        sb.append("&format=").append(packet.numberFormat);
        sb.append("&heart_count=").append(packet.heartCount);

        try {
            player.sendPluginMessage(plugin, "bedwars:health_display",
                sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().fine("Could not send health display to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Get the current cached health for a player.
     */
    public double getCachedHealth(UUID uuid) {
        Double h = cachedHealth.get(uuid);
        return h != null ? h : 0.0;
    }

    /**
     * Get the current cached absorption for a player.
     */
    public int getCachedAbsorption(UUID uuid) {
        Integer a = cachedAbsorption.get(uuid);
        return a != null ? a : 0;
    }

    public int getSendIntervalTicks() { return sendIntervalTicks; }
    public Map<UUID, Double> getCachedHealthMap() { return cachedHealth; }
    public int getCachedPlayerCount() { return cachedHealth.size(); }
}