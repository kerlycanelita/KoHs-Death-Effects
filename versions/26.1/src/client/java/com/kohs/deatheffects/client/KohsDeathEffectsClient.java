package com.kohs.deatheffects.client;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.config.BetaWarningScreen;
import com.kohs.deatheffects.client.effect.DeathEffectManager;
import com.kohs.deatheffects.client.effect.KidsEffectManager;
import com.kohs.deatheffects.client.effect.KidsShoulderFeatureRenderer;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.Player;

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
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				registrationHelper.register(new KidsShoulderFeatureRenderer(avatarRenderer, KIDS_EFFECT_MANAGER));
			}
		});
		DEATH_EFFECT_MANAGER.register();
		KIDS_EFFECT_MANAGER.register();
	}

	public static void onPlayerDeathStatus(Player player) {
		DEATH_EFFECT_MANAGER.spawnForDeath(player);
	}

	public static void onKidsPlayerDeath(Player player) {
		KIDS_EFFECT_MANAGER.onPlayerDeath(player);
	}

	public static KidsEffectManager getKidsEffectManager() {
		return KIDS_EFFECT_MANAGER;
	}

	private static void showBetaWarningIfNeeded(Minecraft client) {
		if (betaWarningShownThisSession || client == null || client.screen == null) {
			return;
		}

		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (config.betaWarningDismissed || client.screen instanceof BetaWarningScreen) {
			return;
		}

		Screen parent = client.screen;
		betaWarningShownThisSession = true;
		client.setScreen(new BetaWarningScreen(parent));
	}
}
