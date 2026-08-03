package com.kohs.deatheffects.client.effect;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.FaintAnimationType;
import com.kohs.deatheffects.KohsDeathEffectsConfig.GhostMovementMode;
import com.kohs.deatheffects.client.mixin.LivingEntityRendererAccessor;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class RisingSilhouetteEffect {
	private static final Identifier WHITE_TEXTURE = Identifier.of(KohsDeathEffects.MOD_ID, "effect/solid_white");
	private static final float PLAYER_MODEL_SCALE = 0.9375F;
	private static final String ARMOR_CUTOUT_LAYER = "armor_cutout_no_cull";
	private static final String LAYER_TEXTURE_PREFIX = "texture[Optional[";
	private static final int FAINT_FALL_TICKS = 26;
	private static final int FAINT_SETTLE_TICKS = 16;
	private static NativeImageBackedTexture whiteTexture;

	private final Vec3d position;
	private final Vec3d velocity;
	private final DeathEffectMode mode;
	private final GhostMovementMode movementMode;
	private final int color;
	private final float alpha;
	private final float scale;
	private final int durationTicks;
	private final float riseHeight;
	private final boolean renderGhostArmor;
	private final boolean renderGhostHeldItems;
	private final boolean morphElevationEnabled;
	private final Entity morphEntity;
	private final FaintAnimationType faintAnimationType;
	private final int faintCrawlSpeed;
	private final PlayerEntity sourcePlayer;
	private final PlayerEntityModel<PlayerEntity> playerModel;
	private final Identifier skinTexture;
	private final float bodyYaw;
	private int ageTicks;

	private RisingSilhouetteEffect(
		Vec3d position,
		Vec3d velocity,
		DeathEffectMode mode,
		GhostMovementMode movementMode,
		int color,
		float alpha,
		float scale,
		int durationTicks,
		float riseHeight,
		boolean renderGhostArmor,
		boolean renderGhostHeldItems,
		boolean morphElevationEnabled,
		Entity morphEntity,
		FaintAnimationType faintAnimationType,
		int faintCrawlSpeed,
		PlayerEntity sourcePlayer
	) {
		this.position = position;
		this.velocity = velocity;
		this.mode = mode;
		this.movementMode = movementMode;
		this.color = color;
		this.alpha = alpha;
		this.scale = scale;
		this.durationTicks = Math.max(1, durationTicks);
		this.riseHeight = riseHeight;
		this.renderGhostArmor = renderGhostArmor;
		this.renderGhostHeldItems = renderGhostHeldItems;
		this.morphElevationEnabled = morphElevationEnabled;
		this.morphEntity = morphEntity;
		this.faintAnimationType = faintAnimationType == null ? FaintAnimationType.FALL : faintAnimationType;
		this.faintCrawlSpeed = MathHelper.clamp(faintCrawlSpeed, 100, 300);
		this.sourcePlayer = sourcePlayer;
		this.bodyYaw = sourcePlayer.getBodyYaw();
		boolean slim = sourcePlayer instanceof AbstractClientPlayerEntity clientPlayer
			&& clientPlayer.getSkinTextures().model() == SkinTextures.Model.SLIM;
		this.skinTexture = sourcePlayer instanceof AbstractClientPlayerEntity clientPlayer
			? clientPlayer.getSkinTextures().texture()
			: DefaultSkinHelper.getTexture();
		this.playerModel = new PlayerEntityModel<>(
			MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER),
			slim
		);
	}

	public static RisingSilhouetteEffect from(PlayerEntity player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3d explosionPosition) {
		DeathEffectMode mode = config.deathEffectMode;
		GhostMovementMode movementMode = mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostMovement : GhostMovementMode.RISING;
		float alpha = switch (mode) {
			case PLAYER_GHOST -> config.playerGhostAlpha;
			case RAGDOLL, SILHOUETTE -> 1.0F;
			case KIDS -> 0.0F;
			case MORPH -> config.morphAlpha;
		};
		int durationTicks = switch (mode) {
			case PLAYER_GHOST -> config.playerGhostDurationTicks();
			case RAGDOLL -> 20 * 20;
			case KIDS -> 1;
			case MORPH -> config.morphDurationTicks();
			case SILHOUETTE -> config.durationTicks();
		};
		float riseHeight = mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostRiseHeight : mode == DeathEffectMode.MORPH ? 7.0F : config.silhouetteRiseHeight;
		return new RisingSilhouetteEffect(
			new Vec3d(player.getX(), player.getY(), player.getZ()),
			player.getVelocity(),
			mode,
			movementMode,
			config.silhouetteColor,
			alpha,
			mode == DeathEffectMode.SILHOUETTE ? config.silhouetteScale : 1.0F,
			durationTicks,
			riseHeight,
			mode == DeathEffectMode.PLAYER_GHOST && config.playerGhostArmorEnabled,
			mode == DeathEffectMode.PLAYER_GHOST && config.playerGhostHeldItemsEnabled,
			config.morphElevationEnabled,
			createMorphEntity(player, config),
			config.faintAnimationType,
			config.faintCrawlSpeed,
			player
		);
	}

	private static Entity createMorphEntity(PlayerEntity player, KohsDeathEffectsConfig config) {
		if (config.deathEffectMode != DeathEffectMode.MORPH) {
			return null;
		}
		Entity entity = MorphMobCatalog.createEntity(player.getWorld(), config.morphEntityTypeId);
		if (entity == null) {
			return null;
		}
		entity.age = player.age;
		entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		entity.setVelocity(Vec3d.ZERO);
		if (entity instanceof LivingEntity livingEntity) {
			livingEntity.bodyYaw = player.getBodyYaw();
			livingEntity.prevBodyYaw = player.getBodyYaw();
			livingEntity.headYaw = player.getHeadYaw();
			livingEntity.prevHeadYaw = player.getHeadYaw();
		}
		return entity;
	}

	public void tick() {
		this.ageTicks++;
	}

	public boolean isExpired() {
		return this.ageTicks > this.durationTicks;
	}

	public void render(WorldRenderContext context) {
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(false);
		float elapsedTicks = this.ageTicks + tickDelta;
		float progress = MathHelper.clamp(elapsedTicks / (float)this.durationTicks, 0.0F, 1.0F);
		float alphaNow = this.alpha * this.fade(progress);
		if (alphaNow <= 0.01F) {
			return;
		}

		Vec3d cameraPos = context.camera() == null ? Vec3d.ZERO : context.camera().getPos();
		float rise = this.shouldRise() ? this.riseHeight * easeOutCubic(progress) : 0.0F;
		int alphaInt = MathHelper.clamp((int)(alphaNow * 255.0F), 0, 255);

		matrices.push();
		matrices.translate(this.position.x - cameraPos.x, this.position.y - cameraPos.y + rise, this.position.z - cameraPos.z);
		switch (this.mode) {
			case PLAYER_GHOST -> this.renderPlayerGhost(matrices, consumers, alphaInt);
			case RAGDOLL -> this.renderRagdoll(matrices, consumers, alphaInt, elapsedTicks);
			case KIDS -> {
			}
			case MORPH -> this.renderMorph(matrices, consumers, alphaInt, tickDelta);
			case SILHOUETTE -> this.renderSilhouette(matrices, consumers, alphaInt);
		}
		matrices.pop();
	}

	private boolean shouldRise() {
		return this.mode == DeathEffectMode.SILHOUETTE
			|| this.mode == DeathEffectMode.PLAYER_GHOST && this.movementMode == GhostMovementMode.RISING
			|| this.mode == DeathEffectMode.MORPH && this.morphElevationEnabled;
	}

	private float fade(float progress) {
		return switch (this.mode) {
			case PLAYER_GHOST -> 1.0F - progress;
			case MORPH -> 1.0F - smoothStep(progress);
			case SILHOUETTE -> progress < 0.62F ? 1.0F : 1.0F - MathHelper.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F);
			case RAGDOLL -> progress < 0.72F ? 1.0F : 1.0F - MathHelper.clamp((progress - 0.72F) / 0.28F, 0.0F, 1.0F);
			case KIDS -> 0.0F;
		};
	}

	private void renderMorph(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float tickDelta) {
		if (this.morphEntity == null) {
			return;
		}
		this.morphEntity.age = this.ageTicks;
		VertexConsumerProvider alphaConsumers = alpha >= 255 ? consumers : new AlphaVertexConsumerProvider(consumers, alpha / 255.0F);
		MinecraftClient.getInstance().getEntityRenderDispatcher().render(
			this.morphEntity,
			0.0,
			0.0,
			0.0,
			this.morphEntity.getYaw(),
			tickDelta,
			matrices,
			alphaConsumers,
			LightmapTextureManager.MAX_LIGHT_COORDINATE
		);
	}

	private void renderSilhouette(MatrixStack matrices, VertexConsumerProvider consumers, int alpha) {
		int renderColor = argb(alpha, this.color >> 16 & 0xFF, this.color >> 8 & 0xFF, this.color & 0xFF);
		VertexConsumer vertices = new FixedColorVertexConsumer(
			consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(getWhiteTexture(), false)),
			renderColor
		);
		matrices.push();
		matrices.scale(this.scale, this.scale, this.scale);
		this.renderBasePlayerModel(matrices, vertices, renderColor, false);
		matrices.pop();
	}

	private void renderPlayerGhost(MatrixStack matrices, VertexConsumerProvider consumers, int alpha) {
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(this.skinTexture, false));
		this.renderBasePlayerModel(matrices, vertices, argb(alpha, 255, 255, 255), true);
		this.renderVanillaFeaturePass(matrices, consumers, alpha);
	}

	private void renderVanillaFeaturePass(MatrixStack matrices, VertexConsumerProvider consumers, int alpha) {
		if ((!this.renderGhostArmor && !this.renderGhostHeldItems)
			|| !(this.sourcePlayer instanceof AbstractClientPlayerEntity clientPlayer)) {
			return;
		}

		EntityRenderer<? super AbstractClientPlayerEntity> renderer = MinecraftClient.getInstance()
			.getEntityRenderDispatcher()
			.getRenderer(clientPlayer);
		if (!(renderer instanceof PlayerEntityRenderer playerRenderer)) {
			return;
		}

		LivingEntityRendererAccessor accessor;
		try {
			accessor = (LivingEntityRendererAccessor)(Object)playerRenderer;
		} catch (ClassCastException ignored) {
			return;
		}

		PlayerEntityModel<AbstractClientPlayerEntity> featureModel = playerRenderer.getModel();
		featureModel.animateModel(clientPlayer, 0.0F, 0.0F, 1.0F);
		featureModel.setAngles(clientPlayer, 0.0F, 0.0F, clientPlayer.age, 0.0F, clientPlayer.getPitch());
		featureModel.setVisible(true);
		VertexConsumerProvider featureConsumers = alpha >= 255
			? consumers
			: new AlphaVertexConsumerProvider(consumers, alpha / 255.0F);

		matrices.push();
		if (clientPlayer.isInSneakingPose()) {
			matrices.translate(0.0F, -2.0F / 16.0F, 0.0F);
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - this.bodyYaw));
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);

		for (FeatureRenderer<?, ?> feature : accessor.kohsDeathEffects$getFeatures()) {
			boolean armorFeature = feature instanceof ArmorFeatureRenderer<?, ?, ?>;
			boolean heldItemFeature = feature instanceof HeldItemFeatureRenderer<?, ?>;
			if (armorFeature && this.renderGhostArmor || heldItemFeature && this.renderGhostHeldItems) {
				this.renderFeature(feature, matrices, featureConsumers, clientPlayer);
			}
		}
		matrices.pop();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void renderFeature(FeatureRenderer<?, ?> feature, MatrixStack matrices, VertexConsumerProvider consumers, AbstractClientPlayerEntity player) {
		((FeatureRenderer)feature).render(
			matrices,
			consumers,
			LightmapTextureManager.MAX_LIGHT_COORDINATE,
			player,
			0.0F,
			0.0F,
			1.0F,
			player.age,
			0.0F,
			player.getPitch()
		);
	}

	private void renderRagdoll(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float elapsedTicks) {
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(this.skinTexture, false));
		this.preparePlayerModel(true);
		float fall = MathHelper.clamp(elapsedTicks / FAINT_FALL_TICKS, 0.0F, 1.0F);
		float settle = MathHelper.clamp((elapsedTicks - FAINT_FALL_TICKS) / FAINT_SETTLE_TICKS, 0.0F, 1.0F);
		float crawlAmount = this.faintAnimationType == FaintAnimationType.CRAWL
			? smoothStep(MathHelper.clamp((elapsedTicks - 54.0F) / 18.0F, 0.0F, 1.0F))
			: 0.0F;
		float crawlCycle = crawlAmount > 0.0F
			? MathHelper.sin(elapsedTicks * (0.34F + this.faintCrawlSpeed / 260.0F))
			: 0.0F;
		this.applyFaintPose(fall, settle, crawlCycle, crawlAmount);

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - this.bodyYaw));
		float bodyPitch = this.faintAnimationType == FaintAnimationType.CRAWL
			? MathHelper.lerp(crawlAmount, 90.0F, -90.0F)
			: 90.0F;
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(easeOutCubic(fall), 10.0F, bodyPitch)));
		float settledRoll = MathHelper.lerp(smoothStep(settle), 0.0F, 24.0F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
			this.faintAnimationType == FaintAnimationType.CRAWL
				? MathHelper.lerp(crawlAmount, settledRoll, crawlCycle * 3.0F)
				: settledRoll
		));
		matrices.translate(0.0F, -0.07F * smoothStep(settle) - 0.04F * crawlAmount, 0.0F);
		this.renderPreparedPlayerModel(matrices, vertices, argb(alpha, 255, 255, 255));
		matrices.pop();
	}

	private void renderBasePlayerModel(MatrixStack matrices, VertexConsumer vertices, int color, boolean showSkinLayers) {
		this.preparePlayerModel(showSkinLayers);
		matrices.push();
		if (this.sourcePlayer.isInSneakingPose()) {
			matrices.translate(0.0F, -2.0F / 16.0F, 0.0F);
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - this.bodyYaw));
		this.renderPreparedPlayerModel(matrices, vertices, color);
		matrices.pop();
	}

	private void preparePlayerModel(boolean showSkinLayers) {
		this.playerModel.animateModel(this.sourcePlayer, 0.0F, 0.0F, 1.0F);
		this.playerModel.setAngles(this.sourcePlayer, 0.0F, 0.0F, this.sourcePlayer.age, 0.0F, this.sourcePlayer.getPitch());
		this.playerModel.setVisible(true);
		this.playerModel.hat.visible = showSkinLayers;
		this.playerModel.jacket.visible = showSkinLayers;
		this.playerModel.leftPants.visible = showSkinLayers;
		this.playerModel.rightPants.visible = showSkinLayers;
		this.playerModel.leftSleeve.visible = showSkinLayers;
		this.playerModel.rightSleeve.visible = showSkinLayers;
	}

	private void renderPreparedPlayerModel(MatrixStack matrices, VertexConsumer vertices, int color) {
		matrices.push();
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		this.playerModel.render(matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, color);
		matrices.pop();
	}

	private void applyFaintPose(float fall, float settle, float crawlCycle, float crawlAmount) {
		float limp = smoothStep(fall);
		float grounded = smoothStep(settle);
		float rightPull = (crawlCycle + 1.0F) * 0.5F;
		float leftPull = 1.0F - rightPull;
		float bodySway = crawlCycle * crawlAmount;
		this.playerModel.head.pitch = MathHelper.lerp(grounded, 0.24F * limp, MathHelper.lerp(crawlAmount, 0.34F, -0.18F));
		this.playerModel.head.yaw = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, -0.42F, bodySway * 0.10F));
		this.playerModel.head.roll = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, 0.10F, -bodySway * 0.05F));
		this.playerModel.body.pitch = MathHelper.lerp(grounded, 0.10F * limp, MathHelper.lerp(crawlAmount, 0.03F, -0.08F + Math.abs(crawlCycle) * 0.04F));
		this.playerModel.body.roll = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, -0.04F, bodySway * 0.07F));
		this.playerModel.rightArm.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.70F + limp * 0.25F, 1.20F), -1.20F + rightPull * 0.58F);
		this.playerModel.rightArm.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.16F, -0.48F), -0.58F + rightPull * 0.28F);
		this.playerModel.rightArm.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.12F, 0.26F), 0.44F - rightPull * 0.20F);
		this.playerModel.leftArm.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.68F + limp * 0.24F, 1.16F), -1.20F + leftPull * 0.58F);
		this.playerModel.leftArm.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.16F, 0.46F), 0.58F - leftPull * 0.28F);
		this.playerModel.leftArm.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.12F, -0.25F), -0.44F + leftPull * 0.20F);
		this.playerModel.rightLeg.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.08F, 0.10F), 0.32F - rightPull * 0.12F);
		this.playerModel.leftLeg.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.08F, -0.08F), 0.32F - leftPull * 0.12F);
		this.playerModel.hat.copyTransform(this.playerModel.head);
		this.playerModel.leftSleeve.copyTransform(this.playerModel.leftArm);
		this.playerModel.rightSleeve.copyTransform(this.playerModel.rightArm);
		this.playerModel.leftPants.copyTransform(this.playerModel.leftLeg);
		this.playerModel.rightPants.copyTransform(this.playerModel.rightLeg);
		this.playerModel.jacket.copyTransform(this.playerModel.body);
	}

	private static float smoothStep(float value) {
		float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}

	private void renderHumanoid(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float progress) {
		int renderColor = this.mode == DeathEffectMode.SILHOUETTE
			? argb(alpha, this.color >> 16 & 0xFF, this.color >> 8 & 0xFF, this.color & 0xFF)
			: argb(alpha, 245, 235, 255);
		VertexConsumer vertices = new FixedColorVertexConsumer(
			consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(getWhiteTexture(), false)),
			renderColor
		);

		matrices.push();
		matrices.scale(this.scale, this.scale, this.scale);
		if (this.mode == DeathEffectMode.RAGDOLL) {
			float yaw = (float)Math.toDegrees(Math.atan2(this.velocity.x, this.velocity.z));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(84.0F));
			matrices.translate(0.0F, -0.18F, 0.0F);
		} else {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		}
		drawBody(matrices, vertices, renderColor);
		matrices.pop();
	}

	private static void drawBody(MatrixStack matrices, VertexConsumer vertices, int color) {
		drawCuboid(matrices, vertices, color, 0.0F, 1.5F, 0.0F, 0.5F, 0.5F, 0.5F);
		drawCuboid(matrices, vertices, color, 0.0F, 0.75F, 0.0F, 0.62F, 0.82F, 0.32F);
		drawCuboid(matrices, vertices, color, -0.52F, 0.74F, 0.0F, 0.22F, 0.78F, 0.24F);
		drawCuboid(matrices, vertices, color, 0.52F, 0.74F, 0.0F, 0.22F, 0.78F, 0.24F);
		drawCuboid(matrices, vertices, color, -0.18F, -0.12F, 0.0F, 0.24F, 0.9F, 0.24F);
		drawCuboid(matrices, vertices, color, 0.18F, -0.12F, 0.0F, 0.24F, 0.9F, 0.24F);
	}

	private static void drawCuboid(MatrixStack matrices, VertexConsumer vertices, int color, float cx, float cy, float cz, float width, float height, float depth) {
		MatrixStack.Entry entry = matrices.peek();
		float x1 = cx - width / 2.0F;
		float x2 = cx + width / 2.0F;
		float y1 = cy - height / 2.0F;
		float y2 = cy + height / 2.0F;
		float z1 = cz - depth / 2.0F;
		float z2 = cz + depth / 2.0F;
		quad(vertices, entry, color, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0.0F, 0.0F, 1.0F);
		quad(vertices, entry, color, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0.0F, 0.0F, -1.0F);
		quad(vertices, entry, color, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0.0F, 1.0F, 0.0F);
		quad(vertices, entry, color, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0.0F, -1.0F, 0.0F);
		quad(vertices, entry, color, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1.0F, 0.0F, 0.0F);
		quad(vertices, entry, color, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1.0F, 0.0F, 0.0F);
	}

	private static void quad(VertexConsumer vertices, MatrixStack.Entry entry, int color, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float normalX, float normalY, float normalZ) {
		vertex(vertices, entry, color, x1, y1, z1, 0.0F, 1.0F, normalX, normalY, normalZ);
		vertex(vertices, entry, color, x2, y2, z2, 1.0F, 1.0F, normalX, normalY, normalZ);
		vertex(vertices, entry, color, x3, y3, z3, 1.0F, 0.0F, normalX, normalY, normalZ);
		vertex(vertices, entry, color, x4, y4, z4, 0.0F, 0.0F, normalX, normalY, normalZ);
	}

	private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, int color, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
		vertices.vertex(entry, x, y, z)
			.color(red(color), green(color), blue(color), alpha(color))
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
			.normal(entry, normalX, normalY, normalZ);
	}

	private static Identifier getWhiteTexture() {
		if (whiteTexture == null) {
			whiteTexture = new NativeImageBackedTexture(2, 2, false);
			NativeImage image = whiteTexture.getImage();
			for (int x = 0; x < 2; x++) {
				for (int y = 0; y < 2; y++) {
					image.setColor(x, y, 0xFFFFFFFF);
				}
			}
			whiteTexture.upload();
			MinecraftClient.getInstance().getTextureManager().registerTexture(WHITE_TEXTURE, whiteTexture);
		}
		return WHITE_TEXTURE;
	}

	private static float easeOutCubic(float value) {
		float inverse = 1.0F - MathHelper.clamp(value, 0.0F, 1.0F);
		return 1.0F - inverse * inverse * inverse;
	}

	private static int argb(int alpha, int red, int green, int blue) {
		return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
	}

	private static int alpha(int color) {
		return color >>> 24 & 0xFF;
	}

	private static int red(int color) {
		return color >> 16 & 0xFF;
	}

	private static int green(int color) {
		return color >> 8 & 0xFF;
	}

	private static int blue(int color) {
		return color & 0xFF;
	}

	private static final class FixedColorVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int color;

		private FixedColorVertexConsumer(VertexConsumer delegate, int color) {
			this.delegate = delegate;
			this.color = color;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			this.delegate.color(this.color);
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			this.delegate.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			this.delegate.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			this.delegate.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}
	}

	private static final class AlphaVertexConsumerProvider implements VertexConsumerProvider {
		private final VertexConsumerProvider delegate;
		private final float alphaMultiplier;

		private AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alphaMultiplier) {
			this.delegate = delegate;
			this.alphaMultiplier = MathHelper.clamp(alphaMultiplier, 0.0F, 1.0F);
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			return new AlphaVertexConsumer(this.delegate.getBuffer(remapArmorLayer(layer)), this.alphaMultiplier);
		}

		private static RenderLayer remapArmorLayer(RenderLayer layer) {
			String description = layer.toString();
			if (!description.contains(ARMOR_CUTOUT_LAYER)) {
				return layer;
			}

			int textureStart = description.indexOf(LAYER_TEXTURE_PREFIX);
			if (textureStart < 0) {
				return layer;
			}

			textureStart += LAYER_TEXTURE_PREFIX.length();
			int textureEnd = description.indexOf("]]", textureStart);
			if (textureEnd <= textureStart) {
				return layer;
			}

			try {
				return RenderLayer.getEntityTranslucent(Identifier.of(description.substring(textureStart, textureEnd)), false);
			} catch (RuntimeException ignored) {
				return layer;
			}
		}
	}

	private static final class AlphaVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final float alphaMultiplier;

		private AlphaVertexConsumer(VertexConsumer delegate, float alphaMultiplier) {
			this.delegate = delegate;
			this.alphaMultiplier = alphaMultiplier;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			this.delegate.color(red, green, blue, MathHelper.clamp((int)(alpha * this.alphaMultiplier), 0, 255));
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			this.delegate.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			this.delegate.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			this.delegate.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}
	}
}
