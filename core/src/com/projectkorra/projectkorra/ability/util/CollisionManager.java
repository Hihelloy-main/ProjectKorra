package com.projectkorra.projectkorra.ability.util;

import java.util.*;
import java.util.concurrent.TimeUnit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.event.AbilityCollisionEvent;

/**
 * A CollisionManager monitors and handles collisions between CoreAbilities.
 *
 * Fully compatible with Spigot, Paper, and Folia.
 * Safe for Java 8 and newer.
 */
public class CollisionManager {

    private boolean removeMultipleInstances;
    private long detectionDelay;
    private double certainNoCollisionDistance;
    private ArrayList<Collision> collisions;

    // BukkitRunnable for Spigot/Paper
    private BukkitRunnable detectionRunnable;
    // Folia async scheduler task (stored as Object for cross-compatibility)
    private Object foliaTask;

    public CollisionManager() {
        this.removeMultipleInstances = true;
        this.detectionDelay = 1;
        this.certainNoCollisionDistance = 100;
        this.collisions = new ArrayList<Collision>();
    }

    /**
     * Core collision detection logic.
     */
    private void detectCollisions() {
        int activeInstanceCount = 0;
        for (final CoreAbility ability : CoreAbility.getAbilitiesByInstances()) {
            if (!(ability instanceof PassiveAbility)) {
                if (++activeInstanceCount > 1) break;
            }
        }

        if (activeInstanceCount <= 1) return;

        final HashMap<CoreAbility, List<Location>> locationsCache = new HashMap<CoreAbility, List<Location>>();

        for (final Collision collision : this.collisions) {
            final Collection<? extends CoreAbility> instancesFirst =
                    CoreAbility.getAbilities(collision.getAbilityFirst().getClass());
            if (instancesFirst.isEmpty()) continue;

            final Collection<? extends CoreAbility> instancesSecond =
                    CoreAbility.getAbilities(collision.getAbilitySecond().getClass());
            if (instancesSecond.isEmpty()) continue;

            final HashSet<CoreAbility> alreadyCollided = new HashSet<CoreAbility>();
            final double certainNoCollisionDistSquared = Math.pow(this.certainNoCollisionDistance, 2);

            for (final CoreAbility abilityFirst : instancesFirst) {
                if (abilityFirst.getPlayer() == null ||
                        alreadyCollided.contains(abilityFirst) ||
                        !abilityFirst.isCollidable()) continue;

                if (!locationsCache.containsKey(abilityFirst)) {
                    locationsCache.put(abilityFirst, abilityFirst.getLocations());
                }
                final List<Location> locationsFirst = locationsCache.get(abilityFirst);
                if (locationsFirst.isEmpty()) continue;

                for (final CoreAbility abilitySecond : instancesSecond) {
                    if (abilitySecond.getPlayer() == null ||
                            alreadyCollided.contains(abilitySecond) ||
                            !abilitySecond.isCollidable()) continue;
                    if (abilityFirst.getPlayer().equals(abilitySecond.getPlayer())) continue;

                    if (!locationsCache.containsKey(abilitySecond)) {
                        locationsCache.put(abilitySecond, abilitySecond.getLocations());
                    }
                    final List<Location> locationsSecond = locationsCache.get(abilitySecond);
                    if (locationsSecond.isEmpty()) continue;

                    boolean collided = false;
                    boolean certainNoCollision = false;
                    Location locationFirst = null;
                    Location locationSecond = null;
                    final double requiredDist = abilityFirst.getCollisionRadius() + abilitySecond.getCollisionRadius();
                    final double requiredDistSquared = Math.pow(requiredDist, 2);

                    for (int i = 0; i < locationsFirst.size(); i++) {
                        locationFirst = locationsFirst.get(i);
                        if (locationFirst == null) continue;

                        for (int j = 0; j < locationsSecond.size(); j++) {
                            locationSecond = locationsSecond.get(j);
                            if (locationSecond == null) continue;
                            if (locationFirst.getWorld() != locationSecond.getWorld()) continue;

                            final double distSquared = locationFirst.distanceSquared(locationSecond);
                            if (distSquared <= requiredDistSquared) {
                                collided = true;
                                break;
                            } else if (distSquared >= certainNoCollisionDistSquared) {
                                certainNoCollision = true;
                                break;
                            }
                        }
                        if (collided || certainNoCollision) break;
                    }

                    if (collided) {
                        final Collision forwardCollision = new Collision(
                                abilityFirst, abilitySecond,
                                collision.isRemovingFirst(), collision.isRemovingSecond(),
                                locationFirst, locationSecond);

                        final Collision reverseCollision = new Collision(
                                abilitySecond, abilityFirst,
                                collision.isRemovingSecond(), collision.isRemovingFirst(),
                                locationSecond, locationFirst);

                        final AbilityCollisionEvent event = new AbilityCollisionEvent(forwardCollision);
                        Bukkit.getServer().getPluginManager().callEvent(event);
                        if (event.isCancelled()) continue;

                        abilityFirst.handleCollision(forwardCollision);
                        abilitySecond.handleCollision(reverseCollision);

                        if (!this.removeMultipleInstances) {
                            alreadyCollided.add(abilityFirst);
                            alreadyCollided.add(abilitySecond);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a Collision rule to be monitored.
     */
    public void addCollision(final Collision collision) {
        if (collision == null ||
                collision.getAbilityFirst() == null ||
                collision.getAbilitySecond() == null) return;

        this.collisions.removeIf(existing ->
                existing.getAbilityFirst().equals(collision.getAbilityFirst()) &&
                        existing.getAbilitySecond().equals(collision.getAbilitySecond()));

        this.collisions.add(collision);
    }

    /**
     * Starts collision detection using Folia or legacy scheduler automatically.
     */
    public void startCollisionDetection() {
        stopCollisionDetection();

        if (ProjectKorra.isFolia()) {
            // Use Folia’s async scheduler (safe global task)
            try {
                this.foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                        ProjectKorra.plugin,
                        new java.util.function.Consumer<ScheduledTask>() {
                            @Override
                            public void accept(ScheduledTask task) {
                                try {
                                    detectCollisions();
                                } catch (Throwable t) {
                                    ProjectKorra.plugin.getLogger().warning("Error during collision detection (Folia):");
                                    t.printStackTrace();
                                }
                            }
                        },
                        0L,
                        this.detectionDelay * 50L, // Convert ticks to ms
                        TimeUnit.MILLISECONDS
                );
            } catch (Throwable t) {
                ProjectKorra.plugin.getLogger().warning("Failed to start Folia async scheduler, falling back to BukkitRunnable.");
                startBukkitFallback();
            }
        } else {
            startBukkitFallback();
        }
    }

    /**
     * Fallback for Bukkit/Paper (non-Folia).
     */
    private void startBukkitFallback() {
        this.detectionRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    detectCollisions();
                } catch (Throwable t) {
                    ProjectKorra.plugin.getLogger().warning("Error during collision detection:");
                    t.printStackTrace();
                }
            }
        };
        this.detectionRunnable.runTaskTimer(ProjectKorra.plugin, 0L, this.detectionDelay);
    }

    /**
     * Stops collision detection task safely.
     */
    public void stopCollisionDetection() {
        // Cancel BukkitRunnable (Spigot/Paper)
        if (this.detectionRunnable != null) {
            this.detectionRunnable.cancel();
            this.detectionRunnable = null;
        }

        // Cancel Folia task safely if applicable
        try {
            if (this.foliaTask != null && this.foliaTask instanceof ScheduledTask) {
                ScheduledTask task = (ScheduledTask) this.foliaTask;
                if (!task.isCancelled()) {
                    task.cancel();
                }
                this.foliaTask = null;
            }
        } catch (NoClassDefFoundError ignored) {
            // Folia classes not available, safe to ignore
        }
    }

    // Getters / setters
    public boolean isRemoveMultipleInstances() { return this.removeMultipleInstances; }
    public void setRemoveMultipleInstances(final boolean removeMultipleInstances) { this.removeMultipleInstances = removeMultipleInstances; }
    public long getDetectionDelay() { return this.detectionDelay; }
    public void setDetectionDelay(final long detectionDelay) { this.detectionDelay = detectionDelay; }
    public double getCertainNoCollisionDistance() { return this.certainNoCollisionDistance; }
    public void setCertainNoCollisionDistance(final double certainNoCollisionDistance) { this.certainNoCollisionDistance = certainNoCollisionDistance; }
    public ArrayList<Collision> getCollisions() { return this.collisions; }
    public void setCollisions(final ArrayList<Collision> collisions) { this.collisions = collisions; }
    public BukkitRunnable getDetectionRunnable() { return this.detectionRunnable; }
    public void setDetectionRunnable(final BukkitRunnable detectionRunnable) { this.detectionRunnable = detectionRunnable; }
}
