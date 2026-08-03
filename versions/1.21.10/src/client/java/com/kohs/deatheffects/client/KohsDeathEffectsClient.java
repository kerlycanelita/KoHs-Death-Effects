package com.kohs.deatheffects.client;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.config.BetaWarningScreen;
import com.kohs.deatheffects.client.effect.DeathEffectManager;
import com.kohs.deatheffects.client.effect.KidsEffectManager;
import com.kohs.deatheffects.client.effect.KidsShoulderFeatureRenderer;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;

public final class KohsDeathEffectsClient implements ClientModInitializer {
	private static final DeathEffectManager DEATH_EFFECT_MANAGER = new DeathEffectManager();
	private static final KidsEffectManager KIDS_EFFECT_MANAGER = new KidsEffectManager();
	private static boolean betaWarningShownThisSession;

	@Override
	public void onInitializeClient() {
		KohsDeathEffectsConfig.load();
		DeathSoundManager.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> DeathSoundManager.tick());
		ClientTickEvents.END_CLIENT_TICK.register(KohsDeathEffectsClient::showBetaWarningIfNeeded);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer<?> playerRenderer) {
				registrationHelper.register(new KidsShoulderFeatureRenderer(playerRenderer, KIDS_EFFECT_MANAGER));
			}
		});
		DEATH_EFFECT_MANAGER.register();
		KIDS_EFFECT_MANAGER.register();
	}

	public static void onPlayerDeathStatus(PlayerEntity player) {
		DEATH_EFFECT_MANAGER.spawnForDeath(player);
	}

	public static void onKidsPlayerDeath(PlayerEntity player) {
		KIDS_EFFECT_MANAGER.onPlayerDeath(player);
	}

	public static KidsEffectManager getKidsEffectManager() {
		return KIDS_EFFECT_MANAGER;
	}

	private static void showBetaWarningIfNeeded(MinecraftClient client) {
		if (betaWarningShownThisSession || client == null || client.currentScreen == null) {
			return;
		}

		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (config.betaWarningDismissed || client.currentScreen instanceof BetaWarningScreen) {
			return;
		}

		Screen parent = client.currentScreen;
		betaWarningShownThisSession = true;
		client.setScreen(new BetaWarningScreen(parent));
	}
}
