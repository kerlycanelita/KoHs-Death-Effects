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
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

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
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
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

	public void spawnForDeath(PlayerEntity player) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.effectsEnabled || !config.selectedEffectEnabled()) {
			return;
		}

		UUID uuid = player.getUuid();
		if (this.deathSpawnLocks.containsKey(uuid) || !this.playersCurrentlyDead.add(uuid)) {
			return;
		}

		this.deathSpawnLocks.put(uuid, DEATH_SPAWN_LOCK_TICKS);
		Vec3d deathPosition = entityPosition(player);
		if (config.deathEffectMode == DeathEffectMode.MORPH && config.morphMobSoundEnabled) {
			MorphMobSoundPlayer.playConfigured(deathPosition, config);
		} else {
			DeathSoundManager.playAt(deathPosition);
		}
		if (config.deathEffectMode == DeathEffectMode.KIDS) {
			KohsDeathEffectsClient.onKidsPlayerDeath(player);
			return;
		}
		DamageSource recentDamageSource = player.getRecentDamageSource();
		try {
			this.effects.add(RisingSilhouetteEffect.from(
				player,
				config,
				isExplosionDamage(recentDamageSource),
				recentDamageSource == null ? null : recentDamageSource.getPosition()
			));
		} catch (RuntimeException | LinkageError exception) {
			KohsDeathEffects.LOGGER.error("Unable to create death effect mode {}", config.deathEffectMode, exception);
		}
	}

	private static boolean isExplosionDamage(DamageSource damageSource) {
		return damageSource != null
			&& (damageSource.isOf(DamageTypes.EXPLOSION) || damageSource.isOf(DamageTypes.PLAYER_EXPLOSION));
	}

	private static Vec3d entityPosition(PlayerEntity player) {
		return new Vec3d(player.getX(), player.getY(), player.getZ());
	}

	private void tick(MinecraftClient client) {
		if (client.world == null) {
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

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.isAlive() && player.deathTime == 0 && !this.deathSpawnLocks.containsKey(player.getUuid())) {
				this.playersCurrentlyDead.remove(player.getUuid());
			} else if (player.isDead() || player.deathTime > 0) {
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
