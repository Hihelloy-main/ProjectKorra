package com.projectkorra.projectkorra.event;

import com.projectkorra.projectkorra.versions.LuminolIntermediate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
/**
 * <ul>An event that is called when a player changes regions in Folia.</ul>
 * <l1>{@link #getNewRegion()} {@link #getOldRegion()} {@link #getNewRegionId()} {@link #getOldRegionId()} are all broken on Folia due to missing API use Luminol.</l1>
 * @author Hihelloy
 * <ul>Created on 11/9/25 at 7:23pm</ul>
 */
public class PlayerChangeRegionEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    public boolean cancelled;
    public static Location OldLocation;
    public static Location NewLocation;
    public static Player player;

   public PlayerChangeRegionEvent(Location oldloc, Location newloc, Player p) {
       this.OldLocation = oldloc;
       this.NewLocation = newloc;
       this.player = p;
   }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
       this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }


    public Object getNewRegion() {
       return LuminolIntermediate.getRegion(getNewLocation());
    }

    public Object getOldRegion() {
       return LuminolIntermediate.getRegion(getOldLocation());
    }

    public long getNewRegionId() {
       return LuminolIntermediate.getRegionId(getNewRegion());
    }

    public long getOldRegionId() {
        return LuminolIntermediate.getRegionId(getOldRegion());
    }

    public Location getOldLocation() {
       return OldLocation;
    }

    public Location getNewLocation() {
       return NewLocation;
    }

    public Player getPlayer() {
       return player;
    }
}
