package com.projectkorra.projectkorra.firebending.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.util.RepeatingTask;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.firebending.HeatControl;
import com.projectkorra.projectkorra.util.DamageHandler;

public class FireDamageTimer extends RepeatingTask {

	private static final int MAX_TICKS = 90;
	private static final double DAMAGE = 1;
	private static final long BUFFER = 30;
	private static final Map<Entity, FireDamageTimer> INSTANCES = new ConcurrentHashMap<>();
	//private static final Map<Entity, Long> TIMES = new ConcurrentHashMap<>();

	private Entity entity;
	private Player source;
	private Ability ability;


	/**
	 * Deprecated. Use FireDamageTimer#FireDamageTimer(final Entity entity, Final Player source, Ability abil)
	 * instead.
	 * @param entity affected entity.
	 * @param source player who used the fire move.
	 */
	@Deprecated
	public FireDamageTimer(final Entity entity, final Player source) {
		this(entity, source, null, false);
	}
	
	public FireDamageTimer(final Entity entity, final Player source, Ability abil) {
		this(entity, source, abil, false);
	}

	public FireDamageTimer(final Entity entity, final Player source, Ability abil, final boolean affectSelf) {
        super(source);
        if (entity.getEntityId() == source.getEntityId() && !affectSelf) {
			return;
		}

		this.setParentAbility((CoreAbility) ability);

		this.entity = entity;
		this.ability = abil;
		this.source = source;

		INSTANCES.put(entity, this);
		start();
	}

	public static boolean isEnflamed(final Entity entity) {
		return INSTANCES.containsKey(entity);
	}
	
	public static void dealFlameDamage(final Entity entity, final double damage) {
		if (INSTANCES.containsKey(entity) && entity instanceof LivingEntity) {
			if (entity instanceof Player) {
				if (!HeatControl.canBurn((Player) entity)) {
					return;
				}
			}
			FireDamageTimer timer = INSTANCES.get(entity);
			final LivingEntity Lentity = (LivingEntity) entity;
			final Player source = timer.source;
			
			// damages the entity.
			if (timer.ability == null) {
				DamageHandler.damageEntity(Lentity, source, damage, CoreAbility.getAbilitiesByElement(Element.FIRE).get(0), false, true);
			} else {
				DamageHandler.damageEntity(Lentity, source, damage, timer.ability, false, true);
			}
			
			if (entity.getFireTicks() > MAX_TICKS) {
				entity.setFireTicks(MAX_TICKS);
			}
		}
	}

	public static void dealFlameDamage(final Entity entity) {
		dealFlameDamage(entity, DAMAGE);
	}

	/**
	 * Util so that players can find benders who were hurt/killed by bending
	 * fire as opened to regular firetick.
	 * @return Map from Entity to Player, entity on fire to player who set them
	 * alight with firebending.
	 */
	public static Map<Entity, FireDamageTimer> getInstances() {
		return INSTANCES;
	}

	@Override
	public void progress() {
		if (this.entity.getFireTicks() <= 0 || !this.entity.isValid() || !(this.entity instanceof LivingEntity)) {
			INSTANCES.remove(this.entity);
			remove();
		}
	}
}
