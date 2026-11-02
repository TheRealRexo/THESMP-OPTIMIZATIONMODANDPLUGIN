package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * LoadReportManager — aggregates profiling data and reports to clients.
 * Sends CPU load reports and hotspot information to clients.
 */
public class LoadReportManager {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private BukkitTask reportTask;
    private long reportCount;

    public LoadReportManager(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable() {
        reportTask = Bukkit.getScheduler().runTaskTimer(plugin, this::generateReport,
            0, config.getReportIntervalSeconds() * 20L);
    }

    public void disable() {
        if (reportTask != null) {
            reportTask.cancel();
            reportTask = null;
        }
    }

    public void generateReport() {
        reportCount++;

        // Get current TPS and memory
        double[] tps = Bukkit.getTPS();
        double smoothedTps = tps[0];
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        double usedPercent = ((double)(totalMem - freeMem) / maxMem) * 100.0;

        // Count entities
        long totalEntities = 0;
        long hotEntities = 0;
        long hotTiles = 0;

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                totalEntities++;
                if (e instanceof org.bukkit.entity.Villager) {
                    hotTiles++; // villager AI is heavy
                }
            }
        }

        // Get hot spots from profiler
        List<Profiler.EntityProfile> hotEntityList = plugin.getProfiler().getHotEntities();
        List<Profiler.TileEntityProfile> hotTileList = plugin.getProfiler().getHotTiles();

        // Build report
        StringBuilder report = new StringBuilder();
        report.append("=== BedWarsOptimizer v1.0 Report #").append(reportCount).append(" ===\n");
        report.append(String.format("TPS: %.2f | Entities: %d | Hot: %d\n",
            smoothedTps, totalEntities, hotEntityList.size() + hotTileList.size()));
        report.append(String.format("RAM: %.1f%% | Mode: %s\n",
            usedPercent, config.getProfileMode()));

        if (!hotEntityList.isEmpty()) {
            report.append("\nTop Hot Entities:\n");
            for (Profiler.EntityProfile ep : hotEntityList.subList(0, Math.min(10, hotEntityList.size()))) {
                report.append(String.format("  %s (avg: %.1fμs)\n", ep.getType(), ep.getAverageCpuTime() / 1000.0));
            }
        }

        if (!hotTileList.isEmpty()) {
            report.append("\nTop Hot Tiles:\n");
            for (Profiler.TileEntityProfile tp : hotTileList.subList(0, Math.min(10, hotTileList.size()))) {
                report.append(String.format("  %s (avg: %.1fμs)\n", tp.getType(), tp.getAverageCpuTime() / 1000.0));
            }
        }

        // Broadcast to ops
        if (Bukkit.getOnlinePlayers().size() > 0) {
            String formatted = ChatColor.translateAlternateColorCodes('&', report.toString());
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(formatted);
                }
            }
        }

        // Log to console
        plugin.getLogger().info(report.toString());

        // Auto-throttle if needed
        autoThrottle(smoothedTps, usedPercent);
    }

    private void autoThrottle(double tps, double ramPercent) {
        if (!config.isAutoThrottle()) return;

        // Only throttle, never delete entities
        if (tps < config.getTpsThreshold()) {
            // Reduce view distance gradually
            int currentView = 6; // Paper default
            int newView = Math.max(2, currentView - 1);
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                try {
                    world.setViewDistance(newView);
                } catch (Exception ignored) {}
            }
            plugin.getLogger().warning("Auto-throttle: TPS=" + String.format("%.2f", tps) +
                " < threshold=" + config.getTpsThreshold() + ". Reducing view distance to " + newView);
        }

        if (ramPercent > config.getRamThresholdPercent()) {
            Runtime.getRuntime().gc();
            plugin.getLogger().warning("Auto-throttle: RAM at " + String.format("%.0f", ramPercent) + "% — triggered GC");
        }
    }
}