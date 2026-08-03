package com.kohs.deatheffects.client.effect;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public final class KidsShoulderFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private final KidsEffectManager manager;

	public KidsShoulderFeatureRenderer(
		FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context,
		KidsEffectManager manager
	) {
		super(context);
		this.manager = manager;
	}

	@Override
	public void render(
		MatrixStack matrices,
		OrderedRenderCommandQueue commandQueue,
		int light,
		PlayerEntityRenderState state,
		float relativeHeadYaw,
		float pitch
	) {
		this.manager.renderShoulderDolls(matrices, commandQueue, light, state, this.getContextModel());
	}
}


