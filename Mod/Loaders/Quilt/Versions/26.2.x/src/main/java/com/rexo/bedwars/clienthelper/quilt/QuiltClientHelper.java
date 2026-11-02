package com.rexo.bedwars.clienthelper.quilt;

import com.rexo.bedwars.clienthelper.BWOptimizerData;
import com.rexo.bedwars.clienthelper.ClientOffloadManager;

/**
 * Quilt client mod for BedWarsOptimizer.
 * Works across Quilt versions 1.14.x through 26.3.x.
 */
public class QuiltClientHelper {
    private static QuiltClientHelper instance;
    private boolean initialized = false;

    public static QuiltClientHelper getInstance() {
        return instance;
    }

    public QuiltClientHelper() {
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
