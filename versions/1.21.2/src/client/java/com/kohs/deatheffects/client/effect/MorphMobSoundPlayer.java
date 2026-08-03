package com.kohs.deatheffects.client.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.mixin.LivingEntitySoundInvoker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class MorphMobSoundPlayer {
	private static final List<PendingMorphMobSound> PENDING_SOUNDS = new ArrayList<>();

	private MorphMobSoundPlayer() {
	}

	public static void playConfigured(Vec3d position, KohsDeathEffectsConfig config) {
		String mobId = config.morphEntityTypeId;
		int volumePercent = config.morphMobSoundVolume;
		int loops = MathHelper.clamp(config.morphMobSoundLoops, 1, 3);
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

	private static void playOnce(Vec3d position, String mobId, int volumePercent) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		Entity entity = MorphMobCatalog.createEntity(client.world, mobId);
		if (!(entity instanceof LivingEntity livingEntity)) {
			return;
		}

		SoundEvent soundEvent = ((LivingEntitySoundInvoker)livingEntity).kohsDeathEffects$getDeathSound();
		if (soundEvent == null && livingEntity.getType() == EntityType.ENDER_DRAGON) {
			soundEvent = SoundEvents.ENTITY_ENDER_DRAGON_DEATH;
		}
		if (soundEvent == null) {
			return;
		}

		float volume = MathHelper.clamp(((LivingEntitySoundInvoker)livingEntity).kohsDeathEffects$getSoundVolume() * (volumePercent / 100.0F), 0.0F, 3.0F);
		if (volume <= 0.0F) {
			return;
		}

		client.getSoundManager().play(new PositionedSoundInstance(
			soundEvent,
			SoundCategory.PLAYERS,
			volume,
			livingEntity.getSoundPitch(),
			SoundInstance.createRandom(),
			position.x,
			position.y,
			position.z
		));
	}

	private static final class PendingMorphMobSound {
		private final Vec3d position;
		private final String mobId;
		private final int volumePercent;
		private int ticksLeft;

		private PendingMorphMobSound(Vec3d position, String mobId, int volumePercent, int ticksLeft) {
			this.position = position;
			this.mobId = mobId;
			this.volumePercent = volumePercent;
			this.ticksLeft = ticksLeft;
		}
	}
}
