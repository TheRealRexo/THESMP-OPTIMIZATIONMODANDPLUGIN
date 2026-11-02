package com.rexo.bedwars.optimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModEnforcer — ensures every player on the BedWars server has the
 * BedWarsOptimizer client mod installed. Players who haven't registered
 * their mod within the grace period are kicked.
 *
 * The client mod registers itself via ClientComm.registerClient(),
 * which is called when the client's ClientOffloadManager sends a handshake.
 */
public class ModEnforcer implements Listener {

    private final BedWarsOptimizer plugin;
    private final ProfilerConfig config;
    private final Map<UUID, GracePeriod> activeGracePeriods = new ConcurrentHashMap<>();
    private final Map<UUID, Long> registeredClients = new ConcurrentHashMap<>();

    public ModEnforcer(BedWarsOptimizer plugin, ProfilerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Called when a player joins. Starts the grace period countdown.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If enforcement is disabled, don't track
        if (!config.isModEnforcementEnabled()) return;

        // Check if already registered (shouldn't happen on first join, but be safe)
        if (registeredClients.containsKey(uuid)) {
            plugin.getLogger().fine("Player " + player.getName() + " already registered with mod");
            return;
        }

        GracePeriod grace = new GracePeriod(player, uuid);
        activeGracePeriods.put(uuid, grace);
        grace.start();

        plugin.getLogger().fine("Started grace period for " + player.getName() +
            " (" + config.getModGracePeriodTicks() + " ticks)");
    }

    /**
     * Called when a player leaves. Cancels their grace period.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        GracePeriod grace = activeGracePeriods.remove(uuid);
        if (grace != null) {
            grace.cancel();
        }
        registeredClients.remove(uuid);
    }

    /**
     * Register a client mod handshake. Called by ClientComm when a client
     * successfully sends its registration packet.
     */
    public void registerClient(UUID uuid) {
        registeredClients.put(uuid, System.currentTimeMillis());

        // Cancel the grace period if one is active
        GracePeriod grace = activeGracePeriods.remove(uuid);
        if (grace != null) {
            grace.cancel();
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(net.kyori.adventure.text.Component.text("§a§lBedWarsOptimizer §7— mod detected! Profiling enabled."));
            }
        }

        plugin.getLogger().fine("Client mod registered: " + uuid);
    }

    /**
     * Check if a player has the mod registered.
     */
    public boolean hasMod(UUID uuid) {
        return registeredClients.containsKey(uuid);
    }

    /**
     * Get the number of registered clients.
     */
    public int getRegisteredCount() {
        return registeredClients.size();
    }

    /**
     * Get the number of players currently in grace period.
     */
    public int getGracePeriodCount() {
        return activeGracePeriods.size();
    }

    /**
     * Represents an active grace period for a player who hasn't registered yet.
     */
    private class GracePeriod {
        private final Player player;
        private final UUID uuid;
        private final long expireTime;
        private final BukkitTask task;
        private boolean cancelled = false;

        GracePeriod(Player player, UUID uuid) {
            this.player = player;
            this.uuid = uuid;
            this.expireTime = System.currentTimeMillis() +
                (config.getModGracePeriodTicks() * 50L); // 50ms per tick

            // Schedule a task to check every second
            this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkGracePeriod, 20L, 20L);
        }

        void start() {
            // Send reminder messages
            sendReminder();
        }

        void checkGracePeriod() {
            if (cancelled) return;

            if (!player.isOnline()) {
                cancel();
                return;
            }

            long remaining = expireTime - System.currentTimeMillis();
            long remainingSeconds = remaining / 1000;

            // Send reminder at certain thresholds
            if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds == 10) {
                sendReminder(remainingSeconds);
            }

            // Grace period expired — kick the player
            if (remaining <= 0) {
                kickPlayer();
            }
        }

        void sendReminder() {
            player.sendMessage("§e§lBedWarsOptimizer §7— Install the client mod to join this server.");
            player.sendMessage("§7Download: §bgithub.com/TheRealRexo/THESMP-OPTIMIZATIONMODANDPLUGIN");
        }

        void sendReminder(long remainingSeconds) {
            player.sendMessage("§c§lBedWarsOptimizer §7— You have §e" + remainingSeconds +
                "s §7remaining to install the mod before being kicked.");
        }

        void kickPlayer() {
            if (cancelled) return;
            cancelled = true;

            if (task != null) {
                task.cancel();
            }

            String kickMessage = config.getModKickMessage()
                .replace("\\n", "\n")
                .replace("&", "§");

            if (player.isOnline()) {
                player.kickPlayer(kickMessage);
            }

            activeGracePeriods.remove(uuid);
            plugin.getLogger().warning("Kicked " + player.getName() +
                " for not having the BedWarsOptimizer client mod.");
        }

        void cancel() {
            if (cancelled) return;
            cancelled = true;
            if (task != null) {
                task.cancel();
            }
        }
    }
}