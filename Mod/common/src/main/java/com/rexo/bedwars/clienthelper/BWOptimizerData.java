package com.rexo.bedwars.clienthelper;

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
     * Ping equalization packet — sent from server to each client.
     *
     * The server measures each client's real ping, then calculates an offset
     * so that all clients experience equalized latency. The client mod applies
     * this offset to hide the real ping and report 0ms.
     */
    public static class PingEqualizePacket implements Serializable {
        public final long serverTimestamp;
        public final int targetLatencyMs;
        public final int compensationMs;
        public final double jitterFactor;
        public final boolean hidePing;
        public final int serverAvgPingMs;
        public final Map<String, Object> metadata;

        public PingEqualizePacket(long serverTimestamp, int targetLatencyMs,
                                  int compensationMs, double jitterFactor, boolean hidePing,
                                  int serverAvgPingMs) {
            this.serverTimestamp = serverTimestamp;
            this.targetLatencyMs = targetLatencyMs;
            this.compensationMs = compensationMs;
            this.jitterFactor = jitterFactor;
            this.hidePing = hidePing;
            this.serverAvgPingMs = serverAvgPingMs;
            this.metadata = new HashMap<>();
            metadata.put("version", "2.0");
            metadata.put("plugin", "BedWarsOptimizer");
        }
    }

    /**
     * Client ping report — sent from client mod back to server.
     * Contains the client's measured real ping for verification.
     */
    public static class PingReportPacket implements Serializable {
        public final String clientId;
        public final int realPingMs;
        public final int clientTickRate;
        public final long timestamp;

        public PingReportPacket(String clientId, int realPingMs, int clientTickRate) {
            this.clientId = clientId;
            this.realPingMs = realPingMs;
            this.clientTickRate = clientTickRate;
            this.timestamp = System.currentTimeMillis();
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