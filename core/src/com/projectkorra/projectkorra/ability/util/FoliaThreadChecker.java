package com.projectkorra.projectkorra.ability.util;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.StanceAbility;
import com.projectkorra.projectkorra.event.PlayerChangeRegionEvent;
import com.projectkorra.projectkorra.util.PaperLib;
import com.projectkorra.projectkorra.util.ThreadUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * This is a runnable that fixes and restarts passives when a player
 * changes regions in Folia.
 */
public class FoliaThreadChecker implements Runnable {

    private Player player;
    private Location oldLocation;
    private StanceAbility stanceAbility;
    private Location newLocation;
    private boolean checker;

    public FoliaThreadChecker(Player player) {
        this.player = player;
    }

    @Override
    public void run() {

        if (!player.isOnline()) {
            ProjectKorra.log.info("Player is no longer online! Player: " + player.getName());
            return;
        }
        if (this.oldLocation != null && Bukkit.isOwnedByCurrentRegion(oldLocation)) {
            stanceAbility = BendingPlayer.getBendingPlayer(player).getStance();
        }

        if (this.oldLocation != null && !Bukkit.isOwnedByCurrentRegion(oldLocation) && stanceAbility != null) {
            ProjectKorra.log.info(player.getName() + " changed regions. Restarting passives.");
            onChangeRegion(stanceAbility);
            checker = true;
        } else if (stanceAbility == null && checker) {
            checker = false;
            ProjectKorra.log.info("stanceAbility = null");
        }

        this.oldLocation = player.getLocation();
    }

    public void onChangeRegion(StanceAbility stance) {
        PassiveManager.registerPassives(this.player);
        BendingPlayer.getBendingPlayer(player).setStance(stance);
        newLocation = player.getLocation();
        PlayerChangeRegionEvent event = new PlayerChangeRegionEvent(oldLocation, newLocation, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            PaperLib.teleportAsync(player, oldLocation);
            Chunk chunk = event.getNewLocation().getChunk();
            ThreadUtil.runSyncTimer(() -> {
                if (chunk.isLoaded()) {
                    chunk.unload();
                }
            }, 0, 0);
        }
    }
}
