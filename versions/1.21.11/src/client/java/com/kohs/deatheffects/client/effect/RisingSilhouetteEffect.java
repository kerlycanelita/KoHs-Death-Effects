package com.kohs.deatheffects.client.effect;

import java.util.List;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.FaintAnimationType;
import com.kohs.deatheffects.KohsDeathEffectsConfig.GhostMovementMode;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RisingSilhouetteEffect {
	private static final Identifier WHITE_TEXTURE = Identifier.of(KohsDeathEffects.MOD_ID, "effect/solid_white");
	private static final float PLAYER_MODEL_SCALE = 0.9375F;
	private static final float SKIN_TEXTURE_SIZE = 64.0F;
	private static final String ARMOR_CUTOUT_LAYER = "armor_cutout_no_cull";
	private static final String LAYER_TEXTURE_PREFIX = "texture[Optional[";
	private static final float RAGDOLL_PUSH_RADIUS = 1.25F;
	private static final float RAGDOLL_MAX_OFFSET = 3.0F;
	private static final float FAINT_START_HEIGHT = 0.0F;
	private static final float FAINT_GROUND_CONTACT_OFFSET = 0.025F;
	private static final float FAINT_FALL_PITCH = 90.0F;
	private static final float FAINT_CRAWL_PITCH = -90.0F;
	private static final float FAINT_SURFACE_FOLLOW_SPEED = 0.35F;
	private static final int FAINT_SETTLE_TICKS = 16;
	private static final int FAINT_CRAWL_GROUND_HOLD_TICKS = 12;
	private static final int FAINT_CRAWL_PREP_TICKS = 18;
	private static final int FAINT_DEFAULT_FADE_START_TICKS = 170;
	private static final int FAINT_FADE_TICKS = 45;
	private static NativeImageBackedTexture whiteTexture;

	private final Vec3d position;
	private final DeathEffectMode mode;
	private final GhostMovementMode movementMode;
	private final int color;
	private final float scale;
	private final float alpha;
	private final int durationTicks;
	private final float riseHeight;
	private final boolean renderGhostFeatures;
	private final boolean ragdollFadeEnabled;
	private final int ragdollFadeDurationTicks;
	private final boolean ragdollClientCollisionEnabled;
	private final boolean morphElevationEnabled;
	private final Entity morphEntity;
	private final FaintAnimationType faintAnimationType;
	private final int faintCrawlSpeed;
	private final PoseSnapshot pose;
	private final RagdollShape ragdollShape;
	private final RagdollBody ragdollBody;
	private final PlayerEntityModel model;
	private Vec3d ragdollOffset = Vec3d.ZERO;
	private Vec3d ragdollVelocity = Vec3d.ZERO;
	private Vec3d faintCrawlOffset = Vec3d.ZERO;
	private boolean faintSurfaceResolved;
	private float faintSurfaceOffsetY;
	private int faintFallDurationTicks = 26;
	private int faintFadeTicks;
	private boolean faintReachedTarget;
	private float faintCrawlYaw = Float.NaN;
	private int ageTicks;

	private RisingSilhouetteEffect(
		Vec3d position,
		DeathEffectMode mode,
		GhostMovementMode movementMode,
		int color,
		float scale,
		float alpha,
		int durationTicks,
		float riseHeight,
		boolean renderGhostFeatures,
		boolean ragdollFadeEnabled,
		int ragdollFadeDurationTicks,
		boolean ragdollClientCollisionEnabled,
		boolean morphElevationEnabled,
		Entity morphEntity,
		FaintAnimationType faintAnimationType,
		int faintCrawlSpeed,
		Vec3d ragdollExplosionImpulse,
		PoseSnapshot pose
	) {
		this.position = position;
		this.mode = mode;
		this.movementMode = movementMode;
		this.color = color & 0xFFFFFF;
		this.scale = scale;
		this.alpha = alpha;
		this.durationTicks = Math.max(1, durationTicks);
		this.riseHeight = riseHeight;
		this.renderGhostFeatures = renderGhostFeatures;
		this.ragdollFadeEnabled = ragdollFadeEnabled;
		this.ragdollFadeDurationTicks = Math.max(1, ragdollFadeDurationTicks);
		this.ragdollClientCollisionEnabled = ragdollClientCollisionEnabled;
		this.morphElevationEnabled = morphElevationEnabled;
		this.morphEntity = morphEntity;
		this.faintAnimationType = faintAnimationType == null ? FaintAnimationType.FALL : faintAnimationType;
		this.faintCrawlSpeed = MathHelper.clamp(faintCrawlSpeed, 100, 300);
		this.pose = pose;
		this.ragdollShape = RagdollShape.from(pose);
		this.ragdollBody = null;
		this.model = pose.createModel();
	}

	public static RisingSilhouetteEffect from(PlayerEntity player, KohsDeathEffectsConfig config) {
		return from(player, config, false, null);
	}

	public static RisingSilhouetteEffect from(PlayerEntity player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3d explosionPosition) {
		DeathEffectMode mode = config.deathEffectMode;
		GhostMovementMode movementMode = mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostMovement : GhostMovementMode.RISING;

		return new RisingSilhouetteEffect(
			entityPosition(player),
			mode,
			movementMode,
			config.silhouetteColor,
			mode == DeathEffectMode.SILHOUETTE ? config.silhouetteScale : 1.0F,
			mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostAlpha : mode == DeathEffectMode.MORPH ? config.morphAlpha : 1.0F,
			durationTicks(config, mode),
			mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostRiseHeight : mode == DeathEffectMode.MORPH ? 7.0F : config.silhouetteRiseHeight,
			mode == DeathEffectMode.PLAYER_GHOST && (config.playerGhostArmorEnabled || config.playerGhostHeldItemsEnabled),
			config.ragdollFadeEnabled,
			config.ragdollFadeDurationTicks(),
			config.ragdollClientCollisionEnabled,
			config.morphElevationEnabled,
			createMorphEntity(player, config),
			config.faintAnimationType,
			config.faintCrawlSpeed,
			Vec3d.ZERO,
			PoseSnapshot.from(player, config)
		);
	}

	private static Entity createMorphEntity(PlayerEntity player, KohsDeathEffectsConfig config) {
		if (config.deathEffectMode != DeathEffectMode.MORPH) {
			return null;
		}

		Entity entity = MorphMobCatalog.createEntity(player.getEntityWorld(), config.morphEntityTypeId);
		if (entity == null) {
			return null;
		}

		entity.age = player.age;
		entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		entity.setVelocity(Vec3d.ZERO);
		if (entity instanceof LivingEntity livingEntity) {
			livingEntity.bodyYaw = player.getBodyYaw();
			livingEntity.lastBodyYaw = player.getBodyYaw();
			livingEntity.headYaw = player.getHeadYaw();
			livingEntity.lastHeadYaw = player.getHeadYaw();
		}
		return entity;
	}

	private static Vec3d createExplosionImpulse(PlayerEntity player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3d explosionPosition) {
		if (!explosionDeath || !config.ragdollExplosionImpulseEnabled) {
			return Vec3d.ZERO;
		}

		Vec3d center = entityPosition(player).add(0.0, 0.9, 0.0);
		Vec3d origin = explosionPosition == null ? center.subtract(player.getRotationVec(1.0F).multiply(2.0)) : explosionPosition;
		Vec3d direction = center.subtract(origin);
		if (direction.lengthSquared() < 0.0001) {
			Vec3d velocity = player.getVelocity().multiply(1.0, 0.0, 1.0);
			direction = velocity.lengthSquared() > 0.0001 ? velocity : player.getRotationVec(1.0F).multiply(1.0, 0.0, 1.0);
		}

		double distance = Math.max(0.6, direction.length());
		double strength = MathHelper.clamp(1.4 / distance, 0.45, 1.75);
		Vec3d normalized = direction.normalize();
		return new Vec3d(normalized.x * 0.26 * strength, 0.12 * strength + Math.max(0.0, normalized.y) * 0.12, normalized.z * 0.26 * strength);
	}

	private static int durationTicks(KohsDeathEffectsConfig config, DeathEffectMode mode) {
		return switch (mode) {
			case PLAYER_GHOST -> config.playerGhostDurationTicks();
			case RAGDOLL -> 20 * 20;
			case KIDS -> 1;
			case MORPH -> config.morphDurationTicks();
			case SILHOUETTE -> config.durationTicks();
		};
	}

	public void tick() {
		this.tickFaint();
		this.ageTicks++;
	}

	private void tickFaint() {
		if (this.mode != DeathEffectMode.RAGDOLL) {
			return;
		}

		this.resolveFaintSurface();
		int impactTick = this.faintFallDurationTicks + FAINT_SETTLE_TICKS;
		if (this.ageTicks < impactTick) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (this.faintAnimationType == FaintAnimationType.CRAWL && !this.faintReachedTarget && client.player != null) {
			int crawlPrepareTick = impactTick + FAINT_CRAWL_GROUND_HOLD_TICKS;
			if (this.ageTicks < crawlPrepareTick) {
				return;
			}

			Vec3d target = entityPosition(client.player).subtract(this.position);
			Vec3d horizontalTarget = new Vec3d(target.x, 0.0, target.z);
			Vec3d delta = horizontalTarget.subtract(this.faintCrawlOffset);
			double distance = delta.length();
			if (distance > 0.0001) {
				float targetYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
				float currentYaw = Float.isNaN(this.faintCrawlYaw) ? this.pose.state().bodyYaw : this.faintCrawlYaw;
				this.faintCrawlYaw = MathHelper.lerpAngleDegrees(0.2F, currentYaw, targetYaw);
			}

			if (this.ageTicks < crawlPrepareTick + FAINT_CRAWL_PREP_TICKS) {
				return;
			}

			if (distance <= 0.72) {
				this.faintReachedTarget = true;
			} else {
				double step = Math.min(distance, 0.045 * (this.faintCrawlSpeed / 100.0));
				this.faintCrawlOffset = this.faintCrawlOffset.add(delta.normalize().multiply(step));
			}

			this.updateFaintCrawlSurface();
		}

		if (this.faintReachedTarget || this.ageTicks >= FAINT_DEFAULT_FADE_START_TICKS) {
			this.faintFadeTicks++;
		}
	}

	private void resolveFaintSurface() {
		if (this.faintSurfaceResolved) {
			return;
		}

		this.faintSurfaceResolved = true;
		float surfaceOffset = this.findFaintSurfaceOffset(this.position.x, this.position.z, this.position.y + 0.35);
		this.faintSurfaceOffsetY = Float.isNaN(surfaceOffset) ? 0.0F : surfaceOffset;
		float fallDistance = Math.max(0.75F, FAINT_START_HEIGHT - this.faintSurfaceOffsetY);
		this.faintFallDurationTicks = MathHelper.clamp((int)(16.0F + Math.sqrt(fallDistance) * 12.0F), 26, 78);
	}

	private void updateFaintCrawlSurface() {
		double x = this.position.x + this.faintCrawlOffset.x;
		double z = this.position.z + this.faintCrawlOffset.z;
		double currentSurfaceY = this.position.y + this.faintSurfaceOffsetY;
		float surfaceOffset = this.findFaintSurfaceOffset(x, z, currentSurfaceY + 1.05);
		if (!Float.isNaN(surfaceOffset)) {
			this.faintSurfaceOffsetY = MathHelper.lerp(FAINT_SURFACE_FOLLOW_SPEED, this.faintSurfaceOffsetY, surfaceOffset);
		}
	}

	private float findFaintSurfaceOffset(double x, double z, double highestSurfaceY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return Float.NaN;
		}

		int startY = MathHelper.floor(highestSurfaceY);
		int minY = Math.max(client.world.getBottomY(), startY - 96);
		for (int y = startY; y >= minY; y--) {
			BlockPos blockPos = BlockPos.ofFloored(x, y, z);
			BlockState state = client.world.getBlockState(blockPos);
			VoxelShape shape = state.getCollisionShape(client.world, blockPos);
			if (shape.isEmpty()) {
				continue;
			}

			double top = blockPos.getY() + shape.getMax(Direction.Axis.Y);
			if (top <= highestSurfaceY) {
				return (float)(top - this.position.y);
			}
		}

		return Float.NaN;
	}

	public boolean isExpired() {
		if (this.mode == DeathEffectMode.RAGDOLL) {
			return this.faintFadeTicks > FAINT_FADE_TICKS;
		}

		return this.ageTicks > this.durationTicks;
	}

	public void render(WorldRenderContext context) {
		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}

		float tickProgress = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
		float elapsedTicks = this.ageTicks + tickProgress;
		float progress = MathHelper.clamp(elapsedTicks / (float)this.durationTicks, 0.0F, 1.0F);
		float alphaNow = this.alpha * this.getFade(progress, elapsedTicks);
		if (alphaNow <= 0.01F) {
			return;
		}

		CameraRenderState cameraState = context.worldState().cameraRenderState;
		Vec3d cameraPos = cameraState != null && cameraState.pos != null ? cameraState.pos : Vec3d.ZERO;
		int alphaInt = MathHelper.clamp((int)(alphaNow * 255.0F), 0, 255);
		float rise = this.shouldRise() ? this.riseHeight * easeOutCubic(progress) : 0.0F;

		matrices.push();
		Vec3d renderPosition = this.mode == DeathEffectMode.RAGDOLL
			? this.position.add(this.faintCrawlOffset.x, this.getFaintYOffset(elapsedTicks), this.faintCrawlOffset.z)
			: this.position;
		matrices.translate(renderPosition.x - cameraPos.x, renderPosition.y - cameraPos.y + rise, renderPosition.z - cameraPos.z);

		switch (this.mode) {
			case PLAYER_GHOST -> this.renderPlayerGhost(matrices, consumers, context.commandQueue(), cameraState, alphaInt);
			case RAGDOLL -> this.renderRagdoll(matrices, consumers, alphaInt, progress, elapsedTicks);
			case KIDS -> {
			}
			case MORPH -> this.renderMorph(matrices, context.commandQueue(), cameraState, alphaInt, tickProgress);
			case SILHOUETTE -> this.renderSilhouette(matrices, consumers, alphaInt);
		}

		matrices.pop();
	}

	private boolean shouldRise() {
		return this.mode == DeathEffectMode.SILHOUETTE
			|| this.mode == DeathEffectMode.MORPH && this.morphElevationEnabled
			|| this.mode == DeathEffectMode.PLAYER_GHOST && this.movementMode == GhostMovementMode.RISING;
	}

	private float getFade(float progress, float elapsedTicks) {
		return switch (this.mode) {
			case PLAYER_GHOST -> 1.0F - progress;
			case RAGDOLL -> this.getFaintFade();
			case KIDS -> 1.0F;
			case MORPH -> morphFade(progress);
			case SILHOUETTE -> silhouetteFade(progress);
		};
	}

	private float getFaintYOffset(float elapsedTicks) {
		this.resolveFaintSurface();
		float fall = MathHelper.clamp(elapsedTicks / (float)this.faintFallDurationTicks, 0.0F, 1.0F);
		return MathHelper.lerp(easeInCubic(fall), FAINT_START_HEIGHT, this.faintSurfaceOffsetY)
			+ smoothStep(fall) * FAINT_GROUND_CONTACT_OFFSET * this.pose.state().baseScale;
	}

	private float getFaintFade() {
		if (this.faintFadeTicks <= 0) {
			return 1.0F;
		}

		return 1.0F - MathHelper.clamp(this.faintFadeTicks / (float)FAINT_FADE_TICKS, 0.0F, 1.0F);
	}

	private float getRagdollFadeProgress(float elapsedTicks) {
		float fadeWindowTicks = Math.min(this.durationTicks, this.ragdollFadeDurationTicks);
		float fadeStartTicks = this.durationTicks - fadeWindowTicks;
		return MathHelper.clamp((elapsedTicks - fadeStartTicks) / fadeWindowTicks, 0.0F, 1.0F);
	}

	private void renderSilhouette(MatrixStack matrices, VertexConsumerProvider consumers, int alpha) {
		int red = this.color >> 16 & 0xFF;
		int green = this.color >> 8 & 0xFF;
		int blue = this.color & 0xFF;
		VertexConsumer vertices = new FixedColorVertexConsumer(
			consumers.getBuffer(RenderLayers.entityTranslucentEmissive(getWhiteTexture(), false)),
			ColorHelper.getArgb(alpha, red, green, blue)
		);
		matrices.push();
		matrices.scale(this.scale, this.scale, this.scale);
		this.renderBasePlayerModel(matrices, vertices, -1);
		matrices.pop();
	}

	private void renderPlayerGhost(MatrixStack matrices, VertexConsumerProvider consumers, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraState, int alpha) {
		PlayerEntityRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderLayers.entityTranslucent(state.skinTextures.body().texturePath(), false));
		this.renderBasePlayerModel(matrices, skinVertices, ColorHelper.getArgb(alpha, 255, 255, 255));

		if (!this.renderGhostFeatures) {
			return;
		}

		this.renderVanillaFeaturePass(matrices, commandQueue, cameraState, state, alpha);
	}

	private void renderRagdoll(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float progress, float elapsedTicks) {
		PlayerEntityRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderLayers.entityTranslucent(state.skinTextures.body().texturePath(), false));
		this.renderFaintModel(matrices, skinVertices, ColorHelper.getArgb(alpha, 255, 255, 255), progress, elapsedTicks);
	}

	private void renderMorph(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraState, int alpha, float tickDelta) {
		if (this.morphEntity == null) {
			return;
		}

		this.morphEntity.age = this.ageTicks;
		EntityRenderState renderState = MinecraftClient.getInstance().getEntityRenderDispatcher().getAndUpdateRenderState(this.morphEntity, tickDelta);
		OrderedRenderCommandQueue alphaQueue = alpha >= 255 ? commandQueue : new AlphaOrderedRenderCommandQueue(commandQueue, alpha / 255.0F);
		MinecraftClient.getInstance().getEntityRenderDispatcher().render(
			renderState,
			cameraState,
			0.0,
			0.0,
			0.0,
			matrices,
			alphaQueue
		);
	}

	private void renderRagdollBaseModel(MatrixStack matrices, VertexConsumer vertices, int argb, float progress, float elapsedTicks) {
		if (this.ragdollBody == null) {
			return;
		}

		matrices.push();
		this.ragdollBody.render(matrices, vertices, argb, this.pose.slim(), elapsedTicks - (float)Math.floor(elapsedTicks));
		matrices.pop();
	}

	private void renderFaintModel(MatrixStack matrices, VertexConsumer vertices, int argb, float progress, float elapsedTicks) {
		PlayerEntityRenderState state = this.pose.state();
		this.model.setAngles(state);
		float fall = MathHelper.clamp(elapsedTicks / (float)this.faintFallDurationTicks, 0.0F, 1.0F);
		float settle = MathHelper.clamp((elapsedTicks - this.faintFallDurationTicks) / (float)FAINT_SETTLE_TICKS, 0.0F, 1.0F);
		float crawlAmount = this.getFaintCrawlAmount(elapsedTicks);
		float crawlCycle = crawlAmount > 0.0F && !this.faintReachedTarget
			? MathHelper.sin(elapsedTicks * (0.34F + this.faintCrawlSpeed / 260.0F))
			: 0.0F;
		this.applyFaintPose(state, fall, settle, crawlCycle, crawlAmount);
		showPlayerModelParts(this.model);

		matrices.push();
		matrices.scale(state.baseScale, state.baseScale, state.baseScale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - this.getFaintBodyYaw(state, crawlAmount)));
		float bodyPitch = this.faintAnimationType == FaintAnimationType.CRAWL
			? MathHelper.lerp(crawlAmount, FAINT_FALL_PITCH, FAINT_CRAWL_PITCH)
			: FAINT_FALL_PITCH;
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(easeOutCubic(fall), 10.0F, bodyPitch)));
		float settledRoll = MathHelper.lerp(smoothStep(settle), 0.0F, 24.0F);
		float crawlRoll = crawlCycle * 3.0F;
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.faintAnimationType == FaintAnimationType.CRAWL ? MathHelper.lerp(crawlAmount, settledRoll, crawlRoll) : settledRoll));
		matrices.translate(0.0F, -0.07F * smoothStep(settle) - 0.04F * crawlAmount, 0.0F);
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		this.model.render(matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argb);
		matrices.pop();
	}

	private float getFaintCrawlAmount(float elapsedTicks) {
		if (this.faintAnimationType != FaintAnimationType.CRAWL) {
			return 0.0F;
		}

		float crawlPrepareTicks = this.faintFallDurationTicks + FAINT_SETTLE_TICKS + FAINT_CRAWL_GROUND_HOLD_TICKS;
		return smoothStep(MathHelper.clamp((elapsedTicks - crawlPrepareTicks) / (float)FAINT_CRAWL_PREP_TICKS, 0.0F, 1.0F));
	}

	private float getFaintBodyYaw(PlayerEntityRenderState state, float crawlAmount) {
		if (this.faintAnimationType != FaintAnimationType.CRAWL || Float.isNaN(this.faintCrawlYaw)) {
			return state.bodyYaw;
		}

		return MathHelper.lerpAngleDegrees(crawlAmount, state.bodyYaw, this.faintCrawlYaw);
	}

	private void applyFaintPose(PlayerEntityRenderState state, float fall, float settle, float crawlCycle, float crawlAmount) {
		float limp = smoothStep(fall);
		float grounded = smoothStep(settle);
		float rightPull = (crawlCycle + 1.0F) * 0.5F;
		float leftPull = 1.0F - rightPull;
		float bodySway = crawlCycle * crawlAmount;

		this.model.head.pitch = MathHelper.lerp(grounded, 0.24F * limp, MathHelper.lerp(crawlAmount, 0.34F, -0.18F));
		this.model.head.yaw = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, -0.42F, bodySway * 0.10F));
		this.model.head.roll = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, 0.10F, -bodySway * 0.05F));
		this.model.body.pitch = MathHelper.lerp(grounded, 0.10F * limp, MathHelper.lerp(crawlAmount, 0.03F, -0.08F + Math.abs(crawlCycle) * 0.04F));
		this.model.body.roll = MathHelper.lerp(grounded, 0.0F, MathHelper.lerp(crawlAmount, -0.04F, bodySway * 0.07F));
		this.model.rightArm.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.70F + limp * 0.25F, 1.20F), -1.20F + rightPull * 0.58F);
		this.model.rightArm.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.16F, -0.48F), -0.58F + rightPull * 0.28F);
		this.model.rightArm.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.12F, 0.26F), 0.44F - rightPull * 0.20F);
		this.model.leftArm.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.68F + limp * 0.24F, 1.16F), -1.20F + leftPull * 0.58F);
		this.model.leftArm.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.16F, 0.46F), 0.58F - leftPull * 0.28F);
		this.model.leftArm.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.12F, -0.25F), -0.44F + leftPull * 0.20F);
		this.model.rightLeg.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.08F, 0.10F), 0.32F - rightPull * 0.12F);
		this.model.rightLeg.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.06F, 0.18F), 0.16F + rightPull * 0.12F);
		this.model.rightLeg.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.03F, 0.08F), 0.05F);
		this.model.leftLeg.pitch = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, 0.08F, -0.08F), 0.32F - leftPull * 0.12F);
		this.model.leftLeg.yaw = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.06F, -0.16F), -0.16F - leftPull * 0.12F);
		this.model.leftLeg.roll = MathHelper.lerp(crawlAmount, MathHelper.lerp(grounded, -0.03F, -0.07F), -0.05F);
		resetPlayerOverlayTransforms(this.model);
	}

	private static void drawTexturedCuboid(MatrixStack matrices, VertexConsumer vertices, int argb, float width, float height, float depth, float u0, float v0, float u1, float v1) {
		MatrixStack.Entry entry = matrices.peek();
		float minX = -width * 0.5F;
		float maxX = width * 0.5F;
		float minY = -height * 0.5F;
		float maxY = height * 0.5F;
		float minZ = -depth * 0.5F;
		float maxZ = depth * 0.5F;
		float minU = u0 / SKIN_TEXTURE_SIZE;
		float minV = v0 / SKIN_TEXTURE_SIZE;
		float maxU = u1 / SKIN_TEXTURE_SIZE;
		float maxV = v1 / SKIN_TEXTURE_SIZE;

		emitQuad(vertices, entry, argb, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minU, minV, maxU, maxV, 0.0F, 0.0F, -1.0F);
		emitQuad(vertices, entry, argb, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, minU, minV, maxU, maxV, 0.0F, 0.0F, 1.0F);
		emitQuad(vertices, entry, argb, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minU, minV, maxU, maxV, -1.0F, 0.0F, 0.0F);
		emitQuad(vertices, entry, argb, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minU, minV, maxU, maxV, 1.0F, 0.0F, 0.0F);
		emitQuad(vertices, entry, argb, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, minU, minV, maxU, maxV, 0.0F, 1.0F, 0.0F);
		emitQuad(vertices, entry, argb, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, minU, minV, maxU, maxV, 0.0F, -1.0F, 0.0F);
	}

	private static void emitQuad(
		VertexConsumer vertices,
		MatrixStack.Entry entry,
		int argb,
		float x1,
		float y1,
		float z1,
		float x2,
		float y2,
		float z2,
		float x3,
		float y3,
		float z3,
		float x4,
		float y4,
		float z4,
		float minU,
		float minV,
		float maxU,
		float maxV,
		float normalX,
		float normalY,
		float normalZ
	) {
		emitVertex(vertices, entry, argb, x1, y1, z1, maxU, maxV, normalX, normalY, normalZ);
		emitVertex(vertices, entry, argb, x2, y2, z2, minU, maxV, normalX, normalY, normalZ);
		emitVertex(vertices, entry, argb, x3, y3, z3, minU, minV, normalX, normalY, normalZ);
		emitVertex(vertices, entry, argb, x4, y4, z4, maxU, minV, normalX, normalY, normalZ);
	}

	private static void emitVertex(VertexConsumer vertices, MatrixStack.Entry entry, int argb, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
		vertices.vertex(entry, x, y, z)
			.color(ColorHelper.getRed(argb), ColorHelper.getGreen(argb), ColorHelper.getBlue(argb), ColorHelper.getAlpha(argb))
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
			.normal(entry, normalX, normalY, normalZ);
	}

	private void renderVanillaFeaturePass(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraState, PlayerEntityRenderState state, int alpha) {
		if (commandQueue == null || cameraState == null) {
			return;
		}

		EntityRenderer<?, ? super PlayerEntityRenderState> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(state);
		if (!(renderer instanceof PlayerEntityRenderer playerRenderer)) {
			return;
		}

		boolean invisible = state.invisible;
		boolean invisibleToPlayer = state.invisibleToPlayer;
		int outlineColor = state.outlineColor;
		RenderLayer suppressedBodyLayer = RenderLayers.itemEntityTranslucentCull(state.skinTextures.body().texturePath());
		OrderedRenderCommandQueue alphaQueue = new AlphaOrderedRenderCommandQueue(commandQueue, alpha / 255.0F, suppressedBodyLayer);

		state.invisible = true;
		state.invisibleToPlayer = false;
		state.outlineColor = EntityRenderState.NO_OUTLINE;

		try {
			playerRenderer.render(state, matrices, alphaQueue, cameraState);
		} finally {
			state.invisible = invisible;
			state.invisibleToPlayer = invisibleToPlayer;
			state.outlineColor = outlineColor;
		}
	}

	private void renderBasePlayerModel(MatrixStack matrices, VertexConsumer vertices, int argb) {
		PlayerEntityRenderState state = this.pose.state();
		this.model.setAngles(state);

		matrices.push();
		if (state.isInSneakingPose) {
			matrices.translate(0.0F, state.baseScale * -2.0F / 16.0F, 0.0F);
		}

		matrices.scale(state.baseScale, state.baseScale, state.baseScale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - state.bodyYaw));
		if (state.usingRiptide) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F - state.pitch));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.age * -75.0F));
		} else if (state.flipUpsideDown) {
			matrices.translate(0.0F, (state.height + 0.1F) / state.baseScale, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		}

		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		this.model.render(matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argb);
		matrices.pop();
	}

	private static float silhouetteFade(float progress) {
		if (progress < 0.62F) {
			return 1.0F;
		}

		return 1.0F - MathHelper.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F);
	}

	private static float morphFade(float progress) {
		return 1.0F - smoothStep(progress);
	}

	private static float smoothStep(float value) {
		float progress = MathHelper.clamp(value, 0.0F, 1.0F);
		return progress * progress * (3.0F - 2.0F * progress);
	}

	private static float easeOutCubic(float progress) {
		float inverse = 1.0F - progress;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float easeInCubic(float progress) {
		float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
		return clamped * clamped * clamped;
	}

	private static void resetPlayerOverlayTransforms(PlayerEntityModel model) {
		model.hat.resetTransform();
		model.leftSleeve.resetTransform();
		model.rightSleeve.resetTransform();
		model.leftPants.resetTransform();
		model.rightPants.resetTransform();
		model.jacket.resetTransform();
	}

	private static void showPlayerModelParts(PlayerEntityModel model) {
		model.head.visible = true;
		model.hat.visible = true;
		model.body.visible = true;
		model.jacket.visible = true;
		model.rightArm.visible = true;
		model.rightSleeve.visible = true;
		model.leftArm.visible = true;
		model.leftSleeve.visible = true;
		model.rightLeg.visible = true;
		model.rightPants.visible = true;
		model.leftLeg.visible = true;
		model.leftPants.visible = true;
	}

	private static float randomSigned(int seed, int salt) {
		int value = seed ^ salt * 0x45D9F3B;
		value ^= value >>> 16;
		value *= 0x45D9F3B;
		value ^= value >>> 16;
		return (value & 0xFFFF) / 32767.5F - 1.0F;
	}

	private static Vec3d entityPosition(Entity entity) {
		return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
	}

	private static int stateSeed(PlayerEntityRenderState state) {
		int nameSeed = state.playerName == null ? 0 : state.playerName.getString().hashCode();
		return nameSeed ^ state.id * 31;
	}

	private static Identifier getWhiteTexture() {
		if (whiteTexture == null) {
			whiteTexture = new NativeImageBackedTexture("KoHs Death Effects Solid White", 2, 2, false);
			MinecraftClient.getInstance().getTextureManager().registerTexture(WHITE_TEXTURE, whiteTexture);
			NativeImage image = whiteTexture.getImage();
			image.fillRect(0, 0, 2, 2, 0xFFFFFFFF);
			whiteTexture.upload();
		}

		return WHITE_TEXTURE;
	}

	private static final class RagdollBody {
		private static final int HEAD = 0;
		private static final int NECK = 1;
		private static final int CHEST = 2;
		private static final int RIGHT_SHOULDER = 3;
		private static final int LEFT_SHOULDER = 4;
		private static final int RIGHT_ARM = 5;
		private static final int LEFT_ARM = 6;
		private static final int PIVOT = 7;
		private static final int RIGHT_HIP = 8;
		private static final int LEFT_HIP = 9;
		private static final int RIGHT_KNEE = 10;
		private static final int LEFT_KNEE = 11;
		private static final int RIGHT_LEG = 12;
		private static final int LEFT_LEG = 13;
		private static final double GRAVITY = 0.052;
		private static final double FLOOR_Y = 0.035;
		private static final double AIR_DAMPING = 0.985;
		private static final double FLOOR_FRICTION = 0.62;
		private static final int CONSTRAINT_ITERATIONS = 8;

		private final RagdollNode[] nodes;
		private final RagdollJoint[] joints;

		private RagdollBody(RagdollNode[] nodes, RagdollJoint[] joints) {
			this.nodes = nodes;
			this.joints = joints;
		}

		private static RagdollBody create(PoseSnapshot pose, RagdollShape shape, Vec3d explosionImpulse) {
			PlayerEntityRenderState state = pose.state();
			Vec3d inheritedVelocity = pose.velocity().multiply(0.16, 0.03, 0.16);
			float yaw = 180.0F - shape.yawDegrees();
			int seed = stateSeed(state) ^ Float.floatToIntBits(shape.phase());
			Vec3d forward = rotateY(new Vec3d(0.0, 0.0, 1.0), yaw);
			Vec3d side = rotateY(new Vec3d(1.0, 0.0, 0.0), yaw);

			RagdollNode[] nodes = new RagdollNode[] {
				node(0.0, 2.0, 0.0, 1.5, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 0),
				node(0.0, 1.5, -0.1, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 1),
				node(0.0, 1.125, 0.0, 1.5, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 2),
				node(-0.3125, 1.5, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 3),
				node(0.3125, 1.5, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 4),
				node(-0.4125, 0.75, 0.0, 0.9, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 5),
				node(0.4125, 0.75, 0.0, 0.9, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 6),
				node(0.0, 0.75, 0.0, 3.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 7),
				node(-0.11875, 0.75, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 8),
				node(0.11875, 0.75, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 9),
				node(-0.11875, 0.375, 0.0, 0.8, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 10),
				node(0.11875, 0.375, 0.0, 0.8, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 11),
				node(-0.125, 0.1, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 12),
				node(0.125, 0.1, 0.0, 1.0, yaw, inheritedVelocity, explosionImpulse, forward, side, shape, seed, 13)
			};

			return new RagdollBody(nodes, new RagdollJoint[] {
				joint(RIGHT_HIP, RIGHT_KNEE, 0.375, 1.0),
				joint(RIGHT_KNEE, RIGHT_LEG, 0.375, 1.0),
				joint(LEFT_HIP, LEFT_KNEE, 0.375, 1.0),
				joint(LEFT_KNEE, LEFT_LEG, 0.375, 1.0),
				joint(PIVOT, CHEST, 0.375, 0.95),
				joint(CHEST, NECK, 0.39, 1.0),
				joint(RIGHT_ARM, RIGHT_SHOULDER, 0.75, 0.98),
				joint(LEFT_ARM, LEFT_SHOULDER, 0.75, 0.98),
				joint(RIGHT_HIP, PIVOT, 0.12, 1.0),
				joint(LEFT_HIP, PIVOT, 0.12, 1.0),
				joint(CHEST, RIGHT_SHOULDER, 0.315, 1.0),
				joint(CHEST, LEFT_SHOULDER, 0.315, 1.0),
				joint(RIGHT_HIP, LEFT_HIP, 0.24, 1.0),
				joint(RIGHT_SHOULDER, LEFT_SHOULDER, 0.625, 1.0),
				joint(PIVOT, NECK, 0.76, 0.9),
				joint(PIVOT, HEAD, 1.25, 0.48),
				joint(HEAD, NECK, 0.50, 0.92),
				joint(RIGHT_SHOULDER, LEFT_HIP, 0.775, 0.68),
				joint(LEFT_SHOULDER, RIGHT_HIP, 0.775, 0.68),
				joint(RIGHT_SHOULDER, RIGHT_KNEE, 0.875, 0.28),
				joint(LEFT_SHOULDER, LEFT_KNEE, 0.875, 0.28),
				joint(CHEST, RIGHT_HIP, 0.44, 0.62),
				joint(CHEST, LEFT_HIP, 0.44, 0.62),
				joint(RIGHT_HIP, LEFT_KNEE, 0.625, 0.2),
				joint(LEFT_HIP, RIGHT_KNEE, 0.625, 0.2)
			});
		}

		private static RagdollNode node(
			double x,
			double y,
			double z,
			double mass,
			float yaw,
			Vec3d inheritedVelocity,
			Vec3d explosionImpulse,
			Vec3d forward,
			Vec3d side,
			RagdollShape shape,
			int seed,
			int index
		) {
			Vec3d position = rotateY(new Vec3d(x, y, z), yaw);
			double heightFactor = MathHelper.clamp((float)(y / 2.0), 0.0F, 1.0F);
			Vec3d center = rotateY(new Vec3d(0.0, 0.85, 0.0), yaw);
			Vec3d radial = position.subtract(center);
			Vec3d spinDirection = radial.horizontalLengthSquared() > 0.0001
				? new Vec3d(-radial.z, 0.0, radial.x).normalize()
				: side;
			double fallSign = shape.faceDown() ? 1.0 : -0.65;
			Vec3d fallVelocity = forward.multiply(0.055 * fallSign * heightFactor)
				.add(0.0, -0.018 * heightFactor, 0.0);
			Vec3d spinVelocity = spinDirection.multiply(shape.initialSpinVelocity() * 0.045 * heightFactor);
			Vec3d looseVelocity = side.multiply(randomSigned(seed, 30 + index) * 0.018 * (0.35 + heightFactor));
			Vec3d explosiveVelocity = explosionImpulse.multiply(0.65 + heightFactor * 0.65 + randomSigned(seed, 60 + index) * 0.2)
				.add(side.multiply(randomSigned(seed, 80 + index) * explosionImpulse.length() * 0.28));
			Vec3d velocity = inheritedVelocity.add(fallVelocity).add(spinVelocity).add(looseVelocity).add(explosiveVelocity);
			return new RagdollNode(position, position.subtract(velocity), 1.0 / mass);
		}

		private static RagdollJoint joint(int first, int second, double distance, double stiffness) {
			return new RagdollJoint(first, second, distance, stiffness);
		}

		private void tick() {
			for (RagdollNode node : this.nodes) {
				Vec3d velocity = node.position.subtract(node.previous).multiply(AIR_DAMPING);
				node.previous = node.position;
				node.position = node.position.add(velocity).add(0.0, -GRAVITY, 0.0);
				this.collideFloor(node);
			}

			for (int iteration = 0; iteration < CONSTRAINT_ITERATIONS; iteration++) {
				for (RagdollJoint joint : this.joints) {
					this.solve(joint);
				}

				for (RagdollNode node : this.nodes) {
					this.collideFloor(node);
				}
			}
		}

		private void solve(RagdollJoint joint) {
			RagdollNode first = this.nodes[joint.first()];
			RagdollNode second = this.nodes[joint.second()];
			Vec3d delta = second.position.subtract(first.position);
			double length = delta.length();
			if (length < 0.0001) {
				return;
			}

			double difference = (length - joint.distance()) / length;
			double totalInverseMass = first.inverseMass + second.inverseMass;
			if (totalInverseMass <= 0.0) {
				return;
			}

			Vec3d correction = delta.multiply(difference * joint.stiffness());
			first.position = first.position.add(correction.multiply(first.inverseMass / totalInverseMass));
			second.position = second.position.subtract(correction.multiply(second.inverseMass / totalInverseMass));
		}

		private void collideFloor(RagdollNode node) {
			if (node.position.y >= FLOOR_Y) {
				return;
			}

			Vec3d velocity = node.position.subtract(node.previous);
			node.position = new Vec3d(node.position.x, FLOOR_Y, node.position.z);
			node.previous = new Vec3d(
				node.position.x - velocity.x * FLOOR_FRICTION,
				FLOOR_Y + Math.abs(velocity.y) * 0.08,
				node.position.z - velocity.z * FLOOR_FRICTION
			);
		}

		private void render(MatrixStack matrices, VertexConsumer vertices, int argb, boolean slim, float tickDelta) {
			float armWidth = slim ? 0.1875F : 0.25F;
			this.renderLayeredSegment(matrices, vertices, argb, PIVOT, NECK, 0.50F, 0.25F, 20.0F, 20.0F, 28.0F, 32.0F, 20.0F, 36.0F, 28.0F, 48.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, NECK, HEAD, 0.50F, 0.50F, 8.0F, 8.0F, 16.0F, 16.0F, 40.0F, 8.0F, 48.0F, 16.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, RIGHT_SHOULDER, RIGHT_ARM, armWidth, 0.25F, 44.0F, 20.0F, 48.0F, 32.0F, 44.0F, 36.0F, 48.0F, 48.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, LEFT_SHOULDER, LEFT_ARM, armWidth, 0.25F, 36.0F, 52.0F, 40.0F, 64.0F, 52.0F, 52.0F, 56.0F, 64.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, RIGHT_HIP, RIGHT_KNEE, 0.25F, 0.25F, 4.0F, 20.0F, 8.0F, 26.0F, 4.0F, 36.0F, 8.0F, 42.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, RIGHT_KNEE, RIGHT_LEG, 0.25F, 0.25F, 4.0F, 26.0F, 8.0F, 32.0F, 4.0F, 42.0F, 8.0F, 48.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, LEFT_HIP, LEFT_KNEE, 0.25F, 0.25F, 20.0F, 52.0F, 24.0F, 58.0F, 4.0F, 52.0F, 8.0F, 58.0F, tickDelta);
			this.renderLayeredSegment(matrices, vertices, argb, LEFT_KNEE, LEFT_LEG, 0.25F, 0.25F, 20.0F, 58.0F, 24.0F, 64.0F, 4.0F, 58.0F, 8.0F, 64.0F, tickDelta);
		}

		private void applyClientPush(Vec3d direction, Vec3d playerVelocity, double strength) {
			Vec3d impulse = direction.multiply(0.048 * strength).add(playerVelocity.multiply(0.12));
			for (RagdollNode node : this.nodes) {
				double heightFactor = MathHelper.clamp((float)(node.position.y / 1.8), 0.0F, 1.0F);
				node.previous = node.previous.subtract(impulse.multiply(0.35 + heightFactor * 0.65));
			}
		}

		private void renderSegment(
			MatrixStack matrices,
			VertexConsumer vertices,
			int argb,
			int first,
			int second,
			float width,
			float depth,
			float u0,
			float v0,
			float u1,
			float v1,
			float tickDelta
		) {
			Vec3d from = this.lerp(first, tickDelta);
			Vec3d to = this.lerp(second, tickDelta);
			Vec3d delta = to.subtract(from);
			float length = (float)delta.length();
			if (length < 0.001F) {
				return;
			}

			Vector3f direction = new Vector3f((float)delta.x, (float)delta.y, (float)delta.z).normalize();
			Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
			Vec3d center = from.add(delta.multiply(0.5));

			matrices.push();
			matrices.translate(center.x, center.y, center.z);
			matrices.multiply(rotation);
			drawTexturedCuboid(matrices, vertices, argb, width, length, depth, u0, v0, u1, v1);
			matrices.pop();
		}

		private void renderLayeredSegment(
			MatrixStack matrices,
			VertexConsumer vertices,
			int argb,
			int first,
			int second,
			float width,
			float depth,
			float baseU0,
			float baseV0,
			float baseU1,
			float baseV1,
			float overlayU0,
			float overlayV0,
			float overlayU1,
			float overlayV1,
			float tickDelta
		) {
			this.renderSegment(matrices, vertices, argb, first, second, width, depth, baseU0, baseV0, baseU1, baseV1, tickDelta);
			this.renderSegment(matrices, vertices, argb, first, second, width + 0.035F, depth + 0.035F, overlayU0, overlayV0, overlayU1, overlayV1, tickDelta);
		}

		private Vec3d lerp(int index, float tickDelta) {
			RagdollNode node = this.nodes[index];
			return node.previous.lerp(node.position, tickDelta);
		}

		private static Vec3d rotateY(Vec3d vector, float degrees) {
			double radians = Math.toRadians(degrees);
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			return new Vec3d(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
		}

		private static final class RagdollNode {
			private Vec3d position;
			private Vec3d previous;
			private final double inverseMass;

			private RagdollNode(Vec3d position, Vec3d previous, double inverseMass) {
				this.position = position;
				this.previous = previous;
				this.inverseMass = inverseMass;
			}
		}

		private record RagdollJoint(int first, int second, double distance, double stiffness) {
		}
	}

	private record RagdollShape(
		float yawDegrees,
		boolean faceDown,
		float phase,
		float initialSpinVelocity
	) {
		private static RagdollShape from(PoseSnapshot pose) {
			PlayerEntityRenderState state = pose.state();
			Vec3d velocity = pose.velocity();
			float speed = (float)velocity.horizontalLength();
			int seed = stateSeed(state)
				^ state.id * 31
				^ Float.floatToIntBits(state.bodyYaw)
				^ Float.floatToIntBits((float)velocity.x)
				^ Float.floatToIntBits((float)velocity.z);
			float velocityYaw = speed > 0.035F ? (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : state.bodyYaw;
			float yawInfluence = MathHelper.clamp(speed * 2.75F, 0.0F, 1.0F);
			float yaw = MathHelper.lerp(yawInfluence, state.bodyYaw, velocityYaw) + randomSigned(seed, 1) * 22.0F;
			boolean faceDown = randomSigned(seed, 2) > -0.25F;
			float phase = (randomSigned(seed, 5) + 1.0F) * (float)Math.PI;
			float spinVelocity = randomSigned(seed, 8) * 0.28F + speed * randomSigned(seed, 9) * 0.55F;

			return new RagdollShape(yaw, faceDown, phase, spinVelocity);
		}
	}

	private record PoseSnapshot(PlayerEntityRenderState state, boolean slim, Vec3d velocity) {
		private static PoseSnapshot from(PlayerEntity player, KohsDeathEffectsConfig config) {
			PlayerEntityRenderState state = new PlayerEntityRenderState();
			boolean slim = false;

			if (player instanceof AbstractClientPlayerEntity clientPlayer) {
				EntityRenderer<? super AbstractClientPlayerEntity, ?> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(clientPlayer);
				if (renderer instanceof PlayerEntityRenderer playerRenderer) {
					playerRenderer.updateRenderState(clientPlayer, state, 1.0F);
					slim = state.skinTextures.model() == PlayerSkinType.SLIM;
					prepareForEffect(state, config);
					return new PoseSnapshot(state, slim, player.getVelocity());
				}
			}

			captureFallback(player, state);
			prepareForEffect(state, config);
			return new PoseSnapshot(state, slim, player.getVelocity());
		}

		private static void captureFallback(PlayerEntity player, PlayerEntityRenderState state) {
			state.entityType = player.getType();
			state.x = player.getX();
			state.y = player.getY();
			state.z = player.getZ();
			state.age = player.age;
			state.width = player.getWidth();
			state.height = player.getHeight();
			state.standingEyeHeight = player.getStandingEyeHeight();
			state.bodyYaw = player.getBodyYaw();
			state.relativeHeadYaw = MathHelper.wrapDegrees(player.getHeadYaw() - player.getBodyYaw());
			state.pitch = player.getPitch();
			state.baseScale = player.getScale();
			state.ageScale = player.getScaleFactor();
			state.mainArm = player.getMainArm();
			state.preferredArm = player.preferredHand == Hand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
			state.activeHand = player.getActiveHand();
			state.isUsingItem = player.isUsingItem();
			state.isInSneakingPose = player.isInSneakingPose();
			state.handSwingProgress = player.getHandSwingProgress(1.0F);
			state.leftArmPose = fallbackArmPose(player, Arm.LEFT);
			state.rightArmPose = fallbackArmPose(player, Arm.RIGHT);
		}

		private static BipedEntityModel.ArmPose fallbackArmPose(PlayerEntity player, Arm arm) {
			if (player.isUsingItem() && getArmForHand(player, player.getActiveHand()) == arm) {
				return BipedEntityModel.ArmPose.ITEM;
			}

			if (player.handSwinging && player.getMainArm() == arm) {
				return BipedEntityModel.ArmPose.ITEM;
			}

			return player.getStackInArm(arm).isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		}

		private static Arm getArmForHand(PlayerEntity player, Hand hand) {
			Arm mainArm = player.getMainArm();
			return hand == Hand.MAIN_HAND ? mainArm : mainArm.getOpposite();
		}

		private static void prepareForEffect(PlayerEntityRenderState state, KohsDeathEffectsConfig config) {
			state.deathTime = 0.0F;
			state.hurt = false;
			state.invisible = false;
			state.invisibleToPlayer = false;
			state.outlineColor = EntityRenderState.NO_OUTLINE;
			state.spectator = false;
			state.onFire = false;
			state.displayName = null;
			state.playerName = null;
			state.nameLabelPos = null;
			state.leashDatas = null;

			if (config.deathEffectMode == DeathEffectMode.SILHOUETTE) {
				hideSkinLayers(state);
				return;
			}

			showSkinLayers(state);

			if (config.deathEffectMode == DeathEffectMode.PLAYER_GHOST) {
				if (!config.playerGhostArmorEnabled) {
					state.equippedHeadStack = ItemStack.EMPTY;
					state.equippedChestStack = ItemStack.EMPTY;
					state.equippedLegsStack = ItemStack.EMPTY;
					state.equippedFeetStack = ItemStack.EMPTY;
				}

				if (!config.playerGhostHeldItemsEnabled) {
					state.rightHandItemState.clear();
					state.leftHandItemState.clear();
					state.spyglassState.clear();
				}
			}
		}

		private static void hideSkinLayers(PlayerEntityRenderState state) {
			state.hatVisible = false;
			state.jacketVisible = false;
			state.leftPantsLegVisible = false;
			state.rightPantsLegVisible = false;
			state.leftSleeveVisible = false;
			state.rightSleeveVisible = false;
			state.capeVisible = false;
		}

		private static void showSkinLayers(PlayerEntityRenderState state) {
			state.hatVisible = true;
			state.jacketVisible = true;
			state.leftPantsLegVisible = true;
			state.rightPantsLegVisible = true;
			state.leftSleeveVisible = true;
			state.rightSleeveVisible = true;
		}

		private PlayerEntityModel createModel() {
			MinecraftClient client = MinecraftClient.getInstance();
			return new PlayerEntityModel(
				client.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER),
				this.slim
			);
		}
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
		public VertexConsumer color(int color) {
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

		@Override
		public VertexConsumer lineWidth(float width) {
			this.delegate.lineWidth(width);
			return this;
		}

		@Override
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
			this.delegate.vertex(x, y, z, this.color, u, v, overlay, light, normalX, normalY, normalZ);
		}
	}

	private static class AlphaRenderCommandQueue implements RenderCommandQueue {
		protected final RenderCommandQueue delegate;
		protected final float alphaMultiplier;
		protected final RenderLayer suppressedLayer;

		private AlphaRenderCommandQueue(RenderCommandQueue delegate, float alphaMultiplier) {
			this(delegate, alphaMultiplier, null);
		}

		private AlphaRenderCommandQueue(RenderCommandQueue delegate, float alphaMultiplier, RenderLayer suppressedLayer) {
			this.delegate = delegate;
			this.alphaMultiplier = MathHelper.clamp(alphaMultiplier, 0.0F, 1.0F);
			this.suppressedLayer = suppressedLayer;
		}

		@Override
		public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces) {
			this.delegate.submitShadowPieces(matrices, shadowRadius, shadowPieces);
		}

		@Override
		public void submitLabel(MatrixStack matrices, Vec3d nameLabelPos, int y, Text label, boolean notSneaking, int light, double squaredDistanceToCamera, CameraRenderState cameraState) {
			this.delegate.submitLabel(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState);
		}

		@Override
		public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean dropShadow, TextRenderer.TextLayerType layerType, int light, int color, int backgroundColor, int outlineColor) {
			this.delegate.submitText(matrices, x, y, text, dropShadow, layerType, light, this.fadeColor(color), this.fadeColor(backgroundColor), outlineColor);
		}

		@Override
		public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
			this.delegate.submitFire(matrices, renderState, rotation);
		}

		@Override
		public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) {
			this.delegate.submitLeash(matrices, leashData);
		}

		@Override
		public <S> void submitModel(
			Model<? super S> model,
			S state,
			MatrixStack matrices,
			RenderLayer renderLayer,
			int light,
			int overlay,
			int tintedColor,
			Sprite sprite,
			int outlineColor,
			ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay
		) {
			if (this.isSuppressed(renderLayer)) {
				return;
			}

			this.delegate.submitModel(model, state, matrices, renderLayer, light, overlay, this.fadeColor(tintedColor), sprite, outlineColor, crumblingOverlay);
		}

		@Override
		public void submitModelPart(
			ModelPart part,
			MatrixStack matrices,
			RenderLayer renderLayer,
			int light,
			int overlay,
			Sprite sprite,
			boolean sheeted,
			boolean hasGlint,
			int tintedColor,
			ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
			int outlineColor
		) {
			if (this.isSuppressed(renderLayer)) {
				return;
			}

			this.delegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, this.fadeColor(tintedColor), crumblingOverlay, outlineColor);
		}

		@Override
		public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
			this.delegate.submitBlock(matrices, state, light, overlay, outlineColor);
		}

		@Override
		public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
			this.delegate.submitMovingBlock(matrices, state);
		}

		@Override
		public void submitBlockStateModel(MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor) {
			this.delegate.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
		}

		@Override
		public void submitItem(
			MatrixStack matrices,
			ItemDisplayContext displayContext,
			int light,
			int overlay,
			int outlineColors,
			int[] tintLayers,
			List<BakedQuad> quads,
			RenderLayer renderLayer,
			ItemRenderState.Glint glintType
		) {
			this.delegate.submitItem(matrices, displayContext, light, overlay, outlineColors, this.fadeColors(tintLayers), quads, renderLayer, glintType);
		}

		@Override
		public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, OrderedRenderCommandQueue.Custom customRenderer) {
			this.delegate.submitCustom(matrices, renderLayer, customRenderer);
		}

		@Override
		public void submitCustom(OrderedRenderCommandQueue.LayeredCustom customRenderer) {
			this.delegate.submitCustom(customRenderer);
		}

		private int[] fadeColors(int[] colors) {
			if (colors == null) {
				return null;
			}

			int[] faded = colors.clone();
			for (int i = 0; i < faded.length; i++) {
				faded[i] = this.fadeColor(faded[i]);
			}
			return faded;
		}

		protected int fadeColor(int color) {
			int alpha = MathHelper.clamp((int)(ColorHelper.getAlpha(color) * this.alphaMultiplier), 0, 255);
			return ColorHelper.getArgb(alpha, ColorHelper.getRed(color), ColorHelper.getGreen(color), ColorHelper.getBlue(color));
		}

		protected boolean isSuppressed(RenderLayer layer) {
			return this.suppressedLayer != null && (layer == this.suppressedLayer || layer.equals(this.suppressedLayer));
		}
	}

	private static final class AlphaOrderedRenderCommandQueue extends AlphaRenderCommandQueue implements OrderedRenderCommandQueue {
		private final OrderedRenderCommandQueue orderedDelegate;

		private AlphaOrderedRenderCommandQueue(OrderedRenderCommandQueue delegate, float alphaMultiplier) {
			this(delegate, alphaMultiplier, null);
		}

		private AlphaOrderedRenderCommandQueue(OrderedRenderCommandQueue delegate, float alphaMultiplier, RenderLayer suppressedLayer) {
			super(delegate, alphaMultiplier, suppressedLayer);
			this.orderedDelegate = delegate;
		}

		@Override
		public RenderCommandQueue getBatchingQueue(int order) {
			return new AlphaRenderCommandQueue(this.orderedDelegate.getBatchingQueue(order), this.alphaMultiplier, this.suppressedLayer);
		}
	}

	private static final class AlphaVertexConsumerProvider implements VertexConsumerProvider {
		private final VertexConsumerProvider delegate;
		private final float alphaMultiplier;
		private final RenderLayer suppressedLayer;
		private final boolean remapEntityLayers;

		private AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alphaMultiplier, RenderLayer suppressedLayer) {
			this(delegate, alphaMultiplier, suppressedLayer, false);
		}

		private AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alphaMultiplier, RenderLayer suppressedLayer, boolean remapEntityLayers) {
			this.delegate = delegate;
			this.alphaMultiplier = MathHelper.clamp(alphaMultiplier, 0.0F, 1.0F);
			this.suppressedLayer = suppressedLayer;
			this.remapEntityLayers = remapEntityLayers;
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			if (this.suppressedLayer != null && (layer == this.suppressedLayer || layer.equals(this.suppressedLayer))) {
				return NoopVertexConsumer.INSTANCE;
			}

			return new AlphaVertexConsumer(this.delegate.getBuffer(this.remapLayer(layer)), this.alphaMultiplier);
		}

		private RenderLayer remapLayer(RenderLayer layer) {
			return layer;
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
			this.delegate.color(red, green, blue, this.multiplyAlpha(alpha));
			return this;
		}

		@Override
		public VertexConsumer color(int color) {
			int alpha = this.multiplyAlpha(ColorHelper.getAlpha(color));
			this.delegate.color(ColorHelper.getArgb(alpha, ColorHelper.getRed(color), ColorHelper.getGreen(color), ColorHelper.getBlue(color)));
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

		@Override
		public VertexConsumer lineWidth(float width) {
			this.delegate.lineWidth(width);
			return this;
		}

		@Override
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
			int alpha = this.multiplyAlpha(ColorHelper.getAlpha(color));
			int fadedColor = ColorHelper.getArgb(alpha, ColorHelper.getRed(color), ColorHelper.getGreen(color), ColorHelper.getBlue(color));
			this.delegate.vertex(x, y, z, fadedColor, u, v, overlay, light, normalX, normalY, normalZ);
		}

		private int multiplyAlpha(int alpha) {
			return MathHelper.clamp((int)(alpha * this.alphaMultiplier), 0, 255);
		}
	}

	private static final class NoopVertexConsumer implements VertexConsumer {
		private static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer color(int color) {
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer lineWidth(float width) {
			return this;
		}

		@Override
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
		}
	}
}
