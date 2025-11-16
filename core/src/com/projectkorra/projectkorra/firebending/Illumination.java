package com.projectkorra.projectkorra.firebending;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.markers.DayNightFactor;
import com.projectkorra.projectkorra.earthbending.Tremorsense;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ThreadUtil;
import org.bukkit.inventory.ItemStack;

public class Illumination extends FireAbility {

    private static final Map<Block, Player> BLOCKS = new ConcurrentHashMap<>();

    @Attribute(Attribute.COOLDOWN) @DayNightFactor(invert = true)
    private long cooldown;
    @Attribute(Attribute.RANGE) @DayNightFactor
    private double range;
    private int lightThreshold;
    private int lightLevel;
    private Block block;
    private int oldLevel;

    private static boolean MODERN;
    private static Material LIGHT;

    static {
        try {
            MODERN = GeneralMethods.getMCVersion() >= 1170;
        } catch (Throwable t) {
            MODERN = false;
        }
        if (MODERN) {
            try {
                LIGHT = Material.getMaterial("LIGHT");
                if (LIGHT == null) MODERN = false;
            } catch (Throwable ignored) {
                MODERN = false;
            }
        }
    }

    public Illumination(final Player player) {
        super(player);

        if (player == null || !player.isOnline()) return;

        this.range = getConfig().getDouble("Abilities.Fire.Illumination.Range");
        this.cooldown = getConfig().getLong("Abilities.Fire.Illumination.Cooldown");
        this.lightThreshold = getConfig().getInt("Abilities.Fire.Illumination.LightThreshold");

        if (MODERN) {
            this.lightLevel = getConfig().getInt("Abilities.Fire.Illumination.LightLevel");
        }

        final Illumination oldIllumination = getAbility(player, Illumination.class);
        if (oldIllumination != null) {
            ThreadUtil.ensureLocation(player.getLocation(), oldIllumination::remove);
            return;
        }

        if (this.bPlayer.isOnCooldown(this)) return;

        // Schedule ability start safely on the player's region thread
        ThreadUtil.ensureLocation(player.getLocation(), () -> {
            if (!player.isOnline()) return;

            if (player.getLocation().getBlock().getLightLevel() < this.lightThreshold
                    && (!MODERN || slotsFree(player))
                    && !isTremorsensing()) {

                try {
                    this.oldLevel = player.getLocation().getBlock().getLightLevel();
                } catch (Throwable ignored) {}

                this.bPlayer.addCooldown(this);
                this.start();
                this.set();
            }
        });
    }

    @Override
    public void progress() {
        if (!this.bPlayer.canBind(this) || this.bPlayer.isChiBlocked() || this.bPlayer.isParalyzed()
                || this.bPlayer.isBloodbent() || this.bPlayer.isControlledByMetalClips()
                || getConfig().getStringList("Properties.DisabledWorlds").contains(player.getLocation().getWorld().getName())
                || !this.bPlayer.isIlluminating()
                || isTremorsensing()
                || WaterAbility.isWater(getLocation().getBlock())
        ) {
            this.remove();
            return;
        }

        this.set();
    }

    private boolean isTremorsensing() {
        return this.bPlayer.hasElement(Element.EARTH) && this.bPlayer.isTremorSensing()
                && CoreAbility.getAbility(this.player, Tremorsense.class) != null
                && CoreAbility.getAbility(this.player, Tremorsense.class).isGlowing();
    }

    @Override
    public void remove() {
        super.remove();
        this.revert();
    }

    private void revert() {
        if (this.block == null) return;

        BLOCKS.remove(this.block);

        final Block revertBlock = this.block;
        ThreadUtil.ensureLocation(player.getLocation(), () -> {
            try {
                BlockData data = revertBlock.getBlockData();
                revertBlock.getWorld().getPlayers().forEach(p -> p.sendBlockChange(revertBlock.getLocation(), data));
            } catch (Throwable t) {
                try {
                    revertBlock.getWorld().getPlayers().forEach(p -> p.sendBlockChange(revertBlock.getLocation(), Material.AIR.createBlockData()));
                } catch (Throwable ignored) {}
            }
        });

        this.block = null;
    }

    private void set() {
        if (player == null || !player.isOnline()) return;

        ThreadUtil.ensureLocation(player.getLocation(), () -> {
            if (MODERN && LIGHT != null) {
                setModernLight();
            } else {
                setLegacyTorch();
            }
        });
    }

    private void setModernLight() {
        Block eyeBlock = this.player.getEyeLocation().getBlock();
        int level = lightLevel;

        if (!eyeBlock.getType().isAir() && (this.block == null || !this.block.equals(eyeBlock))) {
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block relative = eyeBlock.getRelative(face);
                if (relative.getType().isAir() || (this.block != null && this.block.equals(relative))) {
                    eyeBlock = relative;
                    level = lightLevel - 1;
                    break;
                }
            }
            if (!eyeBlock.getType().isAir()) return;
        }

        BlockData clonedData;
        try {
            clonedData = LIGHT.createBlockData();
            if (clonedData instanceof Levelled) ((Levelled) clonedData).setLevel(level);
        } catch (Throwable t) {
            return;
        }

        final Block newBlock = eyeBlock;
        final BlockData finalData = clonedData;

        this.revert(); // revert old block
        try {
            this.oldLevel = player.getLocation().getBlock().getLightLevel();
        } catch (Throwable ignored) {}

        if (this.oldLevel > this.lightThreshold) {
            remove();
            return;
        }

        this.block = newBlock;
        BLOCKS.put(this.block, this.player);
        this.block.getWorld().getPlayers().forEach(p -> p.sendBlockChange(this.block.getLocation(), finalData));
    }

    private void setLegacyTorch() {
        final Block standingBlock = this.player.getLocation().getBlock();
        final Block bellowBlock = standingBlock.getRelative(BlockFace.DOWN);

        if (!isIgnitable(standingBlock)) return;
        if (standingBlock.equals(this.block)) return;
        if (Tag.LEAVES.isTagged(bellowBlock.getType())) return;
        if (standingBlock.getType().name().endsWith("_FENCE")
                || standingBlock.getType().name().endsWith("_FENCE_GATE")
                || standingBlock.getType().name().endsWith("_WALL")
                || standingBlock.getType() == Material.IRON_BARS
                || standingBlock.getType().name().endsWith("_PANE")) return;

        final Material torch = bPlayer.canUseSubElement(SubElement.BLUE_FIRE) ? Material.SOUL_TORCH : Material.TORCH;
        final Block newBlock = standingBlock;

        this.revert();
        try {
            this.oldLevel = player.getLocation().getBlock().getLightLevel();
        } catch (Throwable ignored) {}

        if (this.oldLevel > this.lightThreshold) {
            remove();
            return;
        }

        this.block = newBlock;
        BLOCKS.put(this.block, this.player);
        this.block.getWorld().getPlayers().forEach(p -> p.sendBlockChange(this.block.getLocation(), torch.createBlockData()));
    }

    @Override
    public String getName() {
        return "Illumination";
    }

    @Override
    public Location getLocation() {
        return this.player != null ? (MODERN ? this.player.getEyeLocation() : this.player.getLocation()) : null;
    }

    @Override
    public long getCooldown() {
        return this.cooldown;
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    public double getRange() {
        return this.range;
    }

    public void setRange(final double range) {
        this.range = range;
    }

    public static boolean isModern() {
        return MODERN;
    }

    public Block getBlock() {
        return this.block;
    }

    public static Map<Block, Player> getBlocks() {
        return BLOCKS;
    }

    private static boolean slotsFree(Player player) {
        if (player == null || player.getInventory() == null) return true;
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return !(main != null && main.getType() != Material.AIR && off != null && off.getType() != Material.AIR);
    }

    public static void slotChange(Player player) {
        if (!MODERN) return;
        if (CoreAbility.hasAbility(player, Illumination.class)) return;

        ThreadUtil.ensureLocation(player.getLocation(), () -> {
            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.isIlluminating()) return;

            CoreAbility abilityRef = CoreAbility.getAbility(Illumination.class);
            if (!(abilityRef instanceof Illumination)) return;
            Illumination dummy = (Illumination) abilityRef;

            if (!dummy.isEnabled() || !bPlayer.canUsePassive(dummy) || !bPlayer.canBendPassive(dummy)) return;
            if (!slotsFree(player)) return;

            new Illumination(player);
        });
    }
}
