# KingdomCome

A Paper (26.3) territory & kingdom management plugin with chunk claiming, PvP rules, and live BossBar territory
indicators.
No ai used in coding this plugin, the only part where ai was used was finding bugs, in fixing those bugs or writing the
plugin no ai was used.

## Features

- **Kingdoms & Roles:** Create and disband kingdoms, `LEADER` / `DUKE` / `MEMBER` hierarchy, and automatic username
  syncing on join.
- **Chunk Claiming & Land Protection:** Chunk-based claims that block non-members from breaking or placing blocks (with
  OP bypass).
- **Smart PvP Rules:** No friendly fire between kingdom members anywhere, and PvP is only allowed when both players are
  in unclaimed wilderness (covers melee, projectiles, TNT/crystals, fire, fishing rods, and harmful splash/lingering
  potions).
- **Territory BossBar:** Live Adventure BossBar showing whose land you're in, color-coded by relationship (`Green` =
  your kingdom, `Blue` = another kingdom, `Yellow` = unaffiliated).
- **Kingdom Homes:** Set a kingdom home and asynchronously teleport back to it.

## Requirements & Installation

- **Server Software:** Paper `26.3` (built with Folia/region-scheduler-safe APIs).
- **Database:** Zero-config **SQLite** (`plugins/KingdomCome/KingdomCome.db` is created and migrated automatically).
- **Dependencies:** `KingdomComeLoader` automatically resolves `HikariCP` and `jctools-core` from Maven Central on
  startup (internet access is required on first boot).

1. Download or build the plugin `.jar`.
2. Place it into your server's `plugins/` folder.
3. Start or restart the server.

## Commands & Permissions

All commands are grouped under `/k`.

| Command                                   | Description                                                              | Who Can Use It              |
|-------------------------------------------|--------------------------------------------------------------------------|-----------------------------|
| `/k create <KingdomName> <leader_player>` | Creates a kingdom and assigns the target player as Leader                | OP or `kingdomcome.create`  |
| `/k disband <KingdomName>`                | Disbands a kingdom and removes all its claims and members                | OP or `kingdomcome.disband` |
| `/k add <PlayerName>`                     | Adds an online player to your kingdom                                    | `LEADER`, `DUKE`            |
| `/k kick <PlayerName> [UUID]`             | Kicks a member from your kingdom (use `[UUID]` if duplicate names exist) | `LEADER`, `DUKE`            |
| `/k claim`                                | Claims the chunk you are standing in for your kingdom                    | `LEADER`, `DUKE`            |
| `/k unclaim`                              | Unclaims the chunk you are standing in                                   | `LEADER`, `DUKE`            |
| `/k set_home`                             | Sets your kingdom's home to your current location                        | `LEADER`, `DUKE`            |
| `/k tp_home`                              | Teleports you to your kingdom's home                                     | Any kingdom member          |

### Roles

- **LEADER:** Full control over the kingdom, including kicking Dukes and managing claims, members, and kingdom home.
- **DUKE:** Can claim/unclaim chunks, set kingdom home, and add/kick regular `MEMBER`s (cannot kick the Leader or other
  Dukes).
- **MEMBER:** Protected by kingdom claims and can use `/k tp_home`.

## Technical Overview

KingdomCome is designed for low-overhead hot paths (movement, block protection, PvP checks) while keeping all database
I/O asynchronous and compatible with Paper's regionized/Folia threading model.

### 1. Lock-Free, Zero-Allocation Spatial Caching

- **Bit-Packed Chunk Keys:** Instead of allocating wrapper objects or `Location`/`Chunk` keys on every lookup,
  `ClaimService` packs 2D chunk coordinates `(chunkX, chunkZ)` into a single 64-bit primitive `long` using bit masking
  (`0xFFFFFFFFL`) to avoid sign-extension issues on negative coordinates.
- **Primitive Concurrent Maps:** Per-world claim caches (`ConcurrentHashMap<UUID, NonBlockingHashMapLong<Claim>>`) and
  the kingdom cache (`NonBlockingHashMapLong<Kingdom>`) use **JCTools `NonBlockingHashMapLong`**, enabling lock-free
  reads and eliminating `Long` autoboxing overhead during high-frequency events.
- **Startup Cache Warming & Write-Through Consistency:** All kingdoms, members, and claims are loaded into memory at
  startup (`KingdomCome#onEnable`). Mutations update the in-memory caches only **after** the underlying SQLite operation
  succeeds.

### 2. Async Pipeline & Region-Thread Safety

- **Folia / Region-Scheduler Ready:** All world, entity, and UI interactions dispatch through Paper's `RegionScheduler`,
  `GlobalRegionScheduler`, and `Entity#getScheduler()` rather than assuming a single global main thread (including
  retired-entity callbacks during `/k tp_home` via `player.teleportAsync`).
- **Declarative Async Error Handling (`CommandAsyncUtils`):** Command handlers use a custom `CompletableFuture` wrapper
  that unwraps `CompletionException`/`ExecutionException`, matches domain exceptions in registration order
  (`LinkedHashMap`), and marshals success/error responses back onto the executing player's owning region thread.
- **Observer-Driven UI Sync:** `ClaimService` and `MemberService` publish cache mutations to `ClaimChangeListener` and
  `MembershipChangeListener` (`CopyOnWriteArrayList`), allowing `KingdomBossbarListener` to immediately refresh or hide
  BossBars when claims change, members join/leave, or kingdoms are disbanded.

### 3. Database Architecture (SQLite + HikariCP)

- **Serialized Single-Writer Pool:** `DatabaseService` pairs a single-connection `HikariCP` pool (`maximumPoolSize = 1`)
  with a dedicated single-thread `ExecutorService`. This keeps all SQLite I/O off server/region threads while preventing
  `SQLITE_BUSY` write-lock contention.
- **SQLite Pragmas & Integrity:** Configured with `WAL` journal mode, `synchronous = NORMAL`, `busy_timeout = 5000`, and
  enforced `foreign_keys = true` with `ON DELETE CASCADE` cleanup across `Kingdoms`, `Players`, and `Claims` (plus
  indexed `kingdom_id` lookups).
- **Transactional Schema Migrations:** Built-in migration runner versioned via SQLite's `PRAGMA user_version`, executing
  upgrades inside explicit transactions with automatic rollback on failure.
- **Atomic Operations & Graceful Shutdown:** Multi-step writes (such as `createKingdomWithLeader`) run inside manual SQL
  transactions with rollback handling. On disable, `DatabaseService#close()` drains pending async DB tasks (up to 60s
  timeout) before closing the connection pool.

### 4. Event Filtering & Comprehensive PvP Guarding

- **Short-Circuited Movement Checks:** `KingdomBossbarListener` exits early on `PlayerMoveEvent` unless the player
  actually crosses a chunk boundary (`blockCoord >> 4`), and caches each player's `lastKingdom` to skip redundant
  BossBar updates while moving within the same kingdom's territory.
- **Multi-Vector PvP Protection (`PvPListener`):** Enforces friendly-fire and claim PvP rules across 7 distinct vectors:
    - Direct swings (`PrePlayerAttackEntityEvent`, cancelling before knockback/sounds)
    - Projectiles, tridents, crystals, and TNT (`EntityDamageByEntityEvent`)
    - Indirect player-attributed damage such as fire/poison/wither (`EntityDamageEvent` via
      `DamageSource#getCausingEntity()`)
    - Flame bows / Fire Aspect (`EntityCombustByEntityEvent`)
    - Fishing rod hooking (`PlayerFishEvent`)
    - Harmful splash potions (`PotionSplashEvent`, zeroing intensity per protected player without cancelling buffs)
    - Lingering harmful clouds (`AreaEffectCloudApplyEvent`)
- **Lean Packaging:** Uses Paper's `PluginLoader` (`KingdomComeLoader`) to resolve `HikariCP` and `jctools-core` at
  runtime rather than shading heavy dependencies into the plugin JAR.

## TODO / Roadmap

- **Wars:** Implement `/k declarewar <KingdomName>` and `/k cancelwar`, allow wartime PvP inside enemy claims, and
  display a `Red` BossBar in warring territory.
- **Kingdom Chat:** Implement `/kc` for private kingdom-only chat.
- **Role Management:** Add commands to promote/demote `DUKE`s and transfer leadership.
- **Discord Integration:** Hook up the `discord_id` field already present in the database schema.
