# KingdomCome

A Paper (26.3) territory & kingdom management plugin with chunk claiming, PvP rules, and live BossBar territory indicators.

## Features

- **Kingdoms & Roles:** Create and disband kingdoms, `LEADER` / `DUKE` / `MEMBER` hierarchy, and automatic username syncing on join.
- **Chunk Claiming & Land Protection:** Chunk-based claims that block non-members from breaking or placing blocks (with OP bypass).
- **Smart PvP Rules:** No friendly fire between kingdom members anywhere, and PvP is only allowed when both players are in unclaimed wilderness (covers melee, projectiles, TNT/crystals, fire, fishing rods, and harmful splash/lingering potions).
- **Territory BossBar:** Live Adventure BossBar showing whose land you're in, color-coded by relationship (`Green` = your kingdom, `Blue` = another kingdom, `Yellow` = unaffiliated).
- **Kingdom Homes:** Set a kingdom home and asynchronously teleport back to it.

## Requirements & Installation

- **Server Software:** Paper `26.3` (built with Folia/region-scheduler-safe APIs).
- **Database:** Zero-config **SQLite** (`plugins/KingdomCome/KingdomCome.db` is created and migrated automatically).
- **Dependencies:** `KingdomComeLoader` automatically resolves `HikariCP` and `jctools-core` from Maven Central on startup (internet access is required on first boot).

1. Download or build the plugin `.jar`.
2. Place it into your server's `plugins/` folder.
3. Start or restart the server.

## Commands & Permissions

All commands are grouped under `/k`.

| Command | Description | Who Can Use It |
| --- | --- | --- |
| `/k create <KingdomName> <leader_player>` | Creates a kingdom and assigns the target player as Leader | OP or `kingdomcome.create` |
| `/k disband <KingdomName>` | Disbands a kingdom and removes all its claims and members | OP or `kingdomcome.disband` |
| `/k add <PlayerName>` | Adds an online player to your kingdom | `LEADER`, `DUKE` |
| `/k kick <PlayerName> [UUID]` | Kicks a member from your kingdom (use `[UUID]` if duplicate names exist) | `LEADER`, `DUKE` |
| `/k claim` | Claims the chunk you are standing in for your kingdom | `LEADER`, `DUKE` |
| `/k unclaim` | Unclaims the chunk you are standing in | `LEADER`, `DUKE` |
| `/k set_home` | Sets your kingdom's home to your current location | `LEADER`, `DUKE` |
| `/k tp_home` | Teleports you to your kingdom's home | Any kingdom member |

### Roles

- **LEADER:** Full control over the kingdom, including kicking Dukes and managing claims, members, and kingdom home.
- **DUKE:** Can claim/unclaim chunks, set kingdom home, and add/kick regular `MEMBER`s (cannot kick the Leader or other Dukes).
- **MEMBER:** Protected by kingdom claims and can use `/k tp_home`.

## Technical Overview

- **Storage:** SQLite via HikariCP in `WAL` mode with versioned schema migrations (`PRAGMA user_version`).
- **Performance:** Lock-free chunk lookup cache using JCTools `NonBlockingHashMapLong` with bit-packed `(chunkX, chunkZ)` keys.
- **Concurrency:** Non-blocking database queries via `CompletableFuture` dispatched back onto player/region threads.

## TODO / Roadmap

- **Wars:** Implement `/k declarewar <KingdomName>` and `/k cancelwar`, allow wartime PvP inside enemy claims, and display a `Red` BossBar in warring territory.
- **Kingdom Chat:** Implement `/kc` for private kingdom-only chat.
- **Role Management:** Add commands to promote/demote `DUKE`s and transfer leadership.
- **Discord Integration:** Hook up the `discord_id` field already present in the database schema.
