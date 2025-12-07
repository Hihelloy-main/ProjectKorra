package com.projectkorra.projectkorra.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import com.projectkorra.projectkorra.ability.CoreAbility;

public class TempArmor {

    private static final Map<LivingEntity, PriorityQueue<TempArmor>> INSTANCES = new ConcurrentHashMap<>();
    private static final Map<LivingEntity, ItemStack[]> ORIGINAL = new HashMap<>();
    private static long defaultDuration = 30000L;

    private final LivingEntity entity;
    private final long startTime;
    private long duration;
    private final ItemStack[] oldArmor;
    private ItemStack[] newArmor;
    private final CoreAbility ability;
    private boolean removeAbilOnForceRevert = false;

    public TempArmor(final LivingEntity entity, final ItemStack[] armorItems) {
        this(entity, defaultDuration, null, armorItems);
    }

    public TempArmor(final LivingEntity entity, final CoreAbility ability, final ItemStack[] armorItems) {
        this(entity, defaultDuration, ability, armorItems);
    }

    public TempArmor(final LivingEntity entity, long duration, final CoreAbility ability, final ItemStack[] armorItems) {
        if (duration <= 0) {
            duration = defaultDuration;
        }

        this.entity = entity;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
        this.ability = ability;
        this.oldArmor = new ItemStack[] { new ItemStack(Material.AIR), new ItemStack(Material.AIR),
                new ItemStack(Material.AIR), new ItemStack(Material.AIR) };

        for (int i = 0; i < 4; i++) {
            if (this.entity.getEquipment().getArmorContents()[i] != null) {
                this.oldArmor[i] = this.entity.getEquipment().getArmorContents()[i].clone();
            }
        }

        if (!INSTANCES.containsKey(entity)) {
            ORIGINAL.put(entity, this.oldArmor);
            final PriorityQueue<TempArmor> queue = new PriorityQueue<>(10, (a, b) -> {
                final long current = System.currentTimeMillis();
                final long remainingA = a.getStartTime() + a.getDuration() - current;
                final long remainingB = b.getStartTime() + b.getDuration() - current;
                return (int) (remainingA - remainingB);
            });

            INSTANCES.put(entity, queue);
        }

        setArmor(armorItems);
        INSTANCES.get(entity).add(this);
    }

    public List<ItemStack> filterArmor(final List<ItemStack> drops) {
        final List<ItemStack> newDrops = new ArrayList<>();

        for (final ItemStack drop : drops) {
            boolean match = false;
            for (final ItemStack armorPiece : this.newArmor) {
                if (armorPiece != null && armorPiece.isSimilar(drop)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                newDrops.add(drop);
            }
        }

        for (final ItemStack armorPiece : this.oldArmor) {
            if (armorPiece != null && armorPiece.getType() != Material.AIR) {
                newDrops.add(armorPiece);
            }
        }
        return newDrops;
    }

    public CoreAbility getAbility() {
        return this.ability;
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    public long getDuration() {
        return this.duration;
    }

    public ItemStack[] getNewArmor() {
        return this.newArmor;
    }

    public ItemStack[] getOldArmor() {
        return this.oldArmor;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public void setArmor(final ItemStack[] armor) {
        this.newArmor = armor;

        ThreadUtil.ensureEntity(this.entity, () -> {
            final ItemStack[] actualArmor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                actualArmor[i] = armor[i] == null ? this.oldArmor[i] : armor[i];
            }
            this.entity.getEquipment().setArmorContents(actualArmor);
        });
    }

    private void updateArmor(final TempArmor next) {
        ThreadUtil.ensureEntity(this.entity, () -> {
            final ItemStack[] actualArmor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                actualArmor[i] = next.newArmor[i] == null ? next.oldArmor[i] : next.newArmor[i];
            }
            this.entity.getEquipment().setArmorContents(actualArmor);
        });
    }

    public void setRemovesAbilityOnForceRevert(final boolean bool) {
        this.removeAbilOnForceRevert = bool;
    }

    public void revert() {
        revert(null, true);
    }

    public void revert(List<ItemStack> drops, boolean keepInv) {
        final PriorityQueue<TempArmor> queue = INSTANCES.get(this.entity);

        if (queue.contains(this)) {
            final TempArmor head = queue.peek();
            if (head.equals(this)) {
                queue.poll();
                if (!queue.isEmpty()) {
                    this.updateArmor(queue.peek());
                }
            } else {
                queue.remove(this);
            }
        }

        if (drops != null) {
            for (ItemStack is : newArmor) {
                if (is != null) drops.remove(is);
            }
        }

        if (queue.isEmpty()) {
            ThreadUtil.ensureEntity(this.entity, () -> this.entity.getEquipment().setArmorContents(ORIGINAL.get(this.entity)));

            if (drops != null && !keepInv) {
                for (ItemStack is : ORIGINAL.get(this.entity)) {
                    if (is != null) drops.add(is);
                }
            }

            INSTANCES.remove(this.entity);
            ORIGINAL.remove(this.entity);
        }

        if (this.removeAbilOnForceRevert && this.ability != null && !this.ability.isRemoved()) {
            this.ability.remove();
        }
    }

    public static void cleanup() {
        for (final LivingEntity entity : INSTANCES.keySet()) {
            final PriorityQueue<TempArmor> queue = INSTANCES.get(entity);
            while (!queue.isEmpty()) {
                final TempArmor tarmor = queue.peek();
                if (System.currentTimeMillis() >= tarmor.getStartTime() + tarmor.getDuration()) {
                    ThreadUtil.ensureEntity(tarmor.getEntity(), tarmor::revert);
                } else {
                    break;
                }
            }
        }
    }

    public static void revertAll() {
        for (final LivingEntity entity : INSTANCES.keySet()) {
            while (!INSTANCES.get(entity).isEmpty()) {
                final TempArmor armor = INSTANCES.get(entity).poll();
                ThreadUtil.ensureEntity(armor.getEntity(), armor::revert);
            }
        }
    }

    public static boolean hasTempArmor(final LivingEntity entity) {
        return INSTANCES.containsKey(entity) && !INSTANCES.get(entity).isEmpty();
    }

    public static TempArmor getVisibleTempArmor(final LivingEntity entity) {
        if (!hasTempArmor(entity)) return null;
        return INSTANCES.get(entity).peek();
    }

    public static List<TempArmor> getTempArmorList(final LivingEntity entity) {
        if (!hasTempArmor(entity)) return Collections.emptyList();
        return new ArrayList<>(INSTANCES.get(entity));
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }
}
