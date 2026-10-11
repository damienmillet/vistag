# Changelog

# VisTag 1.2.0

Target: Paper 26.2 / Java 25.

### Changed

- **Toggle is now admin-controlled** — `/vistag toggle <player>` (ops only, permission `vistag.admin`) replaces the old self-service `/vistag toggle`. Only server operators decide which players have nametag filtering enabled or disabled.
- **Toggles are persisted** — the list of disabled players is saved in `config.yml` under `toggled-off-players` (UUIDs) and survives restarts; it is no longer reset when a player disconnects.
- The `vistag.toggle` permission has been removed.

### Added

- New config key: `toggled-off-players` (managed by the plugin, no manual editing needed).
- `/vistag toggle <player>` supports selectors (e.g. `@a`) and notifies both the admin and the affected player(s).
# VisTag 1.1.0

Target: Paper 26.2 / Java 25.

### Added

- **Sneak hiding** — sneaking players now have their nametag hidden (config: `hide-when-sneaking`, default `true`).
- **FOV cone** — optional view cone: a nametag is visible only if the target is inside the viewer's cone (config: `fov-cone-degrees`, default `0` = disabled).
- **Per-player toggle** — new `/vistag toggle` command: each player can enable/disable nametag filtering for themselves (permission `vistag.toggle`, default: everyone).
- **Exact raycast** — line of sight now tests real block collision shapes (voxel shapes, segment-vs-box intersection); slabs and stairs no longer hide nametags when the sight line passes above them (config: `exact-raycast`, default `true`; `false` keeps the faster block-center test).
- New permissions declared in `paper-plugin.yml`: `vistag.admin` (manage lists, reload), `vistag.toggle`.

### Changed

- Configuration gains three new keys: `hide-when-sneaking`, `fov-cone-degrees`, `exact-raycast` (safe defaults, no migration needed).
# VisTag 1.0.0

Initial release.

- Nametags are only visible when the line of sight between the two players' eyes is free of occluding blocks — per-viewer, using a dedicated scoreboard team (`NAME_TAG_VISIBILITY = NEVER`) for each player.
- Transparency-aware defaults: only occluding blocks hide nametags; glass, leaves, water, slabs… do not.
- Configurable block lists, editable in-game and persisted to `config.yml`:
  - `/vistag exclude add|list|remove <block>` — blocks that never hide nametags (highest priority).
  - `/vistag include add|list|remove <block>` — blocks that always hide nametags.
- `/vistag reload` — reload the configuration.
- Config keys: `excluded`, `included`, `check-interval-ticks` (default `10`), `max-distance` (default `64`).
- Commands use the Brigadier API; block IDs accept `glass` and `minecraft:glass` formats.
- Built for Paper 26.2 / Java 25.