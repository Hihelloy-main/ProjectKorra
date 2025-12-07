package com.projectkorra.projectkorra.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;

import fr.mrmicky.fastboard.FastBoard;
import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;

public class BendingBoard {

    protected static final char[] CHAT_CHARS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static class BoardSlot {

        public int slot;
        public String text;
        public Optional<BoardSlot> next = Optional.empty(), prev = Optional.empty();

        public BoardSlot(int slot) {
            this.slot = slot + 1;
            this.text = "";
        }

        public void update(String prefix, String name) {
            this.text = prefix + name;
        }

        public void clear() {
            this.text = "";
        }

        public void setNext(BoardSlot slot) {
            this.next = Optional.of(slot);
        }

        public void setPrev(BoardSlot slot) {
            this.prev = Optional.of(slot);
        }
    }

    protected final BoardSlot[] slots = new BoardSlot[9];
    protected final Map<String, BoardSlot> misc = new HashMap<>();
    protected final Queue<Integer> miscSlotIds = new LinkedList<>();
    protected BoardSlot miscTail = null;

    protected final Player player;
    protected final BendingPlayer bendingPlayer;

    protected FastBoard board;
    protected int selectedSlot;

    protected String prefix, emptySlot, miscSeparator;
    protected ChatColor selectedColor, altColor;

    public BendingBoard(final BendingPlayer bPlayer) {
        bendingPlayer = bPlayer;
        player = bPlayer.getPlayer();
        selectedSlot = player.getInventory().getHeldItemSlot() + 1;

        prefix = ChatColor.stripColor(ConfigManager.languageConfig.get().getString("Board.Prefix.Text"));
        emptySlot = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.EmptySlot"));
        miscSeparator = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.MiscSeparator"));

        for (int i = 0; i < 9; ++i) {
            slots[i] = new BoardSlot(i);
            miscSlotIds.add(i);
        }

        board = new FastBoard(player);

        updateColors();
        updateAll();
    }

    public void destroy() {
        if (board != null) {
            board.delete();
            board = null;
        }
    }

    private ChatColor getElementColor() {
        if (bendingPlayer.getElements().size() > 1) return Element.AVATAR.getColor();
        if (bendingPlayer.getElements().size() == 1) return bendingPlayer.getElements().get(0).getColor();
        return ChatColor.WHITE;
    }

    private ChatColor getColor(String from, ChatColor def) {
        if (from.equalsIgnoreCase("element")) return getElementColor();
        try {
            return ChatColor.of(from);
        } catch (Exception e) {
            ProjectKorra.plugin.getLogger().warning("Couldn't parse board color from '" + from + "', using default!");
            return def;
        }
    }

    public void hide() {
        if (board != null) {
            board.delete();
            board = null;
        }
    }

    public void show() {
        if (board == null) board = new FastBoard(player);
        updateAll();
    }

    public boolean isVisible() {
        return board != null;
    }

    public void setVisible(boolean show) {
        if (show) show();
        else hide();
    }

    public void setSlot(int slot, String ability, boolean cooldown) {
        if (slot < 1 || slot > 9 || board == null) return;

        StringBuilder sb = new StringBuilder();

        if (ability == null || ability.isEmpty()) {
            sb.append(emptySlot.replaceAll("\\{slot_number\\}", "" + slot));
        } else {
            CoreAbility coreAbility = CoreAbility.getAbility(ChatColor.stripColor(ability));
            if (coreAbility == null) { // MultiAbility
                if (cooldown || bendingPlayer.isOnCooldown(ability)) sb.append(ChatColor.STRIKETHROUGH);
                sb.append(ability);
            } else {
                sb.append(coreAbility.getMovePreviewWithoutCooldownTimer(player, cooldown));
            }
        }

        slots[slot - 1].update((slot == selectedSlot ? selectedColor : altColor) + prefix, sb.toString());
        refresh();
    }

    private int updateSelected(int newSlot) {
        int oldSlot = selectedSlot;
        selectedSlot = newSlot;
        return oldSlot;
    }

    public void updateColors() {
        selectedColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.SelectedColor"), ChatColor.WHITE);
        altColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.NonSelectedColor"), ChatColor.DARK_GRAY);
    }

    public void updateAll() {
        updateColors();
        selectedSlot = player.getInventory().getHeldItemSlot() + 1;
        for (int i = 1; i <= 9; i++) setSlot(i, bendingPlayer.getAbilities().get(i), false);
    }

    public void clearSlot(int slot) {
        setSlot(slot, null, false);
    }

    public void setActiveSlot(int newSlot) {
        int oldSlot = updateSelected(newSlot);
        setSlot(oldSlot, bendingPlayer.getAbilities().get(oldSlot), false);
        setSlot(newSlot, bendingPlayer.getAbilities().get(newSlot), false);
    }

    public void setAbilityCooldown(String name, boolean cooldown) {
        bendingPlayer.getAbilities().entrySet().stream()
                .filter(entry -> name.equals(entry.getValue()))
                .forEach(entry -> setSlot(entry.getKey(), name, cooldown));
    }

    public void updateMisc(String name, ChatColor color, boolean cooldown) {
        if (!cooldown) {
            misc.computeIfPresent(name, (key, slot) -> {
                slot.next.ifPresent(n -> n.prev = slot.prev);
                slot.prev.ifPresent(p -> p.next = slot.next);
                if (slot == miscTail) miscTail = null;
                slot.clear();
                miscSlotIds.add(slot.slot - 10);
                return null;
            });
        } else if (!misc.containsKey(name)) {
            BoardSlot slot = new BoardSlot(10 + miscSlotIds.poll());
            slot.update(String.join("", Collections.nCopies(ChatColor.stripColor(prefix).length() + 1, " ")), color + "" + ChatColor.STRIKETHROUGH + name);

            if (miscTail != null) {
                miscTail.setNext(slot);
                slot.setPrev(miscTail);
            }

            miscTail = slot;
            misc.put(name, slot);
        }
        refresh();
    }

    private void refresh() {
        if (board == null) return;

        List<String> lines = new ArrayList<>();
        for (BoardSlot s : slots) lines.add(s.text);
        if (!misc.isEmpty()) {
            lines.add(miscSeparator);
            for (BoardSlot s : misc.values()) lines.add(s.text);
        }

        String title = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.Title", "Bending"));
        board.updateTitle(title);
        board.updateLines(lines.toArray(new String[0]));
    }
}
