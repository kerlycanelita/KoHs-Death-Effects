package com.kohs.deatheffects.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class KidsShoulderFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final KidsEffectManager manager;

	public KidsShoulderFeatureRenderer(
		RenderLayerParent<AvatarRenderState, PlayerModel> context,
		KidsEffectManager manager
	) {
		super(context);
		this.manager = manager;
	}

	@Override
	public void submit(
		PoseStack matrices,
		SubmitNodeCollector commandQueue,
		int light,
		AvatarRenderState state,
		float relativeHeadYaw,
		float pitch
	) {
		this.manager.renderShoulderDolls(matrices, commandQueue, light, state, this.getParentModel());
	}
}
