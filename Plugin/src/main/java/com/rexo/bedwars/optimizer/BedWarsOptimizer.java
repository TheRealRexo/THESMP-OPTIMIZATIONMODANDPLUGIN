package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicLong;

/**
 * BedWarsOptimizer — CPU profiler + dynamic client-side load offload.
 *
 * Java clients with the mod: receive offloaded work based on ping/CPU capability
 * Bedrock clients: handled server-side (no mod required, no kicking)
 *
 * Work is dynamically split using: client ping, server TPS, RAM usage
 */
public class BedWarsOptimizer extends JavaPlugin {

    private static BedWarsOptimizer instance;
    private ProfilerConfig config;
    private Profiler profiler;
    private LoadReportManager reportManager;
    private ClientComm clientComm;
    private ModEnforcer modEnforcer;
    private final AtomicLong totalEntities = new AtomicLong(0);
    private final AtomicLong hotEntities = new AtomicLong(0);
    private final AtomicLong hotTiles = new AtomicLong(0);

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = new ProfilerConfig(getConfig());

        profiler = new Profiler(this, config);
        reportManager = new LoadReportManager(this, config);
        clientComm = new ClientComm(this, config, reportManager);
        modEnforcer = new ModEnforcer(this, config);

        profiler.enable();
        reportManager.enable();
        clientComm.enable();

        // Register event listeners (mod registration, not enforcement)
        getServer().getPluginManager().registerEvents(modEnforcer, this);

        getLogger().info("BedWarsOptimizer v1.0.0 enabled — profiling: " + config.getProfileMode()
                + " | offload: " + config.isOffloadEnabled()
                + " | threshold: " + config.getClientCpuThreshold());

        // Register command
        getCommand("bwopt").setExecutor(new OptimizerCommand(this));
    }

    @Override
    public void onDisable() {
        if (profiler != null) profiler.disable();
        if (reportManager != null) reportManager.disable();
        if (clientComm != null) clientComm.disable();
        instance = null;
        getLogger().info("BedWarsOptimizer disabled.");
    }

    public static BedWarsOptimizer getInstance() { return instance; }
    public ProfilerConfig getProfilerConfig() { return config; }
    public Profiler getProfiler() { return profiler; }
    public LoadReportManager getReportManager() { return reportManager; }
    public ClientComm getClientComm() { return clientComm; }
    public ModEnforcer getModEnforcer() { return modEnforcer; }
    public long getTotalEntities() { return totalEntities.get(); }
    public long getHotEntities() { return hotEntities.get(); }
    public long getHotTiles() { return hotTiles.get(); }

    public void recordEntity(long n) { totalEntities.addAndGet(n); }
    public void recordHotEntity(long n) { hotEntities.addAndGet(n); }
    public void recordHotTile(long n) { hotTiles.addAndGet(n); }

    public void resetCounters() {
        totalEntities.set(0);
        hotEntities.set(0);
        hotTiles.set(0);
    }
}
