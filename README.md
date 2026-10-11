# VisTag

A **PaperMC 26.2** plugin — player nametags are only visible when no opaque block stands between two players. **Transparent** blocks (glass, leaves, water…) do not hide nametags.

## ✨ Features

- Per-viewer nametag hiding (each player has their own perception).
- [Transparency](https://minecraft.wiki/w/Transparency) aware: only occluding blocks hide by default.
- Configurable **exclusion** (never hides) and **inclusion** (always hides) block lists, editable in-game, persisted to `config.yml`.
- **Sneak hiding**: sneak to hide your nametag (`hide-when-sneaking`).
- **FOV cone**: nametags visible only inside the viewer's view cone (`fov-cone-degrees`, `0` = disabled).
- **Admin-controlled toggle**: `/vistag toggle <player>` lets admins decide who has nametag filtering enabled — persisted across restarts.
- **Exact raycast** on block collision shapes (voxel shapes), with a faster coarse mode.
- Configurable max distance and check frequency.

## 📦 Installation

1. Drop `vistag-1.2.0-26.2.jar` into your server's `plugins/` folder.
2. Restart the server (not `/reload`).
3. `plugins/VisTag/config.yml` is generated on first startup.

**Requirements**: Paper 26.2, Java 25.

## 🛠️ Build

```bash
./gradlew build   # JAR in build/libs/
```

Depends on `io.papermc.paper:paper-api:26.2.build.133-stable`.

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
# Hide nametags of sneaking players.
hide-when-sneaking: true
# FOV cone half-angle in degrees. 0 disables the cone.
fov-cone-degrees: 0
# Use exact block collision shapes for the raycast (voxel shape).
# false = block-center test (faster, coarser).
exact-raycast: true
# Players (UUIDs) whose nametag filtering is disabled by an admin.
# Managed via /vistag toggle <player>.
toggled-off-players: []
```

Priority: `excluded` > `included` > default behavior (`isOccluding`).

## 🕹️ Commands

Alias: `/vt`

| Command | Effect | Permission |
|---|---|---|
| `/vistag toggle <player>` | Enable/disable filtering for a player (persisted) | `vistag.admin` |
| `/vistag exclude add <block>` | This block will never hide nametags | `vistag.admin` (default: op) |
| `/vistag exclude list` | List exclusions | `vistag.admin` |
| `/vistag exclude remove <block>` | Remove an exclusion | `vistag.admin` |
| `/vistag include add <block>` | This block will always hide nametags | `vistag.admin` |
| `/vistag include list` | List inclusions | `vistag.admin` |
| `/vistag include remove <block>` | Remove an inclusion | `vistag.admin` |
| `/vistag reload` | Reload the configuration | `vistag.admin` |

Block IDs accept both `glass` and `minecraft:glass` formats.

## 🧠 Technical overview

- On join, each player receives their **own scoreboard** containing a team with `NAME_TAG_VISIBILITY = NEVER`.
- Every `check-interval-ticks`, for each pair of players in the same world the plugin checks:
  1. the viewer did not toggle VisTag off,
  2. the target is not sneaking (if enabled),
  3. the target is inside the FOV cone (if enabled),
  4. the line of sight between both eyes is free.
- The exact raycast collects candidate blocks along the segment (0.5-block steps) and tests **segment-vs-collision-box** intersection for every box of each block's voxel shape (slab method).
- Per-viewer hiding is not achievable in vanilla (`/team` is global).

## ⚠️ Known limitations

- Nametags are not hidden from spectators.
- Checks are periodic, not instant (default: 2×/s).
- The FOV cone is computed on the horizontal+vertical eye direction: very close targets at the cone edge may flicker slightly between ticks.

## 🗺️ Roadmap ideas

- Per-target toggle (hide your own nametag from everyone).
- Configurable sneak override for spectators / staff.
