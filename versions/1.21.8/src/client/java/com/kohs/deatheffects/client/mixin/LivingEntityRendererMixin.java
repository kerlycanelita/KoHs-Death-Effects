package com.kohs.deatheffects.client.mixin;

import com.kohs.deatheffects.KohsDeathEffectsConfig;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
		method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kohsDeathEffects$hideVanillaPlayerDeathAnimation(
		LivingEntityRenderState state,
		MatrixStack matrices,
		VertexConsumerProvider vertexConsumers,
		int light,
		CallbackInfo ci
	) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (config.effectsEnabled
			&& config.selectedEffectEnabled()
			&& !config.vanillaDeathAnimationEnabled
			&& state instanceof PlayerEntityRenderState
			&& state.deathTime > 0.0F) {
			ci.cancel();
		}
	}
}
