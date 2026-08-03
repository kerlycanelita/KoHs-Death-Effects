package com.kohs.deatheffects.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Channel.class)
public interface SourceAccessor {
	@Invoker("create")
	static Channel kohsDeathEffects$create() {
		throw new AssertionError();
	}
}

