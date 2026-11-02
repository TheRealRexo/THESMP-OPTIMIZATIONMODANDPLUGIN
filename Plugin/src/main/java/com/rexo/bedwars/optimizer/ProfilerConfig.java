package com.rexo.bedwars.optimizer;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the BedWarsOptimizer plugin.
 * Replaces the old BWOptimizer config — no more villager deletion or block replacement.
 */
public class ProfilerConfig {
    private final FileConfiguration config;

    // Profiling
    private final int profileIntervalTicks;
    private final String profileMode; // "entity", "tile", "both"
    private final int hotThresholdTicks; // entity/tile active this many ticks = "hot"

    // Reporting
    private final int reportIntervalSeconds;
    private final double clientCpuThreshold; // 0.0-1.0, client CPU usage to consider "strong"
    private final boolean offloadEnabled;
    private final int maxOffloadPerTick;

    // Entity profiling
    private final int entitySampleSize; // entities to profile per tick
    private final boolean profileVillagers;
    private final boolean profileMonsters;
    private final boolean profileAnimals;
    private final boolean profileItems;

    // Tile profiling
    private final int tileSampleSize; // chunks to profile per tick
    private final boolean profileRedstone;
    private final boolean profileHoppers;
    private final boolean profileMobSpawners;
    private final boolean profileSigns;

    // Auto-throttle (replaces old auto-throttle but WITHOUT entity deletion)
    private final boolean autoThrottle;
    private final double tpsThreshold;
    private final int ramThresholdPercent;

    // TNT void restriction (kept from old plugin — this is safe)
    private final boolean restrictTntNearVoid;
    private final int voidThresholdY;
    private final double tntBlastRadius;

    public ProfilerConfig(FileConfiguration config) {
        this.config = config;

        // Profiling
        this.profileIntervalTicks = config.getInt("profile.interval-ticks", 20);
        this.profileMode = config.getString("profile.mode", "both");
        this.hotThresholdTicks = config.getInt("profile.hot-threshold-ticks", 10);

        // Reporting
        this.reportIntervalSeconds = config.getInt("report.interval-seconds", 30);
        this.clientCpuThreshold = config.getDouble("report.client-cpu-threshold", 0.6);
        this.offloadEnabled = config.getBoolean("report.offload-enabled", true);
        this.maxOffloadPerTick = config.getInt("report.max-offload-per-tick", 50);

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

        // Auto-throttle (safe — only adjusts view/sim distance, never deletes entities)
        this.autoThrottle = config.getBoolean("auto-throttle.enabled", true);
        this.tpsThreshold = config.getDouble("auto-throttle.tps-threshold", 18.0);
        this.ramThresholdPercent = config.getInt("auto-throttle.ram-threshold-percent", 80);

        // TNT void restriction (kept from old plugin)
        this.restrictTntNearVoid = config.getBoolean("tnt-void.restrict", true);
        this.voidThresholdY = config.getInt("tnt-void.void-threshold-y", 16);
        this.tntBlastRadius = config.getDouble("tnt-void.blast-radius", 5.0);
    }

    // Getters
    public int getProfileIntervalTicks() { return profileIntervalTicks; }
    public String getProfileMode() { return profileMode; }
    public int getHotThresholdTicks() { return hotThresholdTicks; }
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }
    public double getClientCpuThreshold() { return clientCpuThreshold; }
    public boolean isOffloadEnabled() { return offloadEnabled; }
    public int getMaxOffloadPerTick() { return maxOffloadPerTick; }
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
    public boolean isAutoThrottle() { return autoThrottle; }
    public double getTpsThreshold() { return tpsThreshold; }
    public int getRamThresholdPercent() { return ramThresholdPercent; }
    public boolean isRestrictTntNearVoid() { return restrictTntNearVoid; }
    public int getVoidThresholdY() { return voidThresholdY; }
    public double getTntBlastRadius() { return tntBlastRadius; }
}