package com.kohs.deatheffects.client.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.client.KohsDeathEffectsClient;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class DeathEffectManager {
	private static final int DEATH_SPAWN_LOCK_TICKS = 40;

	private final List<RisingSilhouetteEffect> effects = new ArrayList<>();
	private final Set<UUID> playersCurrentlyDead = new HashSet<>();
	private final Map<UUID, Integer> deathSpawnLocks = new HashMap<>();
	private boolean registered;

	public void register() {
		if (this.registered) {
			return;
		}

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			for (Iterator<RisingSilhouetteEffect> iterator = this.effects.iterator(); iterator.hasNext();) {
				RisingSilhouetteEffect effect = iterator.next();
				try {
					effect.render(context);
				} catch (RuntimeException | LinkageError exception) {
					iterator.remove();
					KohsDeathEffects.LOGGER.error("Discarding a death effect that failed to render", exception);
				}
			}
		});
		this.registered = true;
	}

	public void spawnForDeath(Player player) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.effectsEnabled || !config.selectedEffectEnabled()) {
			return;
		}

		UUID uuid = player.getUUID();
		if (this.deathSpawnLocks.containsKey(uuid) || !this.playersCurrentlyDead.add(uuid)) {
			return;
		}

		this.deathSpawnLocks.put(uuid, DEATH_SPAWN_LOCK_TICKS);
		Vec3 deathPosition = entityPosition(player);
		if (config.deathEffectMode == DeathEffectMode.MORPH && config.morphMobSoundEnabled) {
			MorphMobSoundPlayer.playConfigured(deathPosition, config);
		} else {
			DeathSoundManager.playAt(deathPosition);
		}
		if (config.deathEffectMode == DeathEffectMode.KIDS) {
			KohsDeathEffectsClient.onKidsPlayerDeath(player);
			return;
		}
		DamageSource recentDamageSource = player.getLastDamageSource();
		try {
			this.effects.add(RisingSilhouetteEffect.from(
				player,
				config,
				isExplosionDamage(recentDamageSource),
				recentDamageSource == null ? null : recentDamageSource.getSourcePosition()
			));
		} catch (RuntimeException | LinkageError exception) {
			KohsDeathEffects.LOGGER.error("Unable to create death effect mode {}", config.deathEffectMode, exception);
		}
	}

	private static boolean isExplosionDamage(DamageSource damageSource) {
		return damageSource != null
			&& (damageSource.is(DamageTypes.EXPLOSION) || damageSource.is(DamageTypes.PLAYER_EXPLOSION));
	}

	private static Vec3 entityPosition(Player player) {
		return new Vec3(player.getX(), player.getY(), player.getZ());
	}

	private void tick(Minecraft client) {
		if (client.level == null) {
			this.effects.clear();
			MorphMobSoundPlayer.clear();
			this.playersCurrentlyDead.clear();
			this.deathSpawnLocks.clear();
			return;
		}

		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.effectsEnabled || !config.selectedEffectEnabled()) {
			this.effects.clear();
			MorphMobSoundPlayer.clear();
			this.playersCurrentlyDead.clear();
			this.deathSpawnLocks.clear();
			return;
		}

		this.tickSpawnLocks();
		MorphMobSoundPlayer.tick();

		for (AbstractClientPlayer player : client.level.players()) {
			if (player.isAlive() && player.deathTime == 0 && !this.deathSpawnLocks.containsKey(player.getUUID())) {
				this.playersCurrentlyDead.remove(player.getUUID());
			} else if (player.isDeadOrDying() || player.deathTime > 0) {
				this.spawnForDeath(player);
			}
		}

		for (Iterator<RisingSilhouetteEffect> iterator = this.effects.iterator(); iterator.hasNext();) {
			RisingSilhouetteEffect effect = iterator.next();
			try {
				effect.tick();
			} catch (RuntimeException | LinkageError exception) {
				iterator.remove();
				KohsDeathEffects.LOGGER.error("Discarding a death effect that failed to update", exception);
				continue;
			}

			if (effect.isExpired()) {
				iterator.remove();
			}
		}
	}

	private void tickSpawnLocks() {
		for (Iterator<Map.Entry<UUID, Integer>> iterator = this.deathSpawnLocks.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticksLeft = entry.getValue() - 1;
			if (ticksLeft <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticksLeft);
			}
		}
	}
}
