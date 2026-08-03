package com.kohs.deatheffects.client.mixin;

import net.minecraft.client.sound.Source;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Source.class)
public interface SourceAccessor {
	@Invoker("create")
	static Source kohsDeathEffects$create() {
		throw new AssertionError();
	}
}
