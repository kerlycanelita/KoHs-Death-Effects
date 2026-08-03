package com.kohs.deatheffects.client.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundSystemAccessor {
	@Accessor("executor")
	SoundEngineExecutor kohsDeathEffects$getTaskQueue();
}

