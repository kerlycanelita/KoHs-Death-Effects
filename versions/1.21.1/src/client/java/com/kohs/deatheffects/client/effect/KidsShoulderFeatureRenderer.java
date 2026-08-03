package com.kohs.deatheffects.client.effect;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

public final class KidsShoulderFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private final KidsEffectManager manager;

	public KidsShoulderFeatureRenderer(
		FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
		KidsEffectManager manager
	) {
		super(context);
		this.manager = manager;
	}

	@Override
	public void render(
		MatrixStack matrices,
		VertexConsumerProvider consumers,
		int light,
		AbstractClientPlayerEntity player,
		float limbAngle,
		float limbDistance,
		float tickDelta,
		float animationProgress,
		float headYaw,
		float headPitch
	) {
		this.manager.renderShoulderDolls(matrices, consumers, light, player, this.getContextModel(), tickDelta);
	}
}
