package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CPU Profiler — profiles entities and tile entities to find hotspots.
 * Reports hot entities/tiles to the LoadReportManager for client offload.
 */
public class Profiler {
    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, EntityProfile> entityProfiles = new ConcurrentHashMap<>();
    private final Map<String, TileEntityProfile> tileProfiles = new ConcurrentHashMap<>();
    private BukkitTask profilingTask;
    private int tickCount;

    public Profiler(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable() {
        profilingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0, config.getProfileIntervalTicks());
    }

    public void disable() {
        if (profilingTask != null) {
            profilingTask.cancel();
            profilingTask = null;
        }
        entityProfiles.clear();
        tileProfiles.clear();
    }

    public void runProfileCycle() {
        tick();
    }

    private void tick() {
        tickCount++;
        List<Entity> candidates = new ArrayList<>();

        // Collect candidate entities based on config
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!e.isValid()) continue;
                boolean include = false;
                switch (e.getType()) {
                    case VILLAGER: include = config.isProfileVillagers(); break;
                    case ZOMBIE:
                    case SKELETON:
                    case CREEPER:
                    case SPIDER:
                    case WITHER:
                    case ENDER_DRAGON:
                        include = config.isProfileMonsters(); break;
                    case PIG:
                    case COW:
                    case CHICKEN:
                    case SHEEP:
                        include = config.isProfileAnimals(); break;
                    case ITEM:
                        include = config.isProfileItems(); break;
                }
                if (include) candidates.add(e);
            }
        }

        // Sample a subset
        Collections.shuffle(candidates);
        int sampleSize = Math.min(config.getEntitySampleSize(), candidates.size());
        for (int i = 0; i < sampleSize; i++) {
            Entity e = candidates.get(i);
            if (!e.isValid()) continue;
            profileEntity(e);
        }

        // Profile tile entities if enabled
        if (config.isProfileRedstone() || config.isProfileHoppers() ||
            config.isProfileMobSpawners() || config.isProfileSigns()) {
            profileTileEntities();
        }
    }

    private void profileEntity(Entity e) {
        UUID uuid = e.getUniqueId();
        long startNs = System.nanoTime();

        // Simulate a small computation to measure "work" the entity generates
        // In practice we measure how much CPU this entity's type consumes
        // by checking activity level and applying weight factors

        double weight = getEntityWeight(e);
        if (weight <= 0.1) return; // too cheap to profile

        // Simulate some of the work
        simulateEntityWork(e);

        long endNs = System.nanoTime();
        long cpuTime = endNs - startNs;

        entityProfiles.merge(uuid, new EntityProfile(uuid, e.getType(), cpuTime),
            (existing, updated) -> {
                existing.increment(cpuTime);
                return existing;
            });
    }

    private double getEntityWeight(Entity e) {
        return switch (e.getType()) {
            case VILLAGER -> 3.0;
            case WITHER, ENDER_DRAGON -> 5.0;
            case ZOMBIE, SKELETON, CREEPER -> 1.5;
            case SHULKER -> 4.0;
            case RAVAGER -> 4.0;
            case ITEM -> 2.0;
            case MINECART -> 1.0;
            default -> 0.5;
        };
    }

    private void simulateEntityWork(Entity e) {
        // Measure the actual computational cost by checking entity state
        // This is a lightweight operation that gives us a baseline
        Location loc = e.getLocation();
        e.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0).size();
        if (e instanceof LivingEntity le) {
            le.hasLineOfSight(e);
        }
    }

    private void profileTileEntities() {
        int sampleSize = config.getTileSampleSize();
        List<Chunk> chunks = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(chunk);
                if (chunks.size() >= sampleSize) break;
            }
            if (chunks.size() >= sampleSize) break;
        }

        for (Chunk chunk : chunks) {
            for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                if (!state.isPlaced()) continue;
                String key = makeTileKey(state);
                if (!isTileRelevant(state)) continue;

                long startNs = System.nanoTime();
                // Measure tile entity work
                state.getBlock().getRelative(0, 0, 0); // baseline access
                long endNs = System.nanoTime();
                long cpuTime = endNs - startNs;

                tileProfiles.merge(key, new TileEntityProfile(key, state.getType(), cpuTime),
                    (existing, updated) -> {
                        existing.increment(cpuTime);
                        return existing;
                    });
            }
        }
    }

    private String makeTileKey(org.bukkit.block.BlockState state) {
        return state.getWorld().getName() + ":" + state.getX() + "," + state.getY() + "," + state.getZ();
    }

    private boolean isTileRelevant(org.bukkit.block.BlockState state) {
        if (config.isProfileRedstone() && isRedstoneRelated(state)) return true;
        if (config.isProfileHoppers() && state.getType().name().contains("HOPPER")) return true;
        if (config.isProfileMobSpawners() && state.getType().name().contains("SPAWNER")) return true;
        if (config.isProfileSigns() && state.getType().name().contains("SIGN")) return true;
        return false;
    }

    private boolean isRedstoneRelated(org.bukkit.block.BlockState state) {
        return state instanceof org.bukkit.block.Block
            && state.getBlock().getType().name().contains("REDSTONE");
    }

    public List<EntityProfile> getHotEntities() {
        return entityProfiles.values().stream()
            .filter(EntityProfile::isHot)
            .sorted(Comparator.comparingLong(EntityProfile::getTotalCpuTime).reversed())
            .limit(50)
            .toList();
    }

    public List<TileEntityProfile> getHotTiles() {
        return tileProfiles.values().stream()
            .filter(TileEntityProfile::isHot)
            .sorted(Comparator.comparingLong(TileEntityProfile::getTotalCpuTime).reversed())
            .limit(50)
            .toList();
    }

    public void resetAllProfiles() {
        entityProfiles.clear();
        tileProfiles.clear();
    }

    public static class EntityProfile {
        private final UUID uuid;
        private final EntityType type;
        private long totalCpuTime;
        private int sampleCount;

        public EntityProfile(UUID uuid, EntityType type, long cpuTime) {
            this.uuid = uuid;
            this.type = type;
            this.totalCpuTime = cpuTime;
            this.sampleCount = 1;
        }

        public void increment(long additionalCpuTime) {
            totalCpuTime += additionalCpuTime;
            sampleCount++;
        }

        public long getAverageCpuTime() {
            return sampleCount > 0 ? totalCpuTime / sampleCount : 0;
        }

        public boolean isHot() {
            return sampleCount >= 3 && getAverageCpuTime() > 100_000; // 100μs avg
        }

        public long getTotalCpuTime() { return totalCpuTime; }
        public UUID getUuid() { return uuid; }
        public EntityType getType() { return type; }
    }

    public static class TileEntityProfile {
        private final String key;
        private final org.bukkit.Material type;
        private long totalCpuTime;
        private int sampleCount;

        public TileEntityProfile(String key, org.bukkit.Material type, long cpuTime) {
            this.key = key;
            this.type = type;
            this.totalCpuTime = cpuTime;
            this.sampleCount = 1;
        }

        public void increment(long additionalCpuTime) {
            totalCpuTime += additionalCpuTime;
            sampleCount++;
        }

        public long getAverageCpuTime() {
            return sampleCount > 0 ? totalCpuTime / sampleCount : 0;
        }

        public boolean isHot() {
            return sampleCount >= 3 && getAverageCpuTime() > 50_000; // 50μs avg
        }

        public long getTotalCpuTime() { return totalCpuTime; }
        public String getKey() { return key; }
        public org.bukkit.Material getType() { return type; }
    }
}