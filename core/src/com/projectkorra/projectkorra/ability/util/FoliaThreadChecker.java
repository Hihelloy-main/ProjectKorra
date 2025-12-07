package com.projectkorra.projectkorra.ability.util;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.StanceAbility;
import com.projectkorra.projectkorra.event.PlayerChangeRegionEvent;
import com.projectkorra.projectkorra.util.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutionException;

public class FoliaThreadChecker implements Runnable {

    private final Player player;
    private Location oldLocation;
    private StanceAbility oldStance;

    public FoliaThreadChecker(Player player) {
        this.player = player;
        this.oldLocation = null;
        this.oldStance = null;
    }

    @Override
    public void run() {

        if (!player.isOnline()) {
            ProjectKorra.log.info("Player is no longer online! Player: " + player.getName());
            return;
        }

        Location currentLocation = player.getLocation();

        if (oldLocation != null && !Bukkit.isOwnedByCurrentRegion(oldLocation)) {

            PlayerChangeRegionEvent event = new PlayerChangeRegionEvent(player, oldLocation, currentLocation);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                restoreOldStance();
                player.teleportAsync(oldLocation);
                try {
                    PaperLib.getChunkAtAsync(oldLocation).get().unload();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            saveOldStance();
            ProjectKorra.log.info(player.getName() + " changed regions. Restarting passives.");
            onChangeRegion();
            restoreOldStance();
        }

        this.oldLocation = currentLocation;
    }

    private void saveOldStance() {
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer != null) {
            oldStance = bPlayer.getStance();
        }
    }

    private void restoreOldStance() {
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer != null && oldStance != null) {
            bPlayer.setStance(oldStance);
        }
    }

    public void onChangeRegion() {
        PassiveManager.registerPassives(player);
    }
}
