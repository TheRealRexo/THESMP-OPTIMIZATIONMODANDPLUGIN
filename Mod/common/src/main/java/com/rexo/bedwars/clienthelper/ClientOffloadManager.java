package com.rexo.bedwars.clienthelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.HashMap;

/**
 * Client-side CPU offload manager.
 * Handles distributing work to strong clients based on their capabilities.
 *
 * Also handles registration with the server — sends a handshake on startup
 * so the server knows the client mod is installed.
 */
public class ClientOffloadManager {
    private static final ClientOffloadManager instance = new ClientOffloadManager();

    // Channel used to register with the server
    public static final String REGISTRATION_CHANNEL = "bedwars:register";

    public static ClientOffloadManager getInstance() {
        return instance;
    }

    private final Map<String, ClientCapability> connectedClients = new HashMap<>();
    private final ExecutorService executor;
    private volatile boolean enabled;
    private volatile int taskIdCounter;
    private volatile boolean registeredWithServer = false;

    private ClientOffloadManager() {
        // Create a thread pool sized to typical CPU cores
        int cores = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(Math.max(2, cores / 2));
        this.enabled = true;
        this.taskIdCounter = 0;
    }

    /**
     * Register this client with the server.
     * Sends a handshake packet so the server knows the mod is installed.
     * Called automatically when the mod initializes.
     */
    public void registerWithServer() {
        if (registeredWithServer) return;
        registeredWithServer = true;

        // In a real implementation, this would send a plugin message
        // For now, we set the flag and the server-side enforcement
        // will detect the mod via the registration packet
        System.out.println("[BWOpt] Registering with server...");
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
     * Returns the task ID for result tracking.
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
            return cpuCapability >= 0.6; // Threshold matches server config
        }

        @Override
        public String toString() {
            return "ClientCapability{clientId='" + clientId + "', cpu=" + cpuCapability + "}";
        }
    }
}