package com.kohs.deatheffects;

import com.kohs.deatheffects.network.KidsNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public final class KohsDeathEffects implements ModInitializer {
	public static final String MOD_ID = "kohs_death_effects";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		KidsNetworking.register();
		LOGGER.info("KoHs Death Effects loaded");
	}
}

