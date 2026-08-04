package com.kohs.deatheffects.client.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;

public final class DeathEffectManager {
	private static final int DEATH_SPAWN_LOCK_TICKS = 4;

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
			for (RisingSilhouetteEffect effect : this.effects) {
				effect.render(context);
			}
		});
		this.registered = true;
	}

	public void spawnForDeath(PlayerEntity player) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.effectsEnabled) {
			return;
		}

		UUID uuid = player.getUuid();
		if (this.deathSpawnLocks.containsKey(uuid) || !this.playersCurrentlyDead.add(uuid)) {
			return;
		}

		this.deathSpawnLocks.put(uuid, DEATH_SPAWN_LOCK_TICKS);
		if (config.deathEffectMode == DeathEffectMode.MORPH && config.morphMobSoundEnabled) {
			MorphMobSoundPlayer.playConfigured(player.getPos(), config);
		} else {
			DeathSoundManager.playAt(player.getPos());
		}
		DamageSource recentDamageSource = player.getRecentDamageSource();
		this.effects.add(RisingSilhouetteEffect.from(
			player,
			config,
			isExplosionDamage(recentDamageSource),
			recentDamageSource == null ? null : recentDamageSource.getPosition()
		));
	}

	private static boolean isExplosionDamage(DamageSource damageSource) {
		return damageSource != null
			&& (damageSource.isOf(DamageTypes.EXPLOSION) || damageSource.isOf(DamageTypes.PLAYER_EXPLOSION));
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
		if (!config.effectsEnabled) {
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
			} else if (player.getHealth() <= 0.0F || player.isDead() || player.deathTime > 0) {
				this.spawnForDeath(player);
			}
		}

		for (Iterator<RisingSilhouetteEffect> iterator = this.effects.iterator(); iterator.hasNext();) {
			RisingSilhouetteEffect effect = iterator.next();
			effect.tick();

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
