package com.kohs.deatheffects.client.mixin;

import java.util.OptionalInt;

import net.minecraft.client.sound.StaticSound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StaticSound.class)
public interface StaticSoundAccessor {
	@Invoker("getStreamBufferPointer")
	OptionalInt kohsDeathEffects$getStreamBufferPointer();
}
