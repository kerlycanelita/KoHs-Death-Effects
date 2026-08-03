package com.kohs.deatheffects.client.mixin;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kohsDeathEffects$hideVanillaPlayerDeathAnimation(
		LivingEntityRenderState state,
		PoseStack matrices,
		SubmitNodeCollector commandQueue,
		CameraRenderState cameraState,
		CallbackInfo ci
	) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (config.effectsEnabled
			&& config.selectedEffectEnabled()
			&& !config.vanillaDeathAnimationEnabled
			&& state instanceof AvatarRenderState
			&& state.deathTime > 0.0F) {
			ci.cancel();
		}
	}
}
