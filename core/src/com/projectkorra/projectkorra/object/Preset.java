package com.projectkorra.projectkorra.object;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.util.ThreadUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.board.BendingBoardManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.storage.DBConnection;

/**
 * A savable association of abilities and hotbar slots, stored per player.
 * Compatible with Spigot, Paper, Folia and Java 8-25.
 *
 * @author kingbirdy
 */
public class Preset {

    public static Map<UUID, List<Preset>> presets = new ConcurrentHashMap<>();
    public static FileConfiguration config = ConfigManager.presetConfig.get();
    public static HashMap<String, ArrayList<String>> externalPresets = new HashMap<>();

    static String loadQuery = "SELECT * FROM pk_presets WHERE uuid = ?";
    static String deleteQuery = "DELETE FROM pk_presets WHERE uuid = ? AND name = ?";
    static String insertQuery = "INSERT INTO pk_presets (uuid, name, slot1, slot2, slot3, slot4, slot5, slot6, slot7, " +
            "slot8, slot9) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final UUID uuid;
    private final HashMap<Integer, String> abilities;
    private final String name;

    public Preset(final UUID uuid, final String name, final HashMap<Integer, String> abilities) {
        this.uuid = uuid;
        this.name = name;
        this.abilities = abilities != null ? new HashMap<>(abilities) : new HashMap<>();

        presets.computeIfAbsent(uuid, k -> new ArrayList<>()).add(this);
    }

    public static void unloadPreset(final Player player) {
        if (player != null) {
            presets.remove(player.getUniqueId());
        }
    }

    public static void loadPresets(final Player player) {
        if (player == null) return;

        ThreadUtil.runAsync(() -> {
            final UUID uuid = player.getUniqueId();
            if (uuid == null) return;

            try (PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement(loadQuery)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    int i = 0;
                    while (rs.next()) {
                        final HashMap<Integer, String> moves = new HashMap<>();
                        for (int total = 1; total <= 9; total++) {
                            final String slot = rs.getString("slot" + total);
                            if (slot != null && !slot.isEmpty()) {
                                moves.put(total, slot);
                            }
                        }
                        new Preset(uuid, rs.getString("name"), moves);
                        i++;
                    }
                    if (i > 0) {
                        ProjectKorra.log.info("Loaded " + i + " presets for " + player.getName());
                    }
                }
            } catch (SQLException ex) {
                ProjectKorra.plugin.getLogger().warning("Failed to load presets for " + player.getName());
                ex.printStackTrace();
            }
        });
    }

    public static void reloadPreset(final Player player) {
        unloadPreset(player);
        loadPresets(player);
    }

    public static boolean bindPreset(final Player player, final Preset preset) {
        if (player == null || preset == null) return false;

        final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return false;

        final HashMap<Integer, String> abilities = new HashMap<>(preset.abilities);
        boolean boundAll = true;

        for (int i = 1; i <= 9; i++) {
            String abilityName = abilities.get(i);
            if (abilityName != null) {
                final CoreAbility coreAbil = CoreAbility.getAbility(abilityName);
                if (coreAbil == null || !bPlayer.canBind(coreAbil)) {
                    abilities.remove(i);
                    boundAll = false;
                }
            }
        }

        bPlayer.setAbilities(abilities);
        BendingBoardManager.updateAllSlots(player);
        return boundAll;
    }

    public static boolean presetExists(final Player player, final String name) {
        if (player == null || name == null) return false;
        UUID uuid = player.getUniqueId();
        List<Preset> list = presets.get(uuid);
        if (list == null) return false;

        for (final Preset preset : list) {
            if (preset != null && preset.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static Preset getPreset(final Player player, final String name) {
        if (player == null || name == null) return null;
        UUID uuid = player.getUniqueId();
        List<Preset> list = presets.get(uuid);
        if (list == null) return null;

        for (final Preset preset : list) {
            if (preset != null && preset.name.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    public static void loadExternalPresets() {
        final HashMap<String, ArrayList<String>> presetsMap = new HashMap<>();

        if (config == null) return;

        for (final String name : config.getKeys(false)) {
            List<String> presetList = config.getStringList(name);
            if (presetList != null && !presetList.isEmpty() && presetList.size() <= 9) {
                presetsMap.put(name.toLowerCase(), new ArrayList<>(presetList));
            }
        }

        externalPresets = presetsMap;
    }

    public static boolean externalPresetExists(final String name) {
        if (name == null) return false;
        for (final String preset : externalPresets.keySet()) {
            if (preset != null && name.equalsIgnoreCase(preset)) {
                return true;
            }
        }
        return false;
    }

    public static HashMap<Integer, String> getPresetContents(final Player player, final String name) {
        if (player == null || name == null) return null;
        UUID uuid = player.getUniqueId();
        List<Preset> list = presets.get(uuid);
        if (list == null) return null;

        for (final Preset preset : list) {
            if (preset != null && preset.name.equalsIgnoreCase(name)) {
                return new HashMap<>(preset.abilities);
            }
        }
        return null;
    }

    public static boolean bindExternalPreset(final Player player, final String name) {
        if (player == null || name == null) return false;

        boolean boundAll = true;
        int slot = 0;
        final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return false;

        final HashMap<Integer, String> abilities = new HashMap<>();

        if (externalPresetExists(name.toLowerCase())) {
            ArrayList<String> presetAbilities = externalPresets.get(name.toLowerCase());
            if (presetAbilities != null) {
                for (final String ability : presetAbilities) {
                    slot++;
                    if (ability != null && !ability.isEmpty()) {
                        final CoreAbility coreAbil = CoreAbility.getAbility(ability);
                        if (coreAbil != null) {
                            abilities.put(slot, coreAbil.getName());
                        }
                    }
                }

                for (int i = 1; i <= 9; i++) {
                    String abilityName = abilities.get(i);
                    if (abilityName != null) {
                        final CoreAbility coreAbil = CoreAbility.getAbility(abilityName);
                        if (coreAbil != null && !bPlayer.canBind(coreAbil)) {
                            abilities.remove(i);
                            boundAll = false;
                        }
                    }
                }

                bPlayer.setAbilities(abilities);
                BendingBoardManager.updateAllSlots(player);
                return boundAll;
            }
        }
        return false;
    }

    public CompletableFuture<Boolean> delete() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ThreadUtil.runAsync(() -> {
            PreparedStatement ps = null;
            try {
                ps = DBConnection.sql.getConnection().prepareStatement(deleteQuery);
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.execute();

                List<Preset> list = presets.get(uuid);
                if (list != null) {
                    list.remove(this);
                }

                future.complete(true);
            } catch (final SQLException e) {
                ProjectKorra.plugin.getLogger().warning("Failed to delete preset: " + name);
                e.printStackTrace();
                future.complete(false);
            } finally {
                try {
                    if (ps != null) ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        return future;
    }

    public CompletableFuture<Boolean> save(final Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (player == null) {
            future.complete(false);
            return future;
        }

        ThreadUtil.runAsync(() -> {
            PreparedStatement ps = null;
            try {
                ps = DBConnection.sql.getConnection().prepareStatement(insertQuery);
                ps.setString(1, uuid.toString());
                ps.setString(2, name);

                for (int i = 1; i <= 9; i++) {
                    String ability = abilities.get(i);
                    ps.setString(2 + i, ability != null ? ability : "");
                }

                ps.execute();
                future.complete(true);
            } catch (final SQLException e) {
                ProjectKorra.plugin.getLogger().warning("Failed to save preset: " + name);
                e.printStackTrace();
                future.complete(false);
            } finally {
                try {
                    if (ps != null) ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        return future;
    }

    public String getName() {
        return this.name;
    }

    public HashMap<Integer, String> getAbilities() {
        return new HashMap<>(this.abilities);
    }

    public UUID getUUID() {
        return this.uuid;
    }
}
