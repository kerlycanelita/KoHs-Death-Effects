package com.kohs.deatheffects.client.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntitySoundInvoker {
	@Invoker("getDeathSound")
	SoundEvent kohsDeathEffects$getDeathSound();

	@Invoker("getSoundVolume")
	float kohsDeathEffects$getSoundVolume();
}
