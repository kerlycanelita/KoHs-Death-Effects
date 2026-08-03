package com.kohs.deatheffects.client.mixin;

import com.kohs.deatheffects.client.KohsDeathEffectsClient;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererDeathEffectsMixin {
	@Shadow
	@Final
	private OrderedRenderCommandQueueImpl entityRenderCommandQueue;

	@Inject(
		method = "renderBlockDamage(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/state/WorldRenderState;)V",
		at = @At("TAIL")
	)
	private void kohsDeathEffects$renderDeathEffects(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, WorldRenderState worldState, CallbackInfo ci) {
		KohsDeathEffectsClient.renderDeathEffects(matrices, consumers, this.entityRenderCommandQueue, worldState.cameraRenderState);
	}
}
