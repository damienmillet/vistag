package dev.damien.vistag;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VisTagPlugin extends JavaPlugin {

    private static final String TEAM_NAME = "vistag_hidden";
    private static final double STEP = 0.5;

    private final MiniMessage mm = MiniMessage.miniMessage();

    // Blocks that NEVER hide the nametag (highest priority)
    private final Set<Material> excluded = new HashSet<>();
    // Blocks that ALWAYS hide the nametag (even transparent)
    private final Set<Material> included = new HashSet<>();
    // Players whose nametag filtering is disabled by an admin (they see all nametags)
    private final Set<UUID> toggledOff = new HashSet<>();

    private int checkInterval;
    private double maxDistance;
    private boolean hideWhenSneaking;
    private double fovConeDegrees;
    private boolean exactRaycast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadLists();

        Bukkit.getPluginManager().registerEvents(new TagListener(), this);

        // Brigadier commands (required with paper-plugin.yml)
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        VisTagCommand.build(this),
                        "Manage nametag visibility (VisTag)",
                        List.of("vt")
                ));

        Bukkit.getScheduler().runTaskTimer(this, this::updateAllViewers, 20L, checkInterval);
    }

    @Override
    public void onDisable() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(main);
        }
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    private void loadSettings() {
        checkInterval = getConfig().getInt("check-interval-ticks", 10);
        maxDistance = getConfig().getInt("max-distance", 64);
        hideWhenSneaking = getConfig().getBoolean("hide-when-sneaking", true);
        fovConeDegrees = Math.max(0.0, getConfig().getDouble("fov-cone-degrees", 0.0));
        exactRaycast = getConfig().getBoolean("exact-raycast", true);
        toggledOff.clear();
        for (String u : getConfig().getStringList("toggled-off-players")) {
            try {
                toggledOff.add(UUID.fromString(u));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID in 'toggled-off-players': " + u);
            }
        }
    }

    private void loadLists() {
        excluded.clear();
        included.clear();
        for (String s : getConfig().getStringList("excluded")) {
            Material m = Material.matchMaterial(s);
            if (m != null) excluded.add(m); else warn("excluded", s);
        }
        for (String s : getConfig().getStringList("included")) {
            Material m = Material.matchMaterial(s);
            if (m != null) included.add(m); else warn("included", s);
        }
    }

    private void warn(String list, String entry) {
        getLogger().warning("Unknown block in list '" + list + "': " + entry);
    }

    void saveLists() {
        FileConfiguration cfg = getConfig();
        cfg.set("excluded", excluded.stream().map(Enum::name).collect(Collectors.toList()));
        cfg.set("included", included.stream().map(Enum::name).collect(Collectors.toList()));
        saveConfig();
    }

    void reload() {
        reloadConfig();
        loadSettings();
        loadLists();
    }

    // ------------------------------------------------------------------
    // Per-player scoreboard: a team that hides nametags FOR THIS VIEWER
    // ------------------------------------------------------------------

    private Scoreboard viewerBoard(Player viewer) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Team team = board.registerNewTeam(TEAM_NAME);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(false);
        return board;
    }

    private Team hiddenTeam(Player viewer) {
        Team team = viewer.getScoreboard().getTeam(TEAM_NAME);
        if (team == null) {
            team = viewer.getScoreboard().registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    // ------------------------------------------------------------------
    // Visibility update
    // ------------------------------------------------------------------

    private void updateAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Team team = hiddenTeam(viewer);
            for (Player target : viewer.getWorld().getPlayers()) {
                if (target.equals(viewer)) continue;
                boolean visible = isVisibleTo(viewer, target);
                boolean hiddenNow = team.hasEntry(target.getName());
                if (visible && hiddenNow) {
                    team.removeEntry(target.getName());
                } else if (!visible && !hiddenNow) {
                    team.addEntry(target.getName());
                }
            }
        }
    }

    /**
     * true if the target's nametag should be visible to the viewer:
     * line of sight + not (sneaking while hide-when-sneaking) + inside FOV cone.
     */
    private boolean isVisibleTo(Player viewer, Player target) {
        if (toggledOff.contains(viewer.getUniqueId())) return true;
        if (hideWhenSneaking && target.isSneaking()) return false;

        Location from = viewer.getEyeLocation();
        Location to = target.getEyeLocation();
        if (from.getWorld() != to.getWorld()) return true;

        double dist = from.distance(to);
        if (dist > maxDistance) return false;
        if (dist < 1.0) return true;

        // FOV cone: angle between the viewer's look direction and the direction to the target
        if (fovConeDegrees > 0.0) {
            Vector look = viewer.getEyeLocation().getDirection().normalize();
            Vector toTarget = to.toVector().subtract(from.toVector()).normalize();
            double cosLimit = Math.cos(Math.toRadians(fovConeDegrees));
            if (look.dot(toTarget) < cosLimit) return false;
        }

        return hasLineOfSight(viewer, target);
    }

    /**
     * true if no occluding block separates the two players' eyes.
     * A block hides if: in "included", or occluding by default.
     * "excluded" blocks never hide (highest priority).
     */
    private boolean hasLineOfSight(Player viewer, Player target) {
        Location from = viewer.getEyeLocation();
        Location to = target.getEyeLocation();
        double dist = from.distance(to);

        // Tolerance margin near both eyes to avoid false positives when standing close
        double margin = Math.min(1.0, dist * 0.25);

        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        Location start = from.clone().add(direction.clone().multiply(margin));
        double length = dist - 2 * margin;

        World world = from.getWorld();

        if (exactRaycast) {
            return exactRayFree(world, start, direction, length);
        }

        // Fallback: block-center sampling
        for (double t = margin; t < dist - margin; t += STEP) {
            Location pos = new Location(world,
                    from.getX() + direction.getX() * t,
                    from.getY() + direction.getY() * t,
                    from.getZ() + direction.getZ() * t);
            Material type = pos.getBlock().getType();
            if (type.isAir()) continue;
            if (blocksLineOfSight(type)) return false;
        }
        return true;
    }

    /**
     * Exact raycast using block collision shapes (voxel shapes).
     * Tests segment-vs-bounding-box intersection for every collision box
     * of each candidate block along the path.
     */
    private boolean exactRayFree(World world, Location start, Vector direction, double length) {
        Set<Block> candidates = new HashSet<>();
        for (double t = 0.0; t <= length; t += STEP) {
            Location pos = start.clone().add(direction.clone().multiply(t));
            candidates.add(world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
        }
        Vector startVec = start.toVector();
        Vector end = startVec.clone().add(direction.clone().multiply(length));
        for (Block block : candidates) {
            Material type = block.getType();
            if (type.isAir()) continue;
            if (excluded.contains(type)) continue;
            if (!included.contains(type) && !type.isOccluding()) continue;
            for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                if (box.contains(startVec) || box.contains(end)) return false;
                if (segmentIntersectsBox(startVec, end, box)) return false;
            }
        }
        return true;
    }

    /** Segment-AABB intersection (slab method). */
    private static boolean segmentIntersectsBox(Vector start, Vector end, BoundingBox box) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();
        double tMin = 0.0, tMax = 1.0;

        double[] d = {dx, dy, dz};
        double[] s = {start.getX(), start.getY(), start.getZ()};
        double[] lo = {box.getMinX(), box.getMinY(), box.getMinZ()};
        double[] hi = {box.getMaxX(), box.getMaxY(), box.getMaxZ()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (s[i] < lo[i] || s[i] > hi[i]) return false;
            } else {
                double t1 = (lo[i] - s[i]) / d[i];
                double t2 = (hi[i] - s[i]) / d[i];
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return false;
            }
        }
        return true;
    }

    // For the coarse (non-exact) mode
    boolean blocksLineOfSight(Material type) {
        if (excluded.contains(type)) return false;
        if (included.contains(type)) return true;
        return type.isOccluding();
    }

    // ------------------------------------------------------------------
    // Toggle
    // ------------------------------------------------------------------

    boolean toggle(UUID playerId) {
        boolean enabled;
        if (toggledOff.remove(playerId)) {
            enabled = true;
        } else {
            toggledOff.add(playerId);
            enabled = false;
        }
        getConfig().set("toggled-off-players", toggledOff.stream().map(UUID::toString).collect(Collectors.toList()));
        saveConfig();
        return enabled;
    }

    // ------------------------------------------------------------------
    // Listener: each player gets their own scoreboard
    // ------------------------------------------------------------------

    private final class TagListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            event.getPlayer().setScoreboard(viewerBoard(event.getPlayer()));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Player leaver = event.getPlayer();
            leaver.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(leaver)) continue;
                Team team = other.getScoreboard().getTeam(TEAM_NAME);
                if (team != null) team.removeEntry(leaver.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    Component msg(String mini) {
        return mm.deserialize(mini);
    }

    Set<Material> excluded() { return excluded; }
    Set<Material> included() { return included; }

    static final class VisTagCommand {

        static LiteralCommandNode<CommandSourceStack> build(VisTagPlugin plugin) {
            return Commands.literal("vistag")
                    .then(listCommand(plugin, "exclude", plugin.excluded()))
                    .then(listCommand(plugin, "include", plugin.included()))
                    .then(Commands.literal("toggle")
                            .requires(src -> src.getSender().hasPermission("vistag.admin"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .executes(ctx -> {
                                        var sender = ctx.getSource().getSender();
                                        PlayerSelectorArgumentResolver resolver =
                                                ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                        var targets = resolver.resolve(ctx.getSource());
                                        if (targets.isEmpty()) {
                                            sender.sendMessage(plugin.msg("<red>[VisTag] Player not found.</red>"));
                                            return 0;
                                        }
                                        for (var target : targets) {
                                            boolean enabled = plugin.toggle(target.getUniqueId());
                                            String state = enabled ? "enabled" : "disabled";
                                            sender.sendMessage(plugin.msg("<green>[VisTag] Nametag filtering is now " + state + " for " + target.getName() + ".</green>"));
                                            target.sendMessage(plugin.msg(enabled
                                                    ? "<green>[VisTag] Nametag filtering enabled for you.</green>"
                                                    : "<yellow>[VisTag] Nametag filtering disabled for you — you see all nametags.</yellow>"));
                                        }
                                        return targets.size();
                                    })))
                    .then(Commands.literal("reload")
                            .requires(src -> src.getSender().hasPermission("vistag.admin"))
                            .executes(ctx -> {
                                plugin.reload();
                                ctx.getSource().getSender().sendMessage(plugin.msg("<green>[VisTag] Configuration reloaded.</green>"));
                                return 1;
                            }))
                    .build();
        }

        private static LiteralArgumentBuilder<CommandSourceStack> listCommand(
                VisTagPlugin plugin, String listName, Set<Material> set) {
            return Commands.literal(listName)
                    .requires(src -> src.getSender().hasPermission("vistag.admin"))
                    .then(Commands.literal("add")
                            .then(Commands.argument("block", StringArgumentType.word())
                                    .executes(ctx -> modify(plugin, listName, set, ctx, true))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("block", StringArgumentType.word())
                                    .executes(ctx -> modify(plugin, listName, set, ctx, false))))
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                var sender = ctx.getSource().getSender();
                                if (set.isEmpty()) {
                                    sender.sendMessage(plugin.msg("<gray>[VisTag] List '" + listName + "' is empty.</gray>"));
                                } else {
                                    String items = set.stream().sorted().map(Enum::name)
                                            .collect(Collectors.joining(", "));
                                    sender.sendMessage(plugin.msg("<yellow>[VisTag] " + listName + ":</yellow> <white>" + items + "</white>"));
                                }
                                return 1;
                            }));
        }

        private static int modify(VisTagPlugin plugin, String listName, Set<Material> set,
                                  CommandContext<CommandSourceStack> ctx, boolean add) {
            var sender = ctx.getSource().getSender();
            String raw = StringArgumentType.getString(ctx, "block");
            Material mat = Material.matchMaterial(raw);
            if (mat == null) {
                sender.sendMessage(plugin.msg("<red>[VisTag] Unknown block: " + raw + "</red>"));
                return 0;
            }
            boolean changed = add ? set.add(mat) : set.remove(mat);
            if (changed) {
                plugin.saveLists();
                sender.sendMessage(plugin.msg("<green>[VisTag] " + mat.name() + (add ? " added to" : " removed from")
                        + " list '" + listName + "'.</green>"));
            } else {
                sender.sendMessage(plugin.msg("<yellow>[VisTag] No change: " + mat.name()
                        + (add ? " is already in" : " is not in") + " list '" + listName + "'.</yellow>"));
            }
            return changed ? 1 : 0;
        }

        private VisTagCommand() {}
    }
}
