package com.kohs.deatheffects.client;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.effect.DeathEffectManager;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.player.PlayerEntity;

public final class KohsDeathEffectsClient implements ClientModInitializer {
	private static final DeathEffectManager DEATH_EFFECT_MANAGER = new DeathEffectManager();

	@Override
	public void onInitializeClient() {
		KohsDeathEffectsConfig.load();
		DeathSoundManager.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> DeathSoundManager.tick());
		DEATH_EFFECT_MANAGER.register();
	}

	public static void onPlayerDeathStatus(PlayerEntity player) {
		DEATH_EFFECT_MANAGER.spawnForDeath(player);
	}
}
