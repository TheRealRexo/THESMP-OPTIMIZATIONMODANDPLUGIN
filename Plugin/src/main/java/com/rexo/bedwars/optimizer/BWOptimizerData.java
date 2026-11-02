package com.rexo.bedwars.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data structures for offload communication between server plugin and client mods.
 */
public class BWOptimizerData implements Serializable {

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
}