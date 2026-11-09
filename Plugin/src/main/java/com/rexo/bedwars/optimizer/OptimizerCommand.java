package com.rexo.bedwars.optimizer;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command handler for /bwopt — profiling, offload, and health display commands.
 */
public class OptimizerCommand implements CommandExecutor, TabCompleter {
    private final BedWarsOptimizer plugin;

    public OptimizerCommand(BedWarsOptimizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("bwopt.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                showStatus(player);
                break;
            case "profile":
                profileNow(player);
                break;
            case "offload":
                showOffload(player);
                break;
            case "health":
                showHealth(player);
                break;
            case "geyser":
                showGeyser(player);
                break;
            case "reset":
                resetProfile(player);
                break;
            case "help":
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== BedWarsOptimizer Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/bwopt status" + ChatColor.GRAY + " — Show current profiling status");
        player.sendMessage(ChatColor.YELLOW + "/bwopt profile" + ChatColor.GRAY + " — Run an immediate profile cycle");
        player.sendMessage(ChatColor.YELLOW + "/bwopt offload" + ChatColor.GRAY + " — Show offload targets");
        player.sendMessage(ChatColor.YELLOW + "/bwopt health" + ChatColor.GRAY + " — Show health display status");
        player.sendMessage(ChatColor.YELLOW + "/bwopt geyser" + ChatColor.GRAY + " — Show Geyser/Floodgate offload status");
        player.sendMessage(ChatColor.YELLOW + "/bwopt reset" + ChatColor.GRAY + " — Reset all profiles");
        player.sendMessage(ChatColor.YELLOW + "/bwopt help" + ChatColor.GRAY + " — Show this help");
    }

    private void showStatus(Player player) {
        ProfilerConfig cfg = plugin.getProfilerConfig();
        double[] tps = org.bukkit.Bukkit.getTPS();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        double memPercent = ((double)(totalMem - freeMem) / maxMem) * 100.0;

        player.sendMessage(ChatColor.GREEN + "=== BedWarsOptimizer Status ===");
        player.sendMessage(ChatColor.GRAY + "Mode: " + cfg.getProfileMode());
        player.sendMessage(ChatColor.GRAY + "TPS: " + String.format("%.2f", tps[0]));
        player.sendMessage(ChatColor.GRAY + "RAM: " + String.format("%.1f%%", memPercent));
        player.sendMessage(ChatColor.GRAY + "Auto-throttle: " + cfg.isAutoThrottle());
        player.sendMessage(ChatColor.GRAY + "Offload: " + cfg.isOffloadEnabled());
        player.sendMessage(ChatColor.GRAY + "Health-display: " + cfg.isHealthDisplayEnabled()
                + " (hearts: " + cfg.isHealthShowHearts() + ", number: " + cfg.isHealthShowNumber() + ")");
        player.sendMessage(ChatColor.GRAY + "Geyser-offload: " + cfg.isFloodgateOffloadEnabled());
        player.sendMessage(ChatColor.GRAY + "Hot entities: " + plugin.getHotEntities());
        player.sendMessage(ChatColor.GRAY + "Hot tiles: " + plugin.getHotTiles());
    }

    public void profileNow(Player player) {
        plugin.getLogger().info("Manual profile triggered by " + player.getName());
        plugin.getProfiler().runProfileCycle();
        player.sendMessage(ChatColor.GREEN + "Profile cycle complete. Check console for results.");
    }

    private void showOffload(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== Offload Status ===");
        var clients = plugin.getModEnforcer().getRegisteredClients();
        player.sendMessage(ChatColor.GRAY + "Registered clients (Java+mod): " + clients.size());
        int capable = 0;
        for (var info : clients.values()) {
            if (plugin.getClientComm().isClientCapable(info.getPlayerId())) {
                capable++;
                Player p = plugin.getServer().getPlayer(info.getPlayerId());
                String name = p != null ? p.getName() : info.getPlayerId().toString();
                player.sendMessage(ChatColor.YELLOW + "  " + name + " (cpu:"
                        + String.format("%.2f", info.getCpuCapability()) + ")");
            }
        }
        player.sendMessage(ChatColor.GRAY + "Capable clients: " + capable);
        player.sendMessage(ChatColor.GRAY + "Bedrock players: " + plugin.getModEnforcer().getBedrockPlayerCount());
        player.sendMessage(ChatColor.GRAY + "Server load: " + String.format("%.2f", plugin.getModEnforcer().getServerLoadFactor()));
    }

    private void showHealth(Player player) {
        HealthDisplayManager hd = plugin.getHealthDisplayManager();
        if (!hd.isActive()) {
            player.sendMessage(ChatColor.RED + "Health display is disabled.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "=== Health Display ===");
        player.sendMessage(ChatColor.GRAY + "Enabled: " + hd.isActive());
        player.sendMessage(ChatColor.GRAY + "Send interval: " + hd.getSendIntervalTicks() + " ticks");
        player.sendMessage(ChatColor.GRAY + "Cached players: " + hd.getCachedPlayerCount());

        for (Map.Entry<UUID, Double> entry : hd.getCachedHealthMap().entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                double health = entry.getValue();
                int hearts = (int) Math.ceil(health / 2.0);
                player.sendMessage(ChatColor.YELLOW + "  " + p.getName() + ": "
                        + ChatColor.RED + String.format("%.1f", health) + " HP "
                        + ChatColor.GRAY + "(" + hearts + " hearts)"
                        + (plugin.getModEnforcer().isBedrock(entry.getKey()) ? " [Bedrock]" : ""));
            }
        }
    }

    private void showGeyser(Player player) {
        ModEnforcer enforcer = plugin.getModEnforcer();
        player.sendMessage(ChatColor.GREEN + "=== Geyser/Floodgate Offload ===");
        player.sendMessage(ChatColor.GRAY + "Enabled: " + plugin.getProfilerConfig().isFloodgateOffloadEnabled());
        player.sendMessage(ChatColor.GRAY + "Bedrock players online: " + enforcer.getBedrockPlayerCount());
        player.sendMessage(ChatColor.GRAY + "Tasks offloaded: " + enforcer.getGeyserOffloadCount());
        player.sendMessage(ChatColor.GRAY + "Server needs offload: " + enforcer.needsGeyserOffload());
    }

    private void resetProfile(Player player) {
        plugin.getProfiler().resetAllProfiles();
        plugin.resetCounters();
        player.sendMessage(ChatColor.GREEN + "All profiles reset.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> options = Arrays.asList("status", "profile", "offload", "health", "geyser", "reset", "help");
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase())) {
                    completions.add(opt);
                }
            }
            return completions;
        }
        return List.of();
    }
}