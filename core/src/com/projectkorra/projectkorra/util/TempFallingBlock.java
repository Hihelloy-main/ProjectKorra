package com.projectkorra.projectkorra.util;

import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TempFallingBlock is a utility class that allows for the creation and management of temporary falling blocks in Minecraft.
 * It provides methods to create, manage, and remove falling blocks, as well as to check if a falling block is a TempFallingBlock.
 * Compatible with Spigot, Paper, Folia and Java 8-25 (likely to be compatible with higher or lower Java versions)
 */
public class TempFallingBlock {
    public static ConcurrentHashMap<FallingBlock, TempFallingBlock> instances = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<CoreAbility, Set<TempFallingBlock>> instancesByAbility = new ConcurrentHashMap<>();

    private final FallingBlock fallingblock;
    private final CoreAbility ability;
    private final long creation;
    private final boolean expire;
    private OnPlaceCallback onPlace;

    public TempFallingBlock(Location location, BlockData data, Vector velocity, CoreAbility ability) {
        this(location, data, velocity, ability, false);
    }

    public TempFallingBlock(Location location, BlockData data, Vector velocity, CoreAbility ability, boolean expire) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world cannot be null");
        }

        if (data == null) {
            throw new IllegalArgumentException("BlockData cannot be null");
        }

        if (ability == null) {
            throw new IllegalArgumentException("CoreAbility cannot be null");
        }

        this.fallingblock = location.getWorld().spawnFallingBlock(location, data.clone());
        this.fallingblock.setVelocity(velocity != null ? velocity : new Vector(0, 0, 0));
        this.fallingblock.setDropItem(false);
        this.ability = ability;
        this.creation = System.currentTimeMillis();
        this.expire = expire;
        this.onPlace = null;

        instances.put(this.fallingblock, this);

        if (!instancesByAbility.containsKey(this.ability)) {
            instancesByAbility.put(this.ability, new HashSet<TempFallingBlock>());
        }

        Set<TempFallingBlock> abilitySet = instancesByAbility.get(this.ability);
        if (abilitySet != null) {
            abilitySet.add(this);
        }
    }

    public static void manage() {
        long time = System.currentTimeMillis();
        List<TempFallingBlock> toRemove = new ArrayList<TempFallingBlock>();

        for (TempFallingBlock tfb : instances.values()) {
            long timeSinceCreation = time - tfb.getCreationTime();

            if (tfb.canExpire() && timeSinceCreation > 5000) {
                toRemove.add(tfb);
            } else if (timeSinceCreation > 120000) {
                toRemove.add(tfb);
            }
        }

        for (TempFallingBlock tfb : toRemove) {
            tfb.remove();
        }
    }

    public static TempFallingBlock get(FallingBlock fallingblock) {
        if (isTempFallingBlock(fallingblock)) {
            return instances.get(fallingblock);
        }
        return null;
    }

    public static boolean isTempFallingBlock(FallingBlock fallingblock) {
        return fallingblock != null && instances.containsKey(fallingblock);
    }

    public static void removeFallingBlock(FallingBlock fallingblock) {
        if (isTempFallingBlock(fallingblock)) {
            final TempFallingBlock tempFallingBlock = instances.get(fallingblock);

            ThreadUtil.ensureEntity(fallingblock, new Runnable() {
                @Override
                public void run() {
                    if (!fallingblock.isDead()) {
                        fallingblock.remove();
                    }
                }
            });

            instances.remove(fallingblock);

            if (tempFallingBlock.ability != null) {
                Set<TempFallingBlock> abilitySet = instancesByAbility.get(tempFallingBlock.ability);
                if (abilitySet != null) {
                    abilitySet.remove(tempFallingBlock);
                    if (abilitySet.isEmpty()) {
                        instancesByAbility.remove(tempFallingBlock.ability);
                    }
                }
            }
        }
    }

    public static void removeAllFallingBlocks() {
        List<FallingBlock> blocks = new ArrayList<FallingBlock>(instances.keySet());

        for (final FallingBlock fallingblock : blocks) {
            ThreadUtil.ensureEntity(fallingblock, new Runnable() {
                @Override
                public void run() {
                    if (!fallingblock.isDead()) {
                        fallingblock.remove();
                    }
                }
            });
        }

        instances.clear();
        instancesByAbility.clear();
    }

    public static Set<TempFallingBlock> getFromAbility(CoreAbility ability) {
        Set<TempFallingBlock> set = instancesByAbility.get(ability);
        return set != null ? set : new HashSet<TempFallingBlock>();
    }

    public void remove() {
        ThreadUtil.ensureEntity(this.fallingblock, new Runnable() {
            @Override
            public void run() {
                if (!TempFallingBlock.this.fallingblock.isDead()) {
                    TempFallingBlock.this.fallingblock.remove();
                }
            }
        });

        instances.remove(this.fallingblock);

        Set<TempFallingBlock> abilitySet = instancesByAbility.get(this.ability);
        if (abilitySet != null) {
            abilitySet.remove(this);
            if (abilitySet.isEmpty()) {
                instancesByAbility.remove(this.ability);
            }
        }
    }

    public FallingBlock getFallingBlock() {
        return this.fallingblock;
    }

    public CoreAbility getAbility() {
        return this.ability;
    }

    public Material getMaterial() {
        return this.fallingblock.getBlockData().getMaterial();
    }

    public BlockData getMaterialData() {
        return this.fallingblock.getBlockData();
    }

    public BlockData getData() {
        return this.fallingblock.getBlockData();
    }

    public Location getLocation() {
        return this.fallingblock.getLocation();
    }

    public long getCreationTime() {
        return this.creation;
    }

    public boolean canExpire() {
        return this.expire;
    }

    public void tryPlace() {
        if (this.onPlace != null) {
            this.onPlace.onPlace(this);
        }
    }

    public OnPlaceCallback getOnPlace() {
        return this.onPlace;
    }

    public void setOnPlace(OnPlaceCallback onPlace) {
        this.onPlace = onPlace;
    }

    @FunctionalInterface
    public interface OnPlaceCallback {
        void onPlace(TempFallingBlock tempFallingBlock);
    }
}
