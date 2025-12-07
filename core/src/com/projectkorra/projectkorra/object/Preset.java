package com.projectkorra.projectkorra.object;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 *
 * @author kingbirdy
 *
 */
public class Preset {

    public static final Map<UUID, List<Preset>> presets = new ConcurrentHashMap<>();
    public static final FileConfiguration config = ConfigManager.presetConfig.get();
    public static HashMap<String, ArrayList<String>> externalPresets = new HashMap<>();
    private static final String loadQuery = "SELECT * FROM pk_presets WHERE uuid = ?";
    private static final String deleteQuery = "DELETE FROM pk_presets WHERE uuid = ? AND name = ?";
    private static final String insertQuery = "INSERT INTO pk_presets (uuid, name, slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final UUID uuid;
    private final HashMap<Integer, String> abilities;
    private final String name;

    public Preset(final UUID uuid, final String name, final HashMap<Integer, String> abilities) {
        this.uuid = uuid;
        this.name = name;
        this.abilities = abilities;
        presets.computeIfAbsent(uuid, k -> new ArrayList<>()).add(this);
    }

    public static void unloadPreset(final Player player) {
        presets.remove(player.getUniqueId());
    }

    public static void loadPresets(final Player player) {
        ThreadUtil.runAsync(() -> {
            final UUID uuid = player.getUniqueId();
            if (uuid == null) return;

            try (PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement(loadQuery)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int count = 0;
                        do {
                            final HashMap<Integer, String> moves = new HashMap<>();
                            for (int slot = 1; slot <= 9; slot++) {
                                final String ability = rs.getString("slot" + slot);
                                if (ability != null) moves.put(slot, ability);
                            }
                            new Preset(uuid, rs.getString("name"), moves);
                            count++;
                        } while (rs.next());
                        ProjectKorra.log.info("Loaded " + count + " presets for " + player.getName());
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void reloadPreset(final Player player) {
        unloadPreset(player);
        loadPresets(player);
    }

    public static boolean bindPreset(final Player player, final Preset preset) {
        final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return false;

        final HashMap<Integer, String> abilities = new HashMap<>(preset.abilities);
        boolean boundAll = true;

        for (int i = 1; i <= 9; i++) {
            final CoreAbility coreAbil = CoreAbility.getAbility(abilities.get(i));
            if (coreAbil != null && !bPlayer.canBind(coreAbil)) {
                abilities.remove(i);
                boundAll = false;
            }
        }

        bPlayer.setAbilities(abilities);
        BendingBoardManager.updateAllSlots(player);
        return boundAll;
    }

    public static boolean presetExists(final Player player, final String name) {
        final List<Preset> playerPresets = presets.get(player.getUniqueId());
        if (playerPresets == null) return false;

        for (final Preset preset : playerPresets) {
            if (preset.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static Preset getPreset(final Player player, final String name) {
        final List<Preset> playerPresets = presets.get(player.getUniqueId());
        if (playerPresets == null) return null;

        for (final Preset preset : playerPresets) {
            if (preset.name.equalsIgnoreCase(name)) return preset;
        }
        return null;
    }

    public static void loadExternalPresets() {
        final HashMap<String, ArrayList<String>> presetsMap = new HashMap<>();
        for (final String name : config.getKeys(false)) {
            if (!presetsMap.containsKey(name)) {
                final List<String> abilitiesList = config.getStringList(name);
                if (!abilitiesList.isEmpty() && abilitiesList.size() <= 9) {
                    presetsMap.put(name.toLowerCase(), new ArrayList<>(abilitiesList));
                }
            }
        }
        externalPresets = presetsMap;
    }

    public static boolean externalPresetExists(final String name) {
        return externalPresets.keySet().stream().anyMatch(p -> p.equalsIgnoreCase(name));
    }

    public static HashMap<Integer, String> getPresetContents(final Player player, final String name) {
        final List<Preset> playerPresets = presets.get(player.getUniqueId());
        if (playerPresets == null) return null;

        for (final Preset preset : playerPresets) {
            if (preset.name.equalsIgnoreCase(name)) return preset.abilities;
        }
        return null;
    }

    public static boolean bindExternalPreset(final Player player, final String name) {
        final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return false;

        if (!externalPresetExists(name.toLowerCase())) return false;

        final HashMap<Integer, String> abilities = new HashMap<>();
        int slot = 0;
        for (final String ability : externalPresets.get(name.toLowerCase())) {
            slot++;
            final CoreAbility coreAbil = CoreAbility.getAbility(ability);
            if (coreAbil != null) abilities.put(slot, coreAbil.getName());
        }

        boolean boundAll = true;
        for (int i = 1; i <= 9; i++) {
            final CoreAbility coreAbil = CoreAbility.getAbility(abilities.get(i));
            if (coreAbil != null && !bPlayer.canBind(coreAbil)) {
                abilities.remove(i);
                boundAll = false;
            }
        }

        bPlayer.setAbilities(abilities);
        BendingBoardManager.updateAllSlots(player);
        return boundAll;
    }

    public CompletableFuture<Boolean> delete() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ThreadUtil.runAsync(() -> {
            try (PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement(deleteQuery)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.execute();
                List<Preset> playerPresets = presets.get(uuid);
                if (playerPresets != null) playerPresets.remove(this);
                future.complete(true);
            } catch (SQLException e) {
                e.printStackTrace();
                future.complete(false);
            }
        });
        return future;
    }

    public String getName() {
        return this.name;
    }

    public CompletableFuture<Boolean> save(final Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ThreadUtil.runAsync(() -> {
            try (PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement(insertQuery)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                for (int i = 1; i <= 9; i++) {
                    ps.setString(2 + i, abilities.get(i));
                }
                ps.execute();
                future.complete(true);
            } catch (SQLException e) {
                e.printStackTrace();
                future.complete(false);
            }
        });
        return future;
    }

    public HashMap<Integer, String> getAbilities() {
        return abilities;
    }

    public UUID getUUID() {
        return uuid;
    }
}
