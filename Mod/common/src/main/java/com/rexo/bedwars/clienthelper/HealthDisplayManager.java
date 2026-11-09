package com.rexo.bedwars.clienthelper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-side health display manager — renders health as a number + hearts
 * over each player (Hypixel BedWars style).
 *
 * Health calculation is done by the client of the player whose health
 * is being displayed — NOT the observing player's client.
 * This offloads the bossbar/render work from the server tick loop.
 */
public class HealthDisplayManager {

    private static final HealthDisplayManager instance = new HealthDisplayManager();

    private final ConcurrentHashMap<String, PlayerHealth> playerHealthMap = new ConcurrentHashMap<>();
    private final AtomicReference<BWOptimizerData.HealthDisplayPacket> lastPacket = new AtomicReference<>(null);
    private volatile boolean active = false;
    private volatile boolean showHearts = true;
    private volatile boolean showNumber = true;
    private volatile int renderIntervalTicks = 2;

    public static HealthDisplayManager getInstance() {
        return instance;
    }

    /**
     * Initialize the health display manager.
     */
    public void init() {
        if (active) return;
        active = true;
        System.out.println("[BWOpt] Health display manager initialized");
    }

    /**
     * Apply a HealthDisplayPacket received from the server.
     * Stores the health data so the render loop can draw it.
     */
    public void applyHealthDisplay(BWOptimizerData.HealthDisplayPacket packet) {
        lastPacket.set(new BWOptimizerData.HealthDisplayPacket(
            packet.serverTimestamp,
            packet.playerUuid,
            packet.health,
            packet.maxHealth,
            packet.absorption,
            packet.showHearts,
            packet.showNumber,
            packet.numberFormat
        ));

        this.showHearts = packet.showHearts;
        this.showNumber = packet.showNumber;

        playerHealthMap.put(packet.playerUuid, new PlayerHealth(
            packet.playerUuid,
            packet.health,
            packet.maxHealth,
            packet.absorption
        ));
    }

    /**
     * Get the health data for a specific player.
     */
    public PlayerHealth getPlayerHealth(String uuid) {
        return playerHealthMap.get(uuid);
    }

    /**
     * Render the health display for a player.
     * Called by the client render loop — draws number + hearts above the player.
     *
     * @param uuid the player whose health is being rendered
     * @return formatted health display string, or null if no data
     */
    public String renderHealthDisplay(String uuid) {
        PlayerHealth ph = playerHealthMap.get(uuid);
        if (ph == null) return null;

        StringBuilder sb = new StringBuilder(64);

        // Health number (e.g. "10" or "10.0")
        if (showNumber) {
            if (ph.health <= 10.0) {
                sb.append((int) ph.health);
            } else {
                sb.append(String.format(java.util.Locale.ROOT, "%.1f", ph.health));
            }
            sb.append(" ");
        }

        // Hearts (e.g. "♥♥♥♥♥")
        if (showHearts) {
            int hearts = (int) Math.ceil(ph.health / 2.0);
            int maxHearts = (int) Math.ceil(ph.maxHealth / 2.0);
            for (int i = 0; i < maxHearts; i++) {
                if (i < hearts) {
                    sb.append("♥");
                } else {
                    sb.append("♡");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Get the heart count for a player.
     */
    public int getHeartCount(String uuid) {
        PlayerHealth ph = playerHealthMap.get(uuid);
        if (ph == null) return 0;
        return (int) Math.ceil(ph.health / 2.0);
    }

    /**
     * Get the max heart count for a player.
     */
    public int getMaxHeartCount(String uuid) {
        PlayerHealth ph = playerHealthMap.get(uuid);
        if (ph == null) return 10;
        return (int) Math.ceil(ph.maxHealth / 2.0);
    }

    public boolean isActive() { return active; }
    public boolean isShowHearts() { return showHearts; }
    public boolean isShowNumber() { return showNumber; }
    public int getRenderIntervalTicks() { return renderIntervalTicks; }
    public int getCachedPlayerCount() { return playerHealthMap.size(); }

    // --- Inner class ---

    public static class PlayerHealth {
        public final String uuid;
        public final double health;
        public final double maxHealth;
        public final int absorption;

        public PlayerHealth(String uuid, double health, double maxHealth, int absorption) {
            this.uuid = uuid;
            this.health = health;
            this.maxHealth = maxHealth;
            this.absorption = absorption;
        }

        public double getHealth() { return health; }
        public double getMaxHealth() { return maxHealth; }
        public int getAbsorption() { return absorption; }
        public int getHeartCount() { return (int) Math.ceil(health / 2.0); }
    }
}