package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BedWarsOptimizer — CPU profiler + client-side load offload.
 *
 * Replaces the old BWOptimizer which deleted all villagers every tick
 * (TeamCleaner.tick → Villager.remove()) and turned iron/gold/emerald
 * blocks into air in y=40-80. This new plugin does NOT touch entities
 * or blocks. It only profiles CPU hotspots and reports them to clients
 * that have spare CPU, which then run the heavy work locally.
 */
public class BedWarsOptimizer extends JavaPlugin {

    private static BedWarsOptimizer instance;
    private ProfilerConfig config;
    private Profiler profiler;
    private LoadReportManager reportManager;
    private ClientComm clientComm;
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

        profiler.enable();
        reportManager.enable();
        clientComm.enable();

        // Register command for manual profiling
        getCommand("bwopt").setExecutor(new OptimizerCommand(this));

        getLogger().info("BedWarsOptimizer v1.0.0 enabled — profiling mode: " + config.getProfileMode());
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