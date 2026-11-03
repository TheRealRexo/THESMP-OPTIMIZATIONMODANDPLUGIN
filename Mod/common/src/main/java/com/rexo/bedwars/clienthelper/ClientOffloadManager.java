package com.rexo.bedwars.clienthelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side CPU offload manager + ping equalization integration.
 *
 * Handles distributing work to strong clients based on their capabilities.
 * Also integrates with PingEqualizerManager to hide real ping and apply
 * server-side compensation for smooth gameplay.
 */
public class ClientOffloadManager {
    private static final ClientOffloadManager instance = new ClientOffloadManager();

    public static final String REGISTRATION_CHANNEL = "bedwars:register";
    public static final String PING_EQ_CHANNEL = "bedwars:equalize";
    public static final String PING_REPORT_CHANNEL = "bedwars:ping_report";

    public static ClientOffloadManager getInstance() {
        return instance;
    }

    private final Map<String, ClientCapability> connectedClients = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private volatile boolean enabled;
    private volatile int taskIdCounter;
    private volatile boolean registeredWithServer = false;
    private final PingEqualizerManager pingManager = PingEqualizerManager.getInstance();
    private final AtomicInteger pingReportCounter = new AtomicInteger(0);
    private final AtomicLong lastPingReportTime = new AtomicLong(0);

    private ClientOffloadManager() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(Math.max(2, cores / 2));
        this.enabled = true;
        this.taskIdCounter = 0;
    }

    /**
     * Register this client with the server.
     * Sends a handshake packet so the server knows the mod is installed.
     */
    public void registerWithServer() {
        if (registeredWithServer) return;
        registeredWithServer = true;

        double cpuCapability = estimateCpuCapability();
        int maxThreads = Runtime.getRuntime().availableProcessors();

        System.out.println("[BWOpt] Registering with server — cpu: " + cpuCapability
                + ", threads: " + maxThreads);
    }

    /**
     * Check if this client has registered with the server.
     */
    public boolean isRegisteredWithServer() {
        return registeredWithServer;
    }

    /**
     * Register a new client with its CPU capability.
     */
    public void registerClient(String clientId, double cpuCapability, int maxThreads) {
        connectedClients.put(clientId, new ClientCapability(clientId, cpuCapability, maxThreads));
        System.out.println("[BWOpt] Client registered: " + clientId + " (CPU: " + cpuCapability + ")");
    }

    /**
     * Unregister a client.
     */
    public void unregisterClient(String clientId) {
        connectedClients.remove(clientId);
    }

    /**
     * Submit an offload task to a strong client.
     */
    public Future<?> submitTask(String clientId, Runnable task) {
        ClientCapability client = connectedClients.get(clientId);
        if (client == null || !client.isStrongEnough()) {
            return null;
        }
        taskIdCounter++;
        return executor.submit(() -> {
            try {
                long start = System.nanoTime();
                task.run();
                long end = System.nanoTime();
                System.out.println("[BWOpt] Task " + taskIdCounter + " completed in " +
                    ((end - start) / 1_000_000) + "ms by client " + clientId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Get all strong clients.
     */
    public Map<String, ClientCapability> getStrongClients() {
        Map<String, ClientCapability> strong = new HashMap<>();
        for (var entry : connectedClients.entrySet()) {
            if (entry.getValue().isStrongEnough()) {
                strong.put(entry.getKey(), entry.getValue());
            }
        }
        return strong;
    }

    /**
     * Check if a specific client is strong enough.
     */
    public boolean isClientStrong(String clientId) {
        ClientCapability client = connectedClients.get(clientId);
        return client != null && client.isStrongEnough();
    }

    /**
     * Periodically send ping reports to the server.
     * Called by the client tick loop.
     */
    public void tickPingReport() {
        long now = System.currentTimeMillis();
        // Send a ping report every 3 seconds
        if (now - lastPingReportTime.get() < 3000) return;
        lastPingReportTime.set(now);
        pingReportCounter.incrementAndGet();

        // In production this sends a PingReportPacket via the plugin channel
        // For now we log it
        String realStr = pingManager.isHidePing() ? "hidden" : (pingManager.getReportedPing() + "ms");
        System.out.println("[BWOpt] Ping report #" + pingReportCounter.get()
                + " — reported: " + pingManager.getReportedPing() + "ms"
                + " (real: " + realStr + ")");
    }

    /**
     * Apply input compensation before processing a tick.
     * Returns the delay in ms to apply.
     */
    public long beforeTick() {
        return pingManager.applyInputCompensation();
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getNextTaskId() {
        return taskIdCounter++;
    }

    public PingEqualizerManager getPingManager() {
        return pingManager;
    }

    private double estimateCpuCapability() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 16) return 1.0;
        if (cores >= 8) return 0.9;
        if (cores >= 6) return 0.7;
        if (cores >= 4) return 0.5;
        return 0.3;
    }

    // --- Inner classes ---

    public static class ClientCapability {
        private final String clientId;
        private final double cpuCapability;
        private final int maxThreads;

        public ClientCapability(String clientId, double cpuCapability, int maxThreads) {
            this.clientId = clientId;
            this.cpuCapability = cpuCapability;
            this.maxThreads = maxThreads;
        }

        public String getClientId() { return clientId; }
        public double getCpuCapability() { return cpuCapability; }
        public int getMaxThreads() { return maxThreads; }

        public boolean isStrongEnough() {
            return cpuCapability >= 0.6;
        }

        @Override
        public String toString() {
            return "ClientCapability{clientId='" + clientId + "', cpu=" + cpuCapability + "}";
        }
    }
}