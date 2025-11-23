package com.projectkorra.projectkorra.board;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.storage.DBConnection;
import com.projectkorra.projectkorra.util.ChatUtil;
import com.projectkorra.projectkorra.util.ThreadUtil;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public final class BendingBoardManager {

    private BendingBoardManager() {}

    private static final Set<String> disabledWorlds = new HashSet<>();
    private static final Map<String, ChatColor> trackedCooldowns = new ConcurrentHashMap<>();
    private static final Set<UUID> disabledPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final Map<Player, BendingBoard> scoreboardPlayers = new ConcurrentHashMap<>();

    public static Function<BendingPlayer, BendingBoard> boardSupplier = BendingBoard::new;

    private static boolean enabled;

    public static void setup() {
        loadDisabledPlayers();
        initialize();
        Bukkit.getOnlinePlayers().forEach(BendingBoardManager::getBoard);
    }

    public static void reload() {
        scoreboardPlayers.values().forEach(BendingBoard::destroy);
        scoreboardPlayers.clear();
        initialize();
    }

    private static void initialize() {
        enabled = ConfigManager.getConfig().getBoolean("Properties.BendingBoard");

        disabledWorlds.clear();
        disabledWorlds.addAll(ConfigManager.getConfig().getStringList("Properties.DisabledWorlds"));

        if (ConfigManager.languageConfig.get().contains("Board.Extras")) {
            ConfigurationSection section = ConfigManager.languageConfig.get().getConfigurationSection("Board.Extras");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        trackedCooldowns.put(key, ChatColor.of(section.getString(key)));
                    } catch (Exception e) {
                        ProjectKorra.plugin.getLogger().warning("Couldn't parse color from 'Board.Extras." + key + "', using white.");
                        trackedCooldowns.put(key, ChatColor.WHITE);
                    }
                }
            }
        }
    }

    public static boolean isDisabled(Player player) {
        return disabledPlayers.contains(player.getUniqueId());
    }

    public static void changeWorld(Player player) {
        getBoard(player).ifPresent((b) -> b.setVisible(!disabledWorlds.contains(player.getWorld().getName())));
    }

    public static void toggleBoard(Player player, boolean force) {
        if (!force && (!enabled || disabledWorlds.contains(player.getWorld().getName()))) {
            ChatUtil.sendBrandingMessage(player, ChatColor.RED + ConfigManager.languageConfig.get().getString("Commands.Board.Disabled"));
            return;
        }

        if (scoreboardPlayers.containsKey(player)) {
            scoreboardPlayers.get(player).hide();
            disabledPlayers.add(player.getUniqueId());
            scoreboardPlayers.remove(player);
            ChatUtil.sendBrandingMessage(player, ChatColor.RED + ConfigManager.languageConfig.get().getString("Commands.Board.ToggledOff"));
        } else {
            disabledPlayers.remove(player.getUniqueId());
            getBoard(player).ifPresent(BendingBoard::show);
            ChatUtil.sendBrandingMessage(player, ChatColor.GREEN + ConfigManager.languageConfig.get().getString("Commands.Board.ToggledOn"));
        }
    }

    public static Optional<BendingBoard> getBoard(Player player) {
        if (!enabled || disabledPlayers.contains(player.getUniqueId()) || !player.hasPermission("bending.command.board")) {
            return Optional.empty();
        }

        if (!player.isOnline()) {
            return Optional.empty();
        }

        if (!scoreboardPlayers.containsKey(player)) {
            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null) {
                return Optional.empty();
            }

            try {
                scoreboardPlayers.put(player, boardSupplier.apply(bPlayer));
            } catch (Exception e) {
                ProjectKorra.plugin.getLogger().warning("Failed to create BendingBoard for " + player.getName());
                e.printStackTrace();
                return Optional.empty();
            }
        }

        return Optional.of(scoreboardPlayers.get(player));
    }

    public static void updateAllSlots(Player player) {
        getBoard(player).ifPresent(BendingBoard::updateAll);
    }

    public static void updateBoard(Player player, String name, boolean forceCooldown, int slot) {
        getBoard(player).ifPresent((board) -> {
            if (MultiAbilityManager.hasMultiAbilityBound(player)) {
                board.updateAll();
            }

            if (name == null || name.isEmpty()) {
                board.clearSlot(slot);
                return;
            }

            CoreAbility coreAbility = CoreAbility.getAbility(name);
            if (coreAbility instanceof ComboAbility) {
                board.updateMisc(name, coreAbility.getElement().getColor(), forceCooldown);
            } else if (coreAbility == null && trackedCooldowns.containsKey(name)) {
                board.updateMisc(name, trackedCooldowns.get(name), forceCooldown);
            } else if (coreAbility != null && slot > 0) {
                board.setSlot(slot, name, forceCooldown);
            } else {
                board.setAbilityCooldown(name, forceCooldown);
            }
        });
    }

    public static void changeActiveSlot(Player player, int newSlot) {
        getBoard(player).ifPresent((board) -> board.setActiveSlot(newSlot));
    }

    public static void addCooldownToTrack(String cooldownName, ChatColor color) {
        trackedCooldowns.put(cooldownName, color);
    }

    public static void loadDisabledPlayers() {
        ThreadUtil.runAsync(() -> {
            Set<UUID> disabled = new HashSet<>();
            try {
                final ResultSet rs = DBConnection.sql.readQuery("SELECT uuid FROM pk_board WHERE enabled = 0");
                while (rs != null && rs.next()) {
                    try {
                        disabled.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException e) {
                        ProjectKorra.plugin.getLogger().warning("Invalid UUID in pk_board table");
                    }
                }
            } catch (SQLException e) {
                ProjectKorra.plugin.getLogger().warning("Failed to load disabled players from database");
                e.printStackTrace();
            }
            disabledPlayers.clear();
            disabledPlayers.addAll(disabled);
        });
    }

    public static void clean(final Player player) {
        BendingBoard board = scoreboardPlayers.remove(player);
        if (board != null) {
            board.destroy();
        }

        final UUID uuid = player.getUniqueId();
        final boolean enabled = !disabledPlayers.contains(uuid);

        ThreadUtil.runAsync(() -> {
            try {
                PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement("SELECT enabled FROM pk_board WHERE uuid = ? LIMIT 1");
                ps.setString(1, uuid.toString());

                boolean exists = false;
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }

                PreparedStatement ps2;
                if (!exists) {
                    ps2 = DBConnection.sql.getConnection().prepareStatement("INSERT INTO pk_board (uuid, enabled) VALUES (?, ?)");
                } else {
                    ps2 = DBConnection.sql.getConnection().prepareStatement("UPDATE pk_board SET enabled = ? WHERE uuid = ?");
                }

                ps2.setInt(1, enabled ? 1 : 0);
                ps2.setString(2, uuid.toString());
                ps2.execute();
                ps2.close();
            } catch (SQLException e) {
                ProjectKorra.plugin.getLogger().warning("Failed to save board preference for " + player.getName());
                e.printStackTrace();
            }
        });
    }
}
