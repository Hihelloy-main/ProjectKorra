package com.projectkorra.projectkorra.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;

import fr.mrmicky.fastboard.FastBoard;
import net.md_5.bungee.api.ChatColor;

/**
 * The main BendingBoard class, now working on Folia with the FastBoard library
 */
public class BendingBoard {

    private final Player player;
    private final BendingPlayer bendingPlayer;
    private final FastBoard board;

    private int selectedSlot;
    private String prefix;
    private String emptySlot;
    private String miscSeparator;
    private ChatColor selectedColor;
    private ChatColor altColor;

    private final Map<String, CooldownEntry> miscCooldowns = new LinkedHashMap<>();
    private BukkitTask updateTask;

    public BendingBoard(final BendingPlayer bPlayer) {
        this.bendingPlayer = bPlayer;
        this.player = bPlayer.getPlayer();

        if (this.player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        this.selectedSlot = this.player.getInventory().getHeldItemSlot() + 1;

        String title = ChatColor.translateAlternateColorCodes('&',
                ConfigManager.languageConfig.get().getString("Board.Title"));
        this.board = new FastBoard(this.player);
        this.board.updateTitle(title);

        this.prefix = ChatColor.stripColor(ConfigManager.languageConfig.get().getString("Board.Prefix.Text"));
        this.emptySlot = ChatColor.translateAlternateColorCodes('&',
                ConfigManager.languageConfig.get().getString("Board.EmptySlot"));
        this.miscSeparator = ChatColor.translateAlternateColorCodes('&',
                ConfigManager.languageConfig.get().getString("Board.MiscSeparator"));

        updateAll();
    }

    public void destroy() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }

        if (this.board != null && !this.board.isDeleted()) {
            this.board.delete();
        }

        this.miscCooldowns.clear();
    }

    private ChatColor getElementColor() {
        if (this.bendingPlayer.getElements().size() > 1) {
            return Element.AVATAR.getColor();
        } else if (this.bendingPlayer.getElements().size() == 1) {
            return this.bendingPlayer.getElements().get(0).getColor();
        } else {
            return ChatColor.WHITE;
        }
    }

    private ChatColor getColor(String from, ChatColor def) {
        if (from == null || from.isEmpty()) {
            return def;
        }

        if (from.equalsIgnoreCase("element")) {
            return getElementColor();
        }

        try {
            return ChatColor.of(from);
        } catch (Exception e) {
            ProjectKorra.plugin.getLogger().warning("Couldn't parse board color from '" + from + "', using default!");
            return def;
        }
    }

    public void hide() {
        if (this.board != null && !this.board.isDeleted()) {
            this.board.updateLines(new ArrayList<String>());
        }
    }

    public void show() {
        updateAll();
    }

    public boolean isVisible() {
        return this.board != null && !this.board.isDeleted();
    }

    public void setVisible(boolean show) {
        if (show) {
            show();
        } else {
            hide();
        }
    }

    public void setSlot(int slot, String ability, boolean cooldown) {
        if (slot < 1 || slot > 9 || this.board == null || this.board.isDeleted()) {
            return;
        }

        updateBoard();
    }

    public void clearSlot(int slot) {
        setSlot(slot, null, false);
    }

    public void setActiveSlot(int newSlot) {
        if (newSlot < 1 || newSlot > 9) {
            return;
        }

        this.selectedSlot = newSlot;
        updateBoard();
    }

    public void setAbilityCooldown(String name, boolean cooldown) {
        if (name == null || name.isEmpty()) {
            return;
        }

        updateBoard();
    }

    public void updateMisc(String name, ChatColor color, boolean cooldown) {
        if (name == null || name.isEmpty()) {
            return;
        }

        if (cooldown) {
            this.miscCooldowns.put(name, new CooldownEntry(color, name));
        } else {
            this.miscCooldowns.remove(name);
        }

        updateBoard();
    }

    public void updateColors() {
        this.selectedColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.SelectedColor"), ChatColor.WHITE);
        this.altColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.NonSelectedColor"), ChatColor.DARK_GRAY);
    }

    public void updateAll() {
        updateColors();
        this.selectedSlot = this.player.getInventory().getHeldItemSlot() + 1;
        this.miscCooldowns.clear();
        updateBoard();
    }

    private void updateBoard() {
        if (this.board == null || this.board.isDeleted()) {
            return;
        }

        List<String> lines = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            String ability = this.bendingPlayer.getAbilities().get(i);
            ChatColor slotColor = (i == this.selectedSlot) ? this.selectedColor : this.altColor;

            if (ability == null || ability.isEmpty()) {
                lines.add(slotColor + this.prefix + this.emptySlot.replaceAll("\\{slot_number\\}", String.valueOf(i)));
            } else {
                CoreAbility coreAbility = CoreAbility.getAbility(ChatColor.stripColor(ability));

                if (coreAbility == null) {
                    boolean isOnCooldown = this.bendingPlayer.isOnCooldown(ability);
                    String display = isOnCooldown ? ChatColor.STRIKETHROUGH + ability : ability;
                    lines.add(slotColor + this.prefix + display);
                } else {
                    String preview = coreAbility.getMovePreviewWithoutCooldownTimer(this.player, false);
                    lines.add(slotColor + this.prefix + preview);
                }
            }
        }

        if (!this.miscCooldowns.isEmpty()) {
            lines.add(this.miscSeparator);

            for (CooldownEntry entry : this.miscCooldowns.values()) {
                lines.add(entry.color + "" + ChatColor.STRIKETHROUGH + entry.name);
            }
        }

        this.board.updateLines(lines);
    }

    private static class CooldownEntry {
        final ChatColor color;
        final String name;

        CooldownEntry(ChatColor color, String name) {
            this.color = color;
            this.name = name;
        }
    }
}
