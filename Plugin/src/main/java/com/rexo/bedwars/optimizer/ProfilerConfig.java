package com.rexo.bedwars.optimizer;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the BedWarsOptimizer plugin.
 *
 * Key features:
 *   - CPU profiling for entities and tile entities
 *   - Dynamic load offload to capable Java clients (Bedrock players are exempt)
 *   - Health display: server sends health packets, client renders number + hearts
 *   - Auto-kick Java players without updated mod
 *   - TPS-based auto-throttle (safe — only adjusts view/sim distance)
 */
public class ProfilerConfig {
    private final FileConfiguration config;

    // ── Profiling ────────────────────────────────────────────────────────────────
    private final int profileIntervalTicks;
    private final String profileMode;
    private final int hotThresholdTicks;

    // Entity profiling
    private final int entitySampleSize;
    private final boolean profileVillagers;
    private final boolean profileMonsters;
    private final boolean profileAnimals;
    private final boolean profileItems;

    // Tile profiling
    private final int tileSampleSize;
    private final boolean profileRedstone;
    private final boolean profileHoppers;
    private final boolean profileMobSpawners;
    private final boolean profileSigns;

    // ── Dynamic Load Offload ─────────────────────────────────────────────────────
    private final int reportIntervalSeconds;
    private final double clientCpuThreshold;
    private final boolean offloadEnabled;
    private final int maxOffloadPerTick;
    private final int pollIntervalSeconds;

    // ── Health Display ───────────────────────────────────────────────────────────
    private final boolean healthDisplayEnabled;
    private final int healthDisplayIntervalTicks;
    private final boolean healthShowHearts;
    private final boolean healthShowNumber;

    // ── Floodgate Offload ───────────────────────────────────────────────────────
    private final boolean floodgateOffloadEnabled;
    private final double floodgateLoadThreshold;

    // ── Auto-throttle ────────────────────────────────────────────────────────────
    private final boolean autoThrottle;
    private final double tpsThreshold;
    private final int ramThresholdPercent;

    // ── TNT void restriction ─────────────────────────────────────────────────────
    private final boolean restrictTntNearVoid;
    private final int voidThresholdY;
    private final double tntBlastRadius;

    public ProfilerConfig(FileConfiguration config) {
        this.config = config;

        // Profiling
        this.profileIntervalTicks = config.getInt("profile.interval-ticks", 20);
        this.profileMode = config.getString("profile.mode", "both");
        this.hotThresholdTicks = config.getInt("profile.hot-threshold-ticks", 10);

        // Entity profiling
        this.entitySampleSize = config.getInt("profile.entity-sample-size", 100);
        this.profileVillagers = config.getBoolean("profile.entity.villagers", true);
        this.profileMonsters = config.getBoolean("profile.entity.monsters", true);
        this.profileAnimals = config.getBoolean("profile.entity.animals", true);
        this.profileItems = config.getBoolean("profile.entity.items", true);

        // Tile profiling
        this.tileSampleSize = config.getInt("profile.tile-sample-size", 20);
        this.profileRedstone = config.getBoolean("profile.tile.redstone", true);
        this.profileHoppers = config.getBoolean("profile.tile.hoppers", true);
        this.profileMobSpawners = config.getBoolean("profile.tile.mob-spawners", true);
        this.profileSigns = config.getBoolean("profile.tile.signs", true);

        // Dynamic load offload
        this.reportIntervalSeconds = config.getInt("report.interval-seconds", 30);
        this.clientCpuThreshold = config.getDouble("report.client-cpu-threshold", 0.6);
        this.offloadEnabled = config.getBoolean("report.offload-enabled", true);
        this.maxOffloadPerTick = config.getInt("report.max-offload-per-tick", 50);
        this.pollIntervalSeconds = config.getInt("report.poll-interval-seconds", 5);

        // Health display
        this.healthDisplayEnabled = config.getBoolean("health-display.enabled", true);
        this.healthDisplayIntervalTicks = config.getInt("health-display.interval-ticks", 2);
        this.healthShowHearts = config.getBoolean("health-display.show-hearts", true);
        this.healthShowNumber = config.getBoolean("health-display.show-number", true);

        // Floodgate offload
        this.floodgateOffloadEnabled = config.getBoolean("floodgate-offload.enabled", true);
        this.floodgateLoadThreshold = config.getDouble("floodgate-offload.load-threshold", 0.7);

        // Auto-throttle
        this.autoThrottle = config.getBoolean("auto-throttle.enabled", true);
        this.tpsThreshold = config.getDouble("auto-throttle.tps-threshold", 18.0);
        this.ramThresholdPercent = config.getInt("auto-throttle.ram-threshold-percent", 80);

        // TNT void restriction
        this.restrictTntNearVoid = config.getBoolean("tnt-void.restrict", true);
        this.voidThresholdY = config.getInt("tnt-void.void-threshold-y", 16);
        this.tntBlastRadius = config.getDouble("tnt-void.blast-radius", 5.0);
    }

    // ── Profiling getters ────────────────────────────────────────────────────────
    public int getProfileIntervalTicks() { return profileIntervalTicks; }
    public String getProfileMode() { return profileMode; }
    public int getHotThresholdTicks() { return hotThresholdTicks; }
    public int getEntitySampleSize() { return entitySampleSize; }
    public boolean isProfileVillagers() { return profileVillagers; }
    public boolean isProfileMonsters() { return profileMonsters; }
    public boolean isProfileAnimals() { return profileAnimals; }
    public boolean isProfileItems() { return profileItems; }
    public int getTileSampleSize() { return tileSampleSize; }
    public boolean isProfileRedstone() { return profileRedstone; }
    public boolean isProfileHoppers() { return profileHoppers; }
    public boolean isProfileMobSpawners() { return profileMobSpawners; }
    public boolean isProfileSigns() { return profileSigns; }

    // ── Dynamic offload getters ──────────────────────────────────────────────────
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }
    public double getClientCpuThreshold() { return clientCpuThreshold; }
    public boolean isOffloadEnabled() { return offloadEnabled; }
    public int getMaxOffloadPerTick() { return maxOffloadPerTick; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }

    // ── Health display getters ───────────────────────────────────────────────────
    public boolean isHealthDisplayEnabled() { return healthDisplayEnabled; }
    public int getHealthDisplayIntervalTicks() { return healthDisplayIntervalTicks; }
    public boolean isHealthShowHearts() { return healthShowHearts; }
    public boolean isHealthShowNumber() { return healthShowNumber; }

    // ── Floodgate offload getters ────────────────────────────────────────────────
    public boolean isFloodgateOffloadEnabled() { return floodgateOffloadEnabled; }
    public double getFloodgateLoadThreshold() { return floodgateLoadThreshold; }

    // ── Auto-throttle getters ────────────────────────────────────────────────────
    public boolean isAutoThrottle() { return autoThrottle; }
    public double getTpsThreshold() { return tpsThreshold; }
    public int getRamThresholdPercent() { return ramThresholdPercent; }

    // ── TNT void getters ─────────────────────────────────────────────────────────
    public boolean isRestrictTntNearVoid() { return restrictTntNearVoid; }
    public int getVoidThresholdY() { return voidThresholdY; }
    public double getTntBlastRadius() { return tntBlastRadius; }
}