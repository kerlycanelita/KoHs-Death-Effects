package com.kohs.deatheffects.client.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.mixin.LivingEntitySoundInvoker;

public final class MorphMobSoundPlayer {
	private static final List<PendingMorphMobSound> PENDING_SOUNDS = new ArrayList<>();

	private MorphMobSoundPlayer() {
	}

	public static void playConfigured(Vec3 position, KohsDeathEffectsConfig config) {
		String mobId = config.morphEntityTypeId;
		int volumePercent = config.morphMobSoundVolume;
		int loops = Mth.clamp(config.morphMobSoundLoops, 1, 3);
		playOnce(position, mobId, volumePercent);
		for (int index = 1; index < loops; index++) {
			PENDING_SOUNDS.add(new PendingMorphMobSound(position, mobId, volumePercent, index * 20));
		}
	}

	public static void tick() {
		for (Iterator<PendingMorphMobSound> iterator = PENDING_SOUNDS.iterator(); iterator.hasNext();) {
			PendingMorphMobSound pendingSound = iterator.next();
			pendingSound.ticksLeft--;
			if (pendingSound.ticksLeft <= 0) {
				playOnce(pendingSound.position, pendingSound.mobId, pendingSound.volumePercent);
				iterator.remove();
			}
		}
	}

	public static void clear() {
		PENDING_SOUNDS.clear();
	}

	private static void playOnce(Vec3 position, String mobId, int volumePercent) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}

		Entity entity = MorphMobCatalog.createEntity(client.level, mobId);
		if (!(entity instanceof LivingEntity livingEntity)) {
			return;
		}

		SoundEvent soundEvent = ((LivingEntitySoundInvoker)livingEntity).kohsDeathEffects$getDeathSound();
		if (soundEvent == null && livingEntity.getType() == EntityTypes.ENDER_DRAGON) {
			soundEvent = SoundEvents.ENDER_DRAGON_DEATH;
		}
		if (soundEvent == null) {
			return;
		}

		float volume = Mth.clamp(((LivingEntitySoundInvoker)livingEntity).kohsDeathEffects$getSoundVolume() * (volumePercent / 100.0F), 0.0F, 3.0F);
		if (volume <= 0.0F) {
			return;
		}

		client.getSoundManager().play(new SimpleSoundInstance(
			soundEvent,
			SoundSource.PLAYERS,
			volume,
			livingEntity.getVoicePitch(),
			SoundInstance.createUnseededRandom(),
			position.x,
			position.y,
			position.z
		));
	}

	private static final class PendingMorphMobSound {
		private final Vec3 position;
		private final String mobId;
		private final int volumePercent;
		private int ticksLeft;

		private PendingMorphMobSound(Vec3 position, String mobId, int volumePercent, int ticksLeft) {
			this.position = position;
			this.mobId = mobId;
			this.volumePercent = volumePercent;
			this.ticksLeft = ticksLeft;
		}
	}
}
