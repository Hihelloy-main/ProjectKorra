package com.projectkorra.projectkorra.ability.util;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class RepeatingTask extends ElementalAbility {

    private CoreAbility parentAbility;

    public RepeatingTask(@NotNull CoreAbility parentAbility) {
        super(parentAbility.getPlayer());
        this.parentAbility = parentAbility;
    }

    public RepeatingTask(@NotNull Player player) {
        super(player);
        this.parentAbility = null;
    }

    public CoreAbility getParentAbility() {
        return parentAbility;
    }

    public void setParentAbility(CoreAbility parentAbility) {
        this.parentAbility = parentAbility;
    }

    @Override
    public Element getElement() {
        return parentAbility == null ? Element.AVATAR : parentAbility.getElement();
    }

    @Override
    public long getCooldown() {
        return 0;
    }

    @Override
    public boolean isHiddenAbility() {
        return true;
    }

    @Override
    public boolean isIgniteAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isExplosiveAbility() {
        return false;
    }

    @Override
    public String getName() {
        return parentAbility == null ? "RepeatingTask" : parentAbility.getName() + "Task";
    }

    @Override
    public Location getLocation() {
        return parentAbility == null ? null : parentAbility.getLocation();
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
