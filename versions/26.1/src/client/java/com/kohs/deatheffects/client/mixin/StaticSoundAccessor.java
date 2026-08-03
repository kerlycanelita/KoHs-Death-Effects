package com.kohs.deatheffects.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.util.OptionalInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SoundBuffer.class)
public interface StaticSoundAccessor {
	@Invoker("getAlBuffer")
	OptionalInt kohsDeathEffects$getStreamBufferPointer();
}

