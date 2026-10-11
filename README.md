# VisTag

A **PaperMC 1.21.x** plugin — player nametags are only visible when no opaque block stands between two players. **Transparent** blocks (glass, leaves, water…) do not hide nametags.

## ✨ Features

- Per-viewer nametag hiding (each player has their own perception).
- [Transparency](https://minecraft.wiki/w/Transparency) aware: only occluding blocks hide by default.
- Configurable **exclusion** (never hides) and **inclusion** (always hides) block lists, editable in-game, persisted to `config.yml`.
- Configurable max distance and check frequency.

## 📦 Installation

1. Drop `vistag-1.0.0.jar` into your server's `plugins/` folder.
2. Restart the server (not `/reload`).
3. `plugins/VisTag/config.yml` is generated on first startup.

**Requirements**: Paper 1.21.x, Java 21.

## 🛠️ Build

```bash
./gradlew build   # JAR in build/libs/
```

Depends on `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`.

## ⚙️ Configuration (`config.yml`)

```yaml
# Blocks that NEVER hide nametags, even opaque ones (highest priority).
excluded: []
# Blocks that ALWAYS hide nametags, even transparent ones.
included: []
# Update frequency in ticks (20 = 1 s).
check-interval-ticks: 10
# Maximum distance (in blocks) at which a nametag can be visible.
max-distance: 64
```

Priority: `excluded` > `included` > default behavior (`isOccluding`).

## 🕹️ Commands

Alias: `/vt` — Permission: `vistag.admin`

| Command | Effect |
|---|---|
| `/vistag exclude add <block>` | This block will never hide nametags |
| `/vistag exclude list` | List exclusions |
| `/vistag exclude remove <block>` | Remove an exclusion |
| `/vistag include add <block>` | This block will always hide nametags |
| `/vistag include list` | List inclusions |
| `/vistag include remove <block>` | Remove an inclusion |
| `/vistag reload` | Reload the configuration |

Block IDs accept both `glass` and `minecraft:glass` formats.

## 🧠 Technical overview

- On join, each player receives their **own scoreboard** containing a team with `NAME_TAG_VISIBILITY = NEVER`.
- Every `check-interval-ticks`, a raycast (0.5-block steps, tolerance margin near the eyes) is cast between each pair of players in the same world.
- If an occluding (or `included`) block is hit → the nametag is hidden for that viewer. Otherwise → visible.
- Per-viewer hiding is not achievable in vanilla (`/team` is global).

## ⚠️ Known limitations

- The raycast tests the block center, not the exact hitbox: slabs/stairs may hide a nametag even when the line of sight passes above them → use `exclude`/`include` case by case.
- Nametags are not hidden from spectators.
- Checks are periodic, not instant (default: 2×/s).

## 🗺️ Roadmap ideas

- Also hide while sneaking.
- FOV cone control (visible only when looked at).
- Per-player `/vistag toggle`.
- Exact raycast on collision shapes (voxel shape).

---
Author: damien millet
