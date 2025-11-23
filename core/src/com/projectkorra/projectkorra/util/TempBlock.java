package com.projectkorra.projectkorra.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Snowable;

import com.projectkorra.projectkorra.GeneralMethods;

/**
 * Utility class for the creation of temporary blocks
 */
public class TempBlock {

    private static final Map<Block, LinkedList<TempBlock>> instances_ = new HashMap<>();
    @Deprecated
    public static Map<Block, TempBlock> instances = new ConcurrentHashMap<>();
    private static final PriorityQueue<TempBlock> REVERT_QUEUE = new PriorityQueue<TempBlock>(128, new java.util.Comparator<TempBlock>() {
        @Override
        public int compare(TempBlock t1, TempBlock t2) {
            return (int) (t1.revertTime - t2.revertTime);
        }
    });
    private static boolean REVERT_TASK_RUNNING;

    private final Block block;
    private BlockData newData;
    private BlockState state;
    private Set<TempBlock> attachedTempBlocks;
    private long revertTime;
    private boolean inRevertQueue;
    private boolean reverted;
    private Runnable revertTask = null;
    private Optional<CoreAbility> ability = Optional.empty();
    private boolean isBendableSource = false;
    private boolean suffocate = true;

    public TempBlock(final Block block, final Material newtype) {
        this(block, newtype.createBlockData(), 0);
    }

    @Deprecated
    public TempBlock(final Block block, final Material newtype, final BlockData newData) {
        this(block, newData, 0);
    }

    public TempBlock(final Block block, final BlockData newData) {
        this(block, newData, 0);
    }

    public TempBlock(final Block block, final BlockData newData, final long revertTime, final CoreAbility ability) {
        this(block, newData, revertTime);
        this.ability = Optional.of(ability);
    }

    public TempBlock(final Block block, final BlockData newData, final CoreAbility ability) {
        this(block, newData, 0, ability);
    }

    public TempBlock(final Block block, BlockData newData, final long revertTime) {
        this.block = block;
        this.newData = newData;
        this.attachedTempBlocks = new HashSet<TempBlock>(0);
        this.suffocate = this.ability.isPresent() ? !(this.ability.get() instanceof WaterAbility) : false;

        if (!FireAbility.canFireGrief() && (newData.getMaterial() == Material.FIRE || newData.getMaterial() == Material.SOUL_FIRE)) {
            newData = FireAbility.createFireState(block, newData.getMaterial() == Material.SOUL_FIRE);
        }

        if (block.getType() == Material.SNOW) {
            if (newData.getMaterial() == Material.AIR) {
                updateSnowableBlock(block.getRelative(BlockFace.DOWN), false);
            }
        }

        if (instances_.containsKey(block)) {
            final TempBlock temp = instances_.get(block).getFirst();
            this.state = temp.state;
            put(block, this);
            block.setBlockData(newData, applyPhysics(newData.getMaterial()));
        } else {
            this.state = block.getState();

            if (this.state instanceof Container || this.state.getType() == Material.JUKEBOX) {
                return;
            }

            put(block, this);
            block.setBlockData(newData, applyPhysics(newData.getMaterial()));
        }

        this.setRevertTime(revertTime);
    }

    public static TempBlock get(final Block block) {
        if (isTempBlock(block)) {
            LinkedList<TempBlock> list = instances_.get(block);
            return list != null && !list.isEmpty() ? list.getLast() : null;
        }
        return null;
    }

    public static LinkedList<TempBlock> getAll(Block block) {
        return instances_.get(block);
    }

    private static void put(Block block, TempBlock tempBlock) {
        if (!instances_.containsKey(block)) {
            instances_.put(block, new LinkedList<TempBlock>());
        }
        instances_.get(block).add(tempBlock);
    }

    public static boolean isTempBlock(final Block block) {
        return block != null && instances_.containsKey(block);
    }

    public static boolean isTouchingTempBlock(final Block block) {
        BlockFace[] faces = new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };
        for (BlockFace face : faces) {
            if (instances_.containsKey(block.getRelative(face))) {
                return true;
            }
        }
        return false;
    }

    public static void removeAll() {
        Set<Block> blockSet = new HashSet<Block>(instances_.keySet());
        for (final Block block : blockSet) {
            ThreadUtil.ensureLocation(block.getLocation(), new Runnable() {
                @Override
                public void run() {
                    revertBlock(block, Material.AIR);
                }
            });
        }

        List<TempBlock> queueCopy = new ArrayList<TempBlock>(REVERT_QUEUE);
        for (final TempBlock tempblock : queueCopy) {
            ThreadUtil.ensureLocation(tempblock.getLocation(), new Runnable() {
                @Override
                public void run() {
                    tempblock.state.update(true, applyPhysics(tempblock.state.getType()));
                    if (tempblock.revertTask != null) {
                        tempblock.revertTask.run();
                    }
                }
            });
        }
        REVERT_QUEUE.clear();
    }

    public static void removeAllInWorld(World world) {
        Set<Block> blockSet = new HashSet<Block>(instances_.keySet());
        for (final Block block : blockSet) {
            if (block.getWorld() == world) {
                revertBlock(block, Material.AIR);
            }
        }
    }

    public static void removeBlock(final Block block) {
        LinkedList<TempBlock> list = instances_.get(block);
        if (list != null) {
            List<TempBlock> copy = new ArrayList<TempBlock>(list);
            for (TempBlock t : copy) {
                REVERT_QUEUE.remove(t);
                remove(t);
            }
        }
    }

    private static void remove(TempBlock tempBlock) {
        if (instances_.containsKey(tempBlock.block)) {
            instances_.get(tempBlock.block).remove(tempBlock);
            if (instances_.get(tempBlock.block).size() == 0) {
                instances_.remove(tempBlock.block);
            }
        }
    }

    public static void revertBlock(final Block block, final Material defaulttype) {
        if (instances_.containsKey(block)) {
            List<TempBlock> tempBlocks = new ArrayList<TempBlock>(instances_.get(block));
            for (TempBlock b : tempBlocks) {
                TempBlock.remove(b);
                b.trueRevertBlock();
            }
        } else {
            if ((defaulttype == Material.LAVA) && GeneralMethods.isAdjacentToThreeOrMoreSources(block, true)) {
                final BlockData data = Material.LAVA.createBlockData();

                if (data instanceof Levelled) {
                    ((Levelled) data).setLevel(0);
                }

                block.setBlockData(data, applyPhysics(data.getMaterial()));
            } else if ((defaulttype == Material.WATER) && GeneralMethods.isAdjacentToThreeOrMoreSources(block)) {
                final BlockData data = Material.WATER.createBlockData();

                if (data instanceof Levelled) {
                    ((Levelled) data).setLevel(0);
                }

                block.setBlockData(data, applyPhysics(data.getMaterial()));
            } else {
                block.setType(defaulttype, applyPhysics(defaulttype));
            }
        }
    }

    public Block getBlock() {
        return this.block;
    }

    public BlockData getBlockData() {
        return this.newData;
    }

    public Location getLocation() {
        return this.block.getLocation();
    }

    public BlockState getState() {
        return this.state;
    }

    public Optional<CoreAbility> getAbility() {
        return this.ability;
    }

    public Runnable getRevertTask() {
        return this.revertTask;
    }

    public void setRevertTask(final Runnable task) {
        this.revertTask = task;
    }

    @Deprecated
    public void setRevertTask(final RevertTask task) {
        this.revertTask = task;
    }

    public long getRevertTime() {
        return this.revertTime;
    }

    public void setRevertTime(final long revertTime) {
        if (revertTime <= 0 || this.state instanceof Container) {
            return;
        }
        this.revertTime = revertTime + System.currentTimeMillis();
        if (!this.inRevertQueue) {
            this.inRevertQueue = true;
            REVERT_QUEUE.add(this);
        }
    }

    public void revertBlock() {
        if (!this.reverted) {
            remove(this);
            trueRevertBlock();
        }
    }

    private void trueRevertBlock() {
        this.trueRevertBlock(true);
    }

    private void trueRevertBlock(boolean removeFromQueue) {
        this.reverted = true;

        if (instances_.containsKey(this.block)) {
            ensureChunkLoaded(this.block.getLocation(), new Runnable() {
                @Override
                public void run() {
                    LinkedList<TempBlock> list = instances_.get(TempBlock.this.block);
                    if (list != null && !list.isEmpty()) {
                        TempBlock last = list.getLast();
                        TempBlock.this.block.setBlockData(last.newData);
                    }
                }
            });
        } else {
            ensureChunkLoaded(this.block.getLocation(), new Runnable() {
                @Override
                public void run() {
                    revertState();
                }
            });
        }

        if (removeFromQueue) {
            REVERT_QUEUE.remove(this);
        }

        if (this.revertTask != null) {
            this.revertTask.run();
        }

        for (TempBlock attached : this.attachedTempBlocks) {
            attached.revertBlock();
        }
    }

    private void revertState() {
        Block block = this.state.getBlock();

        if (block.getType() != this.newData.getMaterial() && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE) {
            GeneralMethods.dropItems(block, GeneralMethods.getDrops(block, this.state.getType(), this.state.getBlockData()));
        } else {
            if (this.state.getType() == Material.SNOW) {
                updateSnowableBlock(block.getRelative(BlockFace.DOWN), true);
            }

            boolean applyPhysics = applyPhysics(this.state.getType()) && !(this.state.getBlockData() instanceof Bisected);
            this.state.update(true, applyPhysics);
        }
    }

    public void addAttachedBlock(TempBlock tempBlock) {
        this.attachedTempBlocks.add(tempBlock);
        tempBlock.attachedTempBlocks.add(this);
    }

    public Set<TempBlock> getAttachedTempBlocks() {
        return this.attachedTempBlocks;
    }

    @Experimental
    public boolean isBendableSource() {
        return this.isBendableSource;
    }

    @Experimental
    public TempBlock setBendableSource(boolean bool) {
        this.isBendableSource = bool;
        return this;
    }

    public boolean canSuffocate() {
        return this.suffocate;
    }

    public TempBlock setCanSuffocate(boolean suffocate) {
        this.suffocate = suffocate;
        return this;
    }

    public void setState(final BlockState newstate) {
        this.state = newstate;
    }

    public void setType(final Material material) {
        this.setType(material.createBlockData());
    }

    @Deprecated
    public void setType(final Material material, final BlockData data) {
        this.setType(data);
    }

    public void setType(final BlockData data) {
        if (isReverted())
            return;
        this.newData = data;
        this.block.setBlockData(data, applyPhysics(data.getMaterial()));
    }

    public boolean isReverted() {
        return this.reverted;
    }

    public TempBlock setAbility(CoreAbility ability) {
        this.ability = Optional.of(ability);
        return this;
    }

    @Deprecated
    public interface RevertTask extends Runnable { }

    public static boolean applyPhysics(Material material) {
        return GeneralMethods.isLightEmitting(material) || (material == Material.FIRE && FireAbility.canFireGrief());
    }

    public void updateSnowableBlock(Block b, boolean snowy) {
        if (b.getBlockData() instanceof Snowable) {
            final Snowable snowable = (Snowable) b.getBlockData();
            snowable.setSnowy(snowy);
            b.setBlockData(snowable);
        }
    }

    private static void ensureChunkLoaded(Location location, Runnable task) {
        ThreadUtil.ensureLocation(location, task);
    }

    @Override
    public String toString() {
        return "TempBlock{" +
                "block=[" + this.block.getX() + "," + this.block.getY() + "," + this.block.getZ() + "]" +
                ", newData=" + this.newData.getAsString() +
                ", attachedTempBlocks=" + this.attachedTempBlocks.size() +
                ", revertTime=" + (this.revertTime == 0 ? "N/A" : (this.revertTime - System.currentTimeMillis()) + "ms") +
                ", reverted=" + this.reverted +
                ", revertTask=" + (this.revertTask != null) +
                ", ability=" + (this.ability.isPresent() ? this.ability.get().getClass().getSimpleName() : "null") +
                ", isBendableSource=" + this.isBendableSource +
                ", suffocate=" + this.suffocate +
                '}';
    }

    public static class TempBlockRevertTask implements Runnable {
        @Override
        public void run() {
            final long currentTime = System.currentTimeMillis();
            while (!REVERT_QUEUE.isEmpty()) {
                final TempBlock tempBlock = REVERT_QUEUE.peek();
                if (currentTime >= tempBlock.getRevertTime()) {
                    REVERT_QUEUE.poll();
                    if (!tempBlock.reverted) {
                        remove(tempBlock);
                        ThreadUtil.ensureLocation(tempBlock.getLocation(), new Runnable() {
                            @Override
                            public void run() {
                                tempBlock.trueRevertBlock(false);
                            }
                        });
                    }
                } else {
                    break;
                }
            }
        }
    }
}
