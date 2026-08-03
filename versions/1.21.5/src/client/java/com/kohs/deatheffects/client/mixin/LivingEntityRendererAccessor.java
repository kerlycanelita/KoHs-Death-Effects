package com.kohs.deatheffects.client.mixin;

import java.util.List;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
	@Accessor("features")
	List<FeatureRenderer<?, ?>> kohsDeathEffects$getFeatures();
}

