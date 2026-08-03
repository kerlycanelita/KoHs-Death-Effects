package com.kohs.deatheffects;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

public final class KohsDeathEffectsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_death_effects.json");
	private static KohsDeathEffectsConfig instance = new KohsDeathEffectsConfig();

	public boolean effectsEnabled = true;
	public DeathEffectMode deathEffectMode = DeathEffectMode.SILHOUETTE;
	public boolean risingSilhouetteEnabled = true;
	public int silhouetteColor = 0xB96BFF;
	public float silhouetteAlpha = 0.62F;
	public float silhouetteScale = 1.0F;
	public int silhouetteDurationSeconds = 10;
	public float silhouetteRiseHeight = 7.0F;
	public boolean playerGhostEnabled = true;
	public GhostMovementMode playerGhostMovement = GhostMovementMode.RISING;
	public float playerGhostAlpha = 0.92F;
	public int playerGhostDurationSeconds = 10;
	public float playerGhostRiseHeight = 7.0F;
	public boolean playerGhostArmorEnabled = true;
	public boolean playerGhostHeldItemsEnabled = true;
	public boolean ragdollEnabled = true;
	public int ragdollDurationSeconds = 10;
	public boolean ragdollFadeEnabled = true;
	public int ragdollFadeDurationSeconds = 5;
	public boolean ragdollClientCollisionEnabled = false;
	public boolean ragdollExplosionImpulseEnabled = true;
	@Deprecated
	public boolean ragdollSolidEnabled = false;
	public FaintAnimationType faintAnimationType = FaintAnimationType.FALL;
	public int faintCrawlSpeed = 100;
	public boolean kidsEnabled = true;
	public KidsMode kidsMode = KidsMode.CUMULATIVE;
	public int kidsTimerSeconds = 10;
	public int kidsCumulativeTimerSeconds = 301;
	public int kidsRopeSizePercent = 100;
	public KidsTrainFacing kidsTrainFacing = KidsTrainFacing.LOOK_DOWN;
	public boolean kidsAnimationEnabled = true;
	public boolean kidsDraggedHandMovementEnabled = true;
	public boolean morphEnabled = true;
	public String morphEntityTypeId = "minecraft:zombie";
	public float morphAlpha = 0.9F;
	public boolean morphElevationEnabled = true;
	public int morphElevationTimeSeconds = 10;
	public boolean morphMobSoundEnabled = false;
	public int morphMobSoundVolume = 100;
	public int morphMobSoundLoops = 1;
	public boolean customDeathSoundEnabled = false;
	public String customDeathSoundId = "";
	public float customDeathSoundVolume = 1.0F;
	public boolean vanillaDeathAnimationEnabled = false;
	public boolean betaWarningDismissed = false;

	public static KohsDeathEffectsConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.notExists(CONFIG_PATH)) {
			instance = new KohsDeathEffectsConfig();
			instance.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			KohsDeathEffectsConfig loaded = GSON.fromJson(reader, KohsDeathEffectsConfig.class);
			instance = loaded == null ? new KohsDeathEffectsConfig() : loaded;
			instance.sanitize();
		} catch (IOException | RuntimeException exception) {
			KohsDeathEffects.LOGGER.warn("Could not load KoHs Death Effects config, using defaults", exception);
			instance = new KohsDeathEffectsConfig();
			instance.save();
		}
	}

	public static KohsDeathEffectsConfig resetToDefaults() {
		instance = new KohsDeathEffectsConfig();
		instance.save();
		return instance;
	}

	public void save() {
		this.sanitize();

		try {
			Files.createDirectories(CONFIG_PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			KohsDeathEffects.LOGGER.warn("Could not save KoHs Death Effects config", exception);
		}
	}

	public int durationTicks() {
		return this.silhouetteDurationSeconds * 20;
	}

	public int playerGhostDurationTicks() {
		return this.playerGhostDurationSeconds * 20;
	}

	public int ragdollDurationTicks() {
		return this.ragdollDurationSeconds * 20;
	}

	public int ragdollFadeDurationTicks() {
		return this.ragdollFadeDurationSeconds * 20;
	}

	public int morphDurationTicks() {
		return this.morphElevationTimeSeconds * 20;
	}

	public boolean selectedEffectEnabled() {
		return switch (this.deathEffectMode) {
			case SILHOUETTE -> this.risingSilhouetteEnabled;
			case PLAYER_GHOST -> this.playerGhostEnabled;
			case RAGDOLL -> this.ragdollEnabled;
			case KIDS -> this.kidsEnabled;
			case MORPH -> this.morphEnabled;
		};
	}

	public void sanitize() {
		if (this.deathEffectMode == null) {
			this.deathEffectMode = DeathEffectMode.SILHOUETTE;
		}

		if (this.playerGhostMovement == null) {
			this.playerGhostMovement = GhostMovementMode.RISING;
		}

		this.risingSilhouetteEnabled = this.deathEffectMode == DeathEffectMode.SILHOUETTE;
		this.playerGhostEnabled = this.deathEffectMode == DeathEffectMode.PLAYER_GHOST;
		this.ragdollEnabled = this.deathEffectMode == DeathEffectMode.RAGDOLL;
		this.kidsEnabled = this.deathEffectMode == DeathEffectMode.KIDS;
		this.morphEnabled = this.deathEffectMode == DeathEffectMode.MORPH;

		this.silhouetteColor &= 0xFFFFFF;
		this.silhouetteAlpha = MathHelper.clamp(this.silhouetteAlpha, 0.05F, 1.0F);
		this.silhouetteScale = MathHelper.clamp(this.silhouetteScale, 0.5F, 2.5F);
		this.silhouetteDurationSeconds = MathHelper.clamp(this.silhouetteDurationSeconds, 1, 60);
		this.silhouetteRiseHeight = MathHelper.clamp(this.silhouetteRiseHeight, 0.5F, 64.0F);
		this.playerGhostAlpha = MathHelper.clamp(this.playerGhostAlpha, 0.05F, 1.0F);
		this.playerGhostDurationSeconds = MathHelper.clamp(this.playerGhostDurationSeconds, 1, 60);
		this.playerGhostRiseHeight = MathHelper.clamp(this.playerGhostRiseHeight, 0.5F, 64.0F);
		this.ragdollDurationSeconds = MathHelper.clamp(this.ragdollDurationSeconds, 1, 60);
		this.ragdollFadeDurationSeconds = MathHelper.clamp(this.ragdollFadeDurationSeconds, 5, 60);
		if (this.ragdollSolidEnabled) {
			this.ragdollClientCollisionEnabled = true;
		}
		if (this.faintAnimationType == null) {
			this.faintAnimationType = FaintAnimationType.FALL;
		}
		this.faintCrawlSpeed = MathHelper.clamp(this.faintCrawlSpeed, 100, 300);
		if (this.kidsMode == null) {
			this.kidsMode = KidsMode.CUMULATIVE;
		}
		this.kidsTimerSeconds = MathHelper.clamp(this.kidsTimerSeconds, 3, 301);
		this.kidsCumulativeTimerSeconds = MathHelper.clamp(this.kidsCumulativeTimerSeconds, 20, 301);
		this.kidsRopeSizePercent = MathHelper.clamp(this.kidsRopeSizePercent, 100, 300);
		if (this.kidsTrainFacing == null) {
			this.kidsTrainFacing = KidsTrainFacing.LOOK_DOWN;
		}
		if (this.morphEntityTypeId == null || this.morphEntityTypeId.isBlank()) {
			this.morphEntityTypeId = "minecraft:zombie";
		}
		this.morphAlpha = MathHelper.clamp(this.morphAlpha, 0.05F, 1.0F);
		this.morphElevationTimeSeconds = MathHelper.clamp(this.morphElevationTimeSeconds, 1, 60);
		this.morphMobSoundVolume = MathHelper.clamp(this.morphMobSoundVolume, 0, 300);
		this.morphMobSoundLoops = MathHelper.clamp(this.morphMobSoundLoops, 1, 3);
		if (this.morphMobSoundEnabled) {
			this.customDeathSoundEnabled = false;
		}
		if (this.customDeathSoundId == null) {
			this.customDeathSoundId = "";
		}
		this.customDeathSoundVolume = MathHelper.clamp(this.customDeathSoundVolume, 0.0F, 3.0F);
	}

	public enum DeathEffectMode {
		SILHOUETTE,
		PLAYER_GHOST,
		RAGDOLL,
		KIDS,
		MORPH
	}

	public enum GhostMovementMode {
		RISING,
		STATIC
	}

	public enum FaintAnimationType {
		FALL,
		CRAWL
	}

	public enum KidsMode {
		CUMULATIVE,
		ONLY_SHOULDERS
	}

	public enum KidsTrainFacing {
		LOOK_DOWN,
		LOOK_UP
	}
}


