package com.rexo.bedwars.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common data structures for the BedWarsOptimizer.
 * These are serialized and sent from the server plugin to client mods.
 */
public class BWOptimizerData {

    /**
     * Hot entity information to offload processing to client.
     */
    public static class HotEntity {
        public final long entityId;
        public final String type;
        public final double x, y, z;
        public final long profiledCpuTime;
        public final String offloadTask;

        public HotEntity(long entityId, String type, double x, double y, double z,
                        long profiledCpuTime, String offloadTask) {
            this.entityId = entityId;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.profiledCpuTime = profiledCpuTime;
            this.offloadTask = offloadTask;
        }
    }

    /**
     * Hot tile entity information.
     */
    public static class HotTile {
        public final String world;
        public final int x, y, z;
        public final String type;
        public final long profiledCpuTime;

        public HotTile(String world, int x, int y, int z, String type, long profiledCpuTime) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.profiledCpuTime = profiledCpuTime;
        }
    }

    /**
     * Complete offload task packet.
     */
    public static class OffloadPacket implements Serializable {
        public final int taskId;
        public final long timestamp;
        public final List<HotEntity> hotEntities;
        public final List<HotTile> hotTiles;
        public final Map<String, Object> metadata;

        public OffloadPacket(int taskId, List<HotEntity> hotEntities, List<HotTile> hotTiles) {
            this.taskId = taskId;
            this.timestamp = System.currentTimeMillis();
            this.hotEntities = hotEntities != null ? hotEntities : new ArrayList<>();
            this.hotTiles = hotTiles != null ? hotTiles : new ArrayList<>();
            this.metadata = new HashMap<>();
            metadata.put("version", "2.0");
            metadata.put("plugin", "BedWarsOptimizer");
        }
    }

    /**
     * Client registration packet.
     */
    public static class ClientRegisterPacket implements Serializable {
        public final String clientId;
        public final double cpuCapability;
        public final int maxThreads;
        public final long timestamp;

        public ClientRegisterPacket(String clientId, double cpuCapability, int maxThreads) {
            this.clientId = clientId;
            this.cpuCapability = cpuCapability;
            this.maxThreads = maxThreads;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Result packet sent back to server.
     */
    public static class TaskResult implements Serializable {
        public final int taskId;
        public final boolean success;
        public final long resultCpuTime;
        public final Map<String, Object> resultData;

        public TaskResult(int taskId, boolean success, long resultCpuTime, Map<String, Object> resultData) {
            this.taskId = taskId;
            this.success = success;
            this.resultCpuTime = resultCpuTime;
            this.resultData = resultData;
        }
    }

    /**
     * Health display packet — sent from server to each client.
     *
     * The server tracks real health but the client mod renders the health
     * display (number + hearts) locally. This offloads the health bossbar
     * rendering from the server tick loop to the client render thread.
     */
    public static class HealthDisplayPacket implements Serializable {
        public final long serverTimestamp;
        public final String playerUuid;
        public final double health;          // 0.0-20.0
        public final double maxHealth;       // 20.0
        public final int absorption;          // absorption hearts
        public final boolean showHearts;      // render heart icons
        public final boolean showNumber;      // render health number
        public final String numberFormat;     // "XX" or "XX.X"
        public final int heartCount;          // number of hearts to display
        public final Map<String, Object> metadata;

        public HealthDisplayPacket(long serverTimestamp, String playerUuid,
                                   double health, double maxHealth, int absorption,
                                   boolean showHearts, boolean showNumber, String numberFormat) {
            this.serverTimestamp = serverTimestamp;
            this.playerUuid = playerUuid;
            this.health = health;
            this.maxHealth = maxHealth;
            this.absorption = absorption;
            this.showHearts = showHearts;
            this.showNumber = showNumber;
            this.numberFormat = numberFormat;
            this.heartCount = (int) Math.ceil(health / 2.0);
            this.metadata = new HashMap<>();
            metadata.put("version", "2.0");
            metadata.put("plugin", "BedWarsOptimizer");
        }
    }

    // --- Helper methods ---

    public static HotEntity parseHotEntity(String line) {
        String[] parts = line.split(":");
        if (parts.length < 7) return null;
        try {
            long entityId = Long.parseLong(parts[0]);
            String type = parts[1];
            double x = Double.parseDouble(parts[2]);
            double y = Double.parseDouble(parts[3]);
            double z = Double.parseDouble(parts[4]);
            long cpuTime = Long.parseLong(parts[5]);
            String task = parts[6];
            return new HotEntity(entityId, type, x, y, z, cpuTime, task);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}