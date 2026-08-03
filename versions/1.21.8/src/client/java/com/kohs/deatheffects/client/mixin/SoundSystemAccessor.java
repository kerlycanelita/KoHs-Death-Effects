package com.kohs.deatheffects.client.mixin;

import net.minecraft.client.sound.SoundExecutor;
import net.minecraft.client.sound.SoundSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
	@Accessor("taskQueue")
	SoundExecutor kohsDeathEffects$getTaskQueue();
}
