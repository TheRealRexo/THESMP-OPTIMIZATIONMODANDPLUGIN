# THE SMP BedWars Optimization Mod

<div align="center">

A high-performance client-side optimization mod + server plugin duo designed specifically for **THE SMP's BedWars server**. This mod dramatically improves FPS, reduces lag spikes, and offloads heavy computational work from the server to capable clients — all while adding a **Hypixel-style health display** (number + hearts over players).

[![GitHub](https://img.shields.io/badge/GitHub-Repo-blue?logo=github)](https://github.com/TheRealRexo/THESMP-OPTIMIZATIONMODANDPLUGIN)
[![Modrinth](https://img.shields.io/badge/Modrinth-Page-green?logo=modrinth)](https://modrinth.com/mod/thesmpbedwarsoptimizationmod)
[![Discord](https://img.shields.io/badge/Discord-THE_SMP-5865F2?logo=discord)](https://discord.com/invite/xvxYN2F3vb)
[![Minecraft](https://img.shields.io/badge/MC-1.20.4%20%2B-orange?logo=microsoftminecraft)]
[![Java](https://img.shields.io/badge/Java-21+-blue?logo=openjdk)]

</div>

---

## 🎯 What This Project Does

This is a **two-part system**:

| Component | Purpose |
|-----------|---------|
| **Client Mod** (this project) | Installed by players — registers CPU capability, receives offloaded work, renders health displays locally |
| **Server Plugin** (companion) | Profiles CPU hotspots, distributes work to capable clients, enforces mod requirement, sends health packets |

> **The server plugin is required for the mod to function.** The mod alone does nothing without the plugin installed on the BedWars server.

---

## ⚡ Core Features

### 1. Dynamic Client-Side Load Offloading
The server continuously profiles entity and tile-entity CPU usage (villagers, monsters, redstone, hoppers, spawners, items, etc.). When server load exceeds thresholds, it **offloads computational tasks to capable Java clients** that have the mod installed.

- **Automatic CPU capability detection** — The mod estimates your client's power based on available CPU cores
- **Smart work distribution** — Tasks are split proportionally: stronger clients get more work
- **Real-time adaptation** — Recalculated every poll interval based on current TPS, RAM usage, and number of capable clients
- **Zero configuration required** — Just install the mod and join; registration is automatic

### 2. Hypixel-Style Health Display (Number + Hearts)
Instead of the server rendering bossbars or holograms for every player, **each client renders its own health display locally**:

- **Health number** (e.g., `10` or `10.5`) above each player
- **Heart icons** (♥♥♥♡♡) showing current/max health visually
- **Absorption hearts** supported (golden/yellow hearts)
- **Health calculation done by the player's own client** — not the observer's client
- **Massive server tick savings** — No more per-player bossbar updates on the main thread

### 3. Mod Enforcement (Java Players Only)
- **Java players MUST have the mod v1.1.0+** to join — automatic kick after grace period
- **Bedrock players (Geyser/Floodgate) are fully supported** — no mod required, handled server-side
- **Geyser/Floodgate offload** — Under heavy load, Bedrock processing work can be offloaded to capable Java clients

---

## 🛠 How It Works (Technical Overview)

### Server-Side (Plugin)
1. **Profiler** samples entities/tile entities every N ticks, measuring CPU time per entity type
2. **LoadReportManager** aggregates hotspots and calculates server load factor (TPS, RAM, entity counts)
3. **ClientComm** identifies capable clients (mod installed + CPU capability ≥ threshold)
4. **Dynamic offload** — Work packets sent to clients proportional to their capability
5. **HealthDisplayManager** sends HealthDisplayPackets to each player's own client every 2 ticks (100ms)

### Client-Side (Mod)
1. **Auto-registration** on join — sends CPU cores/capability to server via plugin channel `bedwars:register`
2. **ClientOffloadManager** receives offload tasks, executes them on a background thread pool (cores/2 threads)
3. **HealthDisplayManager** caches HealthDisplayPackets and renders number + hearts in the render loop
4. **Ping equalization** was removed in v1.1.0 (unreliable) — replaced by health display

---

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| Minecraft | 1.20.4+ (tested on 1.20.4, 1.21.x) |
| Java | 21+ |
| Server Plugin | **BedWarsOptimizer v1.1.0+** (companion plugin, required) |
| Mod Loader | Fabric, Forge, NeoForge, Quilt |

---

## 🎮 Why Download This?

| If you're a... | You'll get... |
|----------------|---------------|
| **Regular BedWars player** | Smoother FPS, fewer lag spikes during big fights, health numbers/hearts over enemies |
| **Server admin (THE SMP)** | Drastically reduced main-thread CPU usage, scalable to 100+ players, Bedrock support (With geyser)|
| **Modpack creator** | Lightweight, zero-config client mod that "just works" on Fabric, Forge, NeoForge, Quilt |
| **Developer** | Clean architecture: profiling → offload → render separation, plugin channels, multi-loader support (Fabric, Forge, NeoForge, Quilt) |

---

## 🔧 Configuration (Server-Side)

The plugin exposes a rich `config.yml` — key options:

```yaml
# Profiling
profile-mode: "ADAPTIVE"        # OFF | FIXED | ADAPTIVE
profile-interval-ticks: 20      # How often to profile
entity-sample-size: 200         # Entities sampled per cycle
profile-villagers: true
profile-monsters: true
profile-animals: false
profile-items: true
profile-redstone: true
profile-hoppers: true
profile-mob-spawners: true
profile-signs: false

# Offload
offload-enabled: true
poll-interval-seconds: 5
client-cpu-threshold: 0.6       # Minimum CPU capability (0.0-1.0)
max-offload-per-tick: 50

# Health Display
health-display-enabled: true
health-display-interval-ticks: 2

# Geyser/Floodgate
floodgate-offload-enabled: true

# Enforcement
kick-no-mod: true
grace-period-seconds: 30
```

> **Client mod has no config** — it's fully automatic.

---

## 📦 Installation

### Players (Client Mod)
1. Download the correct JAR for your loader from **Releases** (or Modrinth)
   - **Fabric** → `bedwars-optimizer-fabric-<version>.jar`
   - **Forge** → `bedwars-optimizer-forge-<version>.jar`
   - **NeoForge** → `bedwars-optimizer-neoforge-<version>.jar`
   - **Quilt** → `bedwars-optimizer-quilt-<version>.jar`
2. Place in your `mods` folder
3. Join **THE SMP BedWars** — mod auto-registers

### Server Admins (Plugin)
1. Download `BedWarsOptimizer.jar` from the companion repo
2. Place in `plugins/` folder
3. Restart server — generates `config.yml` in `plugins/BedWarsOptimizer/`
4. Tweak config as needed, `/bwopt reload` to apply changes

---

## 🔗 Links

- **Server Discord:** [THE SMP](https://discord.com/invite/xvxYN2F3vb) — join for BedWars IP, support, updates
- **Issues & Bug Reports:** [GitHub Issues](https://github.com/TheRealRexo/THESMP-OPTIMIZATIONMODANDPLUGIN/issues)
- **Documentation:** [Wiki Home](../../wiki/Home)
- **Architecture Guide:** [Architecture Wiki](../../wiki/Architecture)
- **Installation Guide:** [Installation Wiki](../../wiki/Installation)

---

## 📄 License

**MIT License** — free to use, modify, distribute. See `LICENSE` file.

---

## 🙏 Credits

- **Lead Developer:** [TheRealRexo](https://github.com/TheRealRexo)
- **Built for:** THE SMP Bedwars Server
- **Inspiration:** Hypixel BedWars health display, Sodium's rendering optimizations

---

> **Note:** This mod is **specific to THE SMP's BedWars server**. It will not function on other servers unless they also run the companion `BedWarsOptimizer` plugin. If you're a server admin wanting this for your own BedWars server, you're welcome to fork and adapt the plugin — the architecture is server-agnostic.
