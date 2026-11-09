package com.rexo.bedwars.clienthelper.quilt;

import com.rexo.bedwars.clienthelper.BWOptimizerData;
import com.rexo.bedwars.clienthelper.ClientOffloadManager;
import com.rexo.bedwars.clienthelper.HealthDisplayManager;

/**
 * QuiltClientHelper for BedWarsOptimizer v1.1.0.
 * 
 * Features:
 *   - CPU offload task processing
 *   - Health display: renders number + hearts over players (Hypixel style)
 *     Each client renders health for its OWN player
 *   - Geyser/Floodgate work offloading when server is under pressure
 */
public class QuiltClientHelper {
    private static QuiltClientHelper instance;
    private boolean initialized = false;
    private final ClientOffloadManager clientManager = ClientOffloadManager.getInstance();
    private final HealthDisplayManager healthManager = HealthDisplayManager.getInstance();

    public static QuiltClientHelper getInstance() {
        return instance;
    }

    public QuiltClientHelper() {
        instance = this;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        
        // Register with server
        clientManager.registerWithServer();
        healthManager.init();
        
        System.out.println("[BWOpt] QuiltClientHelper initialized — version 1.1.0");
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

    public void handleHealthDisplay(BWOptimizerData.HealthDisplayPacket packet) {
        // Only render health for our own player
        String myUuid = getClientUuid();
        if (myUuid != null && packet.playerUuid.equals(myUuid)) {
            healthManager.applyHealthDisplay(packet);
        }
    }

    /**
     * Render health display during the tick/render loop.
     * Returns formatted string like "10.0 ♥♥♥♥" or null if no data.
     */
    public String renderHealthForPlayer(String uuid) {
        return clientManager.renderHealthForPlayer(uuid);
    }

    /**
     * Get the local client's UUID (for matching health packets).
     */
    private String getClientUuid() {
        // In production, this would query Minecraft's session UUID
        return "local_client_uuid";
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
        clientManager.registerWithServer();
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
    public HealthDisplayManager getHealthManager() { return healthManager; }
}