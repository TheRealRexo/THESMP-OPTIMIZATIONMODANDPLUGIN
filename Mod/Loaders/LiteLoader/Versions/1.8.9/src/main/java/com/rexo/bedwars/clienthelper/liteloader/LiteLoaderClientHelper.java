package com.rexo.bedwars.clienthelper.liteloader;

import com.rexo.bedwars.clienthelper.BWOptimizerData;
import com.rexo.bedwars.clienthelper.ClientOffloadManager;

/**
 * LiteLoader client mod for BedWarsOptimizer.
 * Works on LiteLoader 1.8.9+.
 */
public class LiteLoaderClientHelper {
    private static LiteLoaderClientHelper instance;
    private boolean initialized = false;

    public static LiteLoaderClientHelper getInstance() {
        return instance;
    }

    public LiteLoaderClientHelper() {
        instance = this;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
    }

    public void handleOffloadTask(BWOptimizerData.OffloadPacket packet) {
        ClientOffloadManager manager = ClientOffloadManager.getInstance();
        if (!manager.isEnabled()) return;

        for (BWOptimizerData.HotEntity entity : packet.hotEntities) {
            processHotEntity(entity);
        }
        for (BWOptimizerData.HotTile tile : packet.hotTiles) {
            processHotTile(tile);
        }
    }

    private void processHotEntity(BWOptimizerData.HotEntity entity) {
        switch (entity.type) {
            case "VILLAGER":
            case "ZOMBIE":
            case "SKELETON":
            case "CREEPER":
            case "SHULKER":
            case "RAVAGER":
                break;
        }
    }

    private void processHotTile(BWOptimizerData.HotTile tile) {
        if (tile.type.contains("HOPPER")) return;
        if (tile.type.contains("REDSTONE")) return;
        if (tile.type.contains("SPAWNER")) return;
    }

    public void registerWithServer() {
        double cpuCapability = estimateCpuCapability();
    }

    private double estimateCpuCapability() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 16) return 1.0;
        if (cores >= 8) return 0.9;
        if (cores >= 6) return 0.7;
        if (cores >= 4) return 0.5;
        return 0.3;
    }

    public boolean isInitialized() { return initialized; }
}