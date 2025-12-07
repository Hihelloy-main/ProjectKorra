package com.projectkorra.projectkorra.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.projectkorra.projectkorra.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.object.Preset;

/**
 * Executor for /bending preset. Extends {@link PKCommand}.
 */
public class PresetCommand extends PKCommand {

    private static final String[] createaliases = { "create", "c", "save" };
    private static final String[] deletealiases = { "delete", "d", "del" };
    private static final String[] listaliases = { "list", "l" };
    private static final String[] bindaliases = { "bind", "b" };

    public static final String INVALID_NAME = ".*[.,;:*'\"?=`<>+\\-\\[\\]{}^@!#$/\\\\%&()].*";

    private final String noPresets;
    private final String noPresetName;
    private final String deletePreset;
    private final String noPresetNameExternal;
    private final String bendingRemoved;
    private final String bound;
    private final String failedToBindAll;
    private final String databaseError;
    private final String bendingRemovedOther;
    private final String boundOtherConfirm;
    private final String succesfullyCopied;
    private final String reachedMax;
    private final String alreadyExists;
    private final String createdNewPreset;
    private final String cantEditBinds;
    private final String playerNotFound;
    private final String invalidName;

    public PresetCommand() {
        super("preset", "/bending preset <Bind/Create/Delete/List> [Preset]",
                ConfigManager.languageConfig.get().getString("Commands.Preset.Description"),
                new String[] { "preset", "presets", "pre", "set", "p" });

        this.noPresets = ConfigManager.languageConfig.get().getString("Commands.Preset.NoPresets");
        this.noPresetName = ConfigManager.languageConfig.get().getString("Commands.Preset.NoPresetName");
        this.deletePreset = ConfigManager.languageConfig.get().getString("Commands.Preset.Delete");
        this.noPresetNameExternal = ConfigManager.languageConfig.get().getString("Commands.Preset.External.NoPresetName");
        this.bendingRemoved = ConfigManager.languageConfig.get().getString("Commands.Preset.BendingPermanentlyRemoved");
        this.bound = ConfigManager.languageConfig.get().getString("Commands.Preset.SuccesfullyBound");
        this.failedToBindAll = ConfigManager.languageConfig.get().getString("Commands.Preset.FailedToBindAll");
        this.databaseError = ConfigManager.languageConfig.get().getString("Commands.Preset.DatabaseError");
        this.bendingRemovedOther = ConfigManager.languageConfig.get().getString("Commands.Preset.Other.BendingPermanentlyRemoved");
        this.boundOtherConfirm = ConfigManager.languageConfig.get().getString("Commands.Preset.Other.SuccesfullyBoundConfirm");
        this.succesfullyCopied = ConfigManager.languageConfig.get().getString("Commands.Preset.SuccesfullyCopied");
        this.reachedMax = ConfigManager.languageConfig.get().getString("Commands.Preset.MaxPresets");
        this.alreadyExists = ConfigManager.languageConfig.get().getString("Commands.Preset.AlreadyExists");
        this.createdNewPreset = ConfigManager.languageConfig.get().getString("Commands.Preset.Created");
        this.cantEditBinds = ConfigManager.languageConfig.get().getString("Commands.Preset.CantEditBinds");
        this.playerNotFound = ConfigManager.languageConfig.get().getString("Commands.Preset.PlayerNotFound");
        this.invalidName = ConfigManager.languageConfig.get().getString("Commands.Preset.InvalidName");
    }

    @Override
    public void execute(final CommandSender sender, final List<String> args) {
        if (!this.correctLength(sender, args.size(), 1, 3)) return;

        if (sender instanceof Player && MultiAbilityManager.hasMultiAbilityBound((Player) sender)) {
            ChatUtil.sendBrandingMessage(sender, this.cantEditBinds);
            return;
        }

        Player target = null;
        int page = 1;
        String name = null;

        if (args.size() == 1 && !Arrays.asList(listaliases).contains(args.get(0))) {
            this.help(sender, false);
        } else if (args.size() >= 2) {
            if (Arrays.asList(listaliases).contains(args.get(0))) {
                if (args.size() == 3) {
                    target = Bukkit.getPlayer(args.get(1));
                    if (target == null) {
                        ChatUtil.sendBrandingMessage(sender, this.playerNotFound);
                        return;
                    }
                    page = parseInt(args.get(2));
                } else {
                    page = parseInt(args.get(1));
                }
            } else {
                name = args.get(1);
            }
        }

        // List presets
        if (Arrays.asList(listaliases).contains(args.get(0)) && this.hasPermission(sender, "list")) {
            if (target == null) {
                if (!isPlayer(sender)) return;
                target = (Player) sender;
            }

            final List<Preset> presets = Preset.presets.get(target.getUniqueId());
            if (presets == null || presets.isEmpty()) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.noPresets);
                return;
            }

            final List<String> presetNames = new ArrayList<>();
            presets.forEach(p -> presetNames.add(p.getName()));

            boolean firstMessage = true;
            for (String s : this.getPage(presetNames, ChatColor.GOLD + "Presets: ", page, false)) {
                if (firstMessage) {
                    ChatUtil.sendBrandingMessage(sender, s);
                    firstMessage = false;
                } else {
                    sender.sendMessage(ChatColor.YELLOW + s);
                }
            }

        } else if (Arrays.asList(deletealiases).contains(args.get(0)) && this.hasPermission(sender, "delete")) {
            if (args.size() >= 3) {
                target = Bukkit.getPlayer(args.get(1));
                if (target == null) {
                    ChatUtil.sendBrandingMessage(sender, this.playerNotFound);
                    return;
                }
            } else {
                if (!isPlayer(sender)) return;
                target = (Player) sender;
            }

            if (!Preset.presetExists(target, name)) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.noPresetName);
                return;
            }

            final Preset preset = Preset.getPreset(target, name);
            preset.delete().thenAccept(success -> {
                if (success) {
                    ChatUtil.sendBrandingMessage(sender, ChatColor.GREEN + this.deletePreset.replace("{name}", ChatColor.YELLOW + preset.getName() + ChatColor.GREEN));
                } else {
                    ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.databaseError.replace("{name}", ChatColor.YELLOW + preset.getName() + ChatColor.RED));
                }
            }).exceptionally(e -> {
                e.printStackTrace();
                return null;
            });

        } else if (Arrays.asList(bindaliases).contains(args.get(0)) && this.hasPermission(sender, "bind")) {
            if (!isPlayer(sender)) return;
            Player player = (Player) sender;
            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

            if (name == null) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.invalidName);
            } else if (bPlayer.isPermaRemoved()) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.bendingRemoved);
            } else if (Preset.presetExists(player, name)) {
                Preset preset = Preset.getPreset(player, name);
                boolean boundAll = Preset.bindPreset(player, preset);
                ChatUtil.sendBrandingMessage(sender, ChatColor.GREEN + this.bound.replace("{name}", ChatColor.YELLOW + preset.getName() + ChatColor.GREEN));
                if (!boundAll) ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.failedToBindAll);
            } else if (Preset.externalPresetExists(name) && sender.hasPermission("bending.command.preset.bind.external")) {
                boolean boundAll = Preset.bindExternalPreset(player, name);
                if (!boundAll) ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.failedToBindAll);
            } else {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.noPresetNameExternal);
            }

        } else if (Arrays.asList(createaliases).contains(args.get(0)) && this.hasPermission(sender, "create")) {
            if (!isPlayer(sender)) return;
            target = (Player) sender;
            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(target);

            final int limit = GeneralMethods.getMaxPresets(target);
            if (name == null || name.matches(INVALID_NAME)) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.invalidName);
                return;
            }

            List<Preset> playerPresets = Preset.presets.get(target.getUniqueId());
            if (playerPresets != null && playerPresets.size() >= limit) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.reachedMax);
                return;
            }

            if (Preset.presetExists(target, name)) {
                ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.alreadyExists);
                return;
            }

            if (bPlayer == null) return;

            final HashMap<Integer, String> abilities = (HashMap<Integer, String>) bPlayer.getAbilities().clone();
            final Preset preset = new Preset(target.getUniqueId(), name, abilities);
            final String finalName = name;
            preset.save(target).thenAccept(success -> {
                if (success) {
                    ChatUtil.sendBrandingMessage(sender, ChatColor.GREEN + this.createdNewPreset.replace("{name}", ChatColor.YELLOW + finalName + ChatColor.GREEN));
                } else {
                    ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.databaseError.replace("{name}", ChatColor.YELLOW + finalName + ChatColor.RED));
                }
            });
        } else {
            this.help(sender, false);
        }
    }

    @Override
    protected List<String> getTabCompletion(final CommandSender sender, final List<String> args) {
        if (args.size() >= 3 || !sender.hasPermission("bending.command.preset") || !(sender instanceof Player)) return new ArrayList<>();

        final List<String> completions = new ArrayList<>();
        if (args.isEmpty()) {
            completions.addAll(Arrays.asList("create", "delete", "list", "bind"));
        } else if (args.size() == 1 && Arrays.asList(deletealiases).contains(args.get(0).toLowerCase())) {
            final List<Preset> presets = Preset.presets.get(((Player) sender).getUniqueId());
            if (presets != null) presets.forEach(p -> completions.add(p.getName()));

            if (sender.hasPermission("bending.command.preset.bind.external") &&
                    Arrays.asList(bindaliases).contains(args.get(0).toLowerCase())) {
                completions.addAll(Preset.externalPresets.keySet());
            }
        }
        return completions;
    }

    private int parseInt(String string) {
        try { return Integer.parseInt(string); } catch (NumberFormatException e) { return -1; }
    }
}
