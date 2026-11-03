package com.rexo.bedwars.clienthelper.fabric;

import com.rexo.bedwars.clienthelper.BWOptimizerData;
import com.rexo.bedwars.clienthelper.ClientOffloadManager;
import com.rexo.bedwars.clienthelper.PingEqualizerManager;

/**
 * FabricClientHelper for BedWarsOptimizer.
 * Features:
 *   - CPU offload task processing
 *   - Ping equalization: hides real ping, applies server compensation
 */
public class FabricClientHelper {
    private static FabricClientHelper instance;
    private boolean initialized = false;
    private final PingEqualizerManager pingManager = PingEqualizerManager.getInstance();

    public static FabricClientHelper getInstance() {
        return instance;
    }

    public FabricClientHelper() {
        instance = this;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        pingManager.init();
        System.out.println("[BWOpt] FabricClientHelper initialized — ping eq: " + pingManager.isActive());
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

    public void handlePingEqualize(BWOptimizerData.PingEqualizePacket packet) {
        pingManager.applyPingEqualization(packet);
    }

    public int getReportedPing() {
        return pingManager.getReportedPing();
    }

    public long applyInputCompensation() {
        return pingManager.applyInputCompensation();
    }

    public void sendPingReport(int realPingMs, int clientTickRate) {
        BWOptimizerData.PingReportPacket report = new BWOptimizerData.PingReportPacket(
                "java_client", realPingMs, clientTickRate);
        System.out.println("[BWOpt] Ping report: " + report.realPingMs + "ms");
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
        System.out.println("[BWOpt] Registering with server (cpu: " + cpuCapability + ")");
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
    public PingEqualizerManager getPingManager() { return pingManager; }
}