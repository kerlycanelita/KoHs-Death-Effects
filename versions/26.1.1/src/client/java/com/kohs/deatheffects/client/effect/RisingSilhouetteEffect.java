package com.kohs.deatheffects.client.effect;

import java.util.List;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.FaintAnimationType;
import com.kohs.deatheffects.KohsDeathEffectsConfig.GhostMovementMode;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RisingSilhouetteEffect {
	private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "effect/solid_white");
	private static final int FULL_BRIGHT = 0x00F000F0;
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
	private static DynamicTexture whiteTexture;

	private final Vec3 position;
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
	private final PlayerModel model;
	private Vec3 ragdollOffset = Vec3.ZERO;
	private Vec3 ragdollVelocity = Vec3.ZERO;
	private Vec3 faintCrawlOffset = Vec3.ZERO;
	private boolean faintSurfaceResolved;
	private float faintSurfaceOffsetY;
	private int faintFallDurationTicks = 26;
	private int faintFadeTicks;
	private boolean faintReachedTarget;
	private float faintCrawlYaw = Float.NaN;
	private int ageTicks;

	private RisingSilhouetteEffect(
		Vec3 position,
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
		Vec3 ragdollExplosionImpulse,
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
		this.faintCrawlSpeed = Mth.clamp(faintCrawlSpeed, 100, 300);
		this.pose = pose;
		this.ragdollShape = RagdollShape.from(pose);
		this.ragdollBody = null;
		this.model = pose.createModel();
	}

	public static RisingSilhouetteEffect from(Player player, KohsDeathEffectsConfig config) {
		return from(player, config, false, null);
	}

	public static RisingSilhouetteEffect from(Player player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3 explosionPosition) {
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
			Vec3.ZERO,
			PoseSnapshot.from(player, config)
		);
	}

	private static Entity createMorphEntity(Player player, KohsDeathEffectsConfig config) {
		if (config.deathEffectMode != DeathEffectMode.MORPH) {
			return null;
		}

		Entity entity = MorphMobCatalog.createEntity(player.level(), config.morphEntityTypeId);
		if (entity == null) {
			return null;
		}

		entity.tickCount = player.tickCount;
		entity.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		entity.setDeltaMovement(Vec3.ZERO);
		if (entity instanceof LivingEntity livingEntity) {
			livingEntity.yBodyRot = player.getVisualRotationYInDegrees();
			livingEntity.yBodyRotO = player.getVisualRotationYInDegrees();
			livingEntity.yHeadRot = player.getYHeadRot();
			livingEntity.yHeadRotO = player.getYHeadRot();
		}
		return entity;
	}

	private static Vec3 createExplosionImpulse(Player player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3 explosionPosition) {
		if (!explosionDeath || !config.ragdollExplosionImpulseEnabled) {
			return Vec3.ZERO;
		}

		Vec3 center = entityPosition(player).add(0.0, 0.9, 0.0);
		Vec3 origin = explosionPosition == null ? center.subtract(player.getViewVector(1.0F).scale(2.0)) : explosionPosition;
		Vec3 direction = center.subtract(origin);
		if (direction.lengthSqr() < 0.0001) {
			Vec3 velocity = player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
			direction = velocity.lengthSqr() > 0.0001 ? velocity : player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		}

		double distance = Math.max(0.6, direction.length());
		double strength = Mth.clamp(1.4 / distance, 0.45, 1.75);
		Vec3 normalized = direction.normalize();
		return new Vec3(normalized.x * 0.26 * strength, 0.12 * strength + Math.max(0.0, normalized.y) * 0.12, normalized.z * 0.26 * strength);
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

		Minecraft client = Minecraft.getInstance();
		if (this.faintAnimationType == FaintAnimationType.CRAWL && !this.faintReachedTarget && client.player != null) {
			int crawlPrepareTick = impactTick + FAINT_CRAWL_GROUND_HOLD_TICKS;
			if (this.ageTicks < crawlPrepareTick) {
				return;
			}

			Vec3 target = entityPosition(client.player).subtract(this.position);
			Vec3 horizontalTarget = new Vec3(target.x, 0.0, target.z);
			Vec3 delta = horizontalTarget.subtract(this.faintCrawlOffset);
			double distance = delta.length();
			if (distance > 0.0001) {
				float targetYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
				float currentYaw = Float.isNaN(this.faintCrawlYaw) ? this.pose.state().bodyRot : this.faintCrawlYaw;
				this.faintCrawlYaw = Mth.rotLerp(0.2F, currentYaw, targetYaw);
			}

			if (this.ageTicks < crawlPrepareTick + FAINT_CRAWL_PREP_TICKS) {
				return;
			}

			if (distance <= 0.72) {
				this.faintReachedTarget = true;
			} else {
				double step = Math.min(distance, 0.045 * (this.faintCrawlSpeed / 100.0));
				this.faintCrawlOffset = this.faintCrawlOffset.add(delta.normalize().scale(step));
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
		this.faintFallDurationTicks = Mth.clamp((int)(16.0F + Math.sqrt(fallDistance) * 12.0F), 26, 78);
	}

	private void updateFaintCrawlSurface() {
		double x = this.position.x + this.faintCrawlOffset.x;
		double z = this.position.z + this.faintCrawlOffset.z;
		double currentSurfaceY = this.position.y + this.faintSurfaceOffsetY;
		float surfaceOffset = this.findFaintSurfaceOffset(x, z, currentSurfaceY + 1.05);
		if (!Float.isNaN(surfaceOffset)) {
			this.faintSurfaceOffsetY = Mth.lerp(FAINT_SURFACE_FOLLOW_SPEED, this.faintSurfaceOffsetY, surfaceOffset);
		}
	}

	private float findFaintSurfaceOffset(double x, double z, double highestSurfaceY) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return Float.NaN;
		}

		int startY = Mth.floor(highestSurfaceY);
		int minY = Math.max(client.level.getMinY(), startY - 96);
		for (int y = startY; y >= minY; y--) {
			BlockPos blockPos = BlockPos.containing(x, y, z);
			BlockState state = client.level.getBlockState(blockPos);
			VoxelShape shape = state.getCollisionShape(client.level, blockPos);
			if (shape.isEmpty()) {
				continue;
			}

			double top = blockPos.getY() + shape.max(Direction.Axis.Y);
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

	public void render(LevelRenderContext context) {
		PoseStack matrices = context.poseStack();
		MultiBufferSource consumers = context.bufferSource();
		if (matrices == null || consumers == null) {
			return;
		}

		float tickProgress = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float elapsedTicks = this.ageTicks + tickProgress;
		float progress = Mth.clamp(elapsedTicks / (float)this.durationTicks, 0.0F, 1.0F);
		float alphaNow = this.alpha * this.getFade(progress, elapsedTicks);
		if (alphaNow <= 0.01F) {
			return;
		}

		CameraRenderState cameraState = context.levelState().cameraRenderState;
		Vec3 cameraPos = cameraState != null && cameraState.pos != null ? cameraState.pos : Vec3.ZERO;
		int alphaInt = Mth.clamp((int)(alphaNow * 255.0F), 0, 255);
		float rise = this.shouldRise() ? this.riseHeight * easeOutCubic(progress) : 0.0F;

		matrices.pushPose();
		Vec3 renderPosition = this.mode == DeathEffectMode.RAGDOLL
			? this.position.add(this.faintCrawlOffset.x, this.getFaintYOffset(elapsedTicks), this.faintCrawlOffset.z)
			: this.position;
		matrices.translate(renderPosition.x - cameraPos.x, renderPosition.y - cameraPos.y + rise, renderPosition.z - cameraPos.z);

		switch (this.mode) {
			case PLAYER_GHOST -> this.renderPlayerGhost(matrices, consumers, context.submitNodeCollector(), cameraState, alphaInt);
			case RAGDOLL -> this.renderRagdoll(matrices, consumers, alphaInt, progress, elapsedTicks);
			case KIDS -> {
			}
			case MORPH -> this.renderMorph(matrices, context.submitNodeCollector(), cameraState, alphaInt, tickProgress);
			case SILHOUETTE -> this.renderSilhouette(matrices, consumers, alphaInt);
		}

		matrices.popPose();
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
		float fall = Mth.clamp(elapsedTicks / (float)this.faintFallDurationTicks, 0.0F, 1.0F);
		return Mth.lerp(easeInCubic(fall), FAINT_START_HEIGHT, this.faintSurfaceOffsetY)
			+ smoothStep(fall) * FAINT_GROUND_CONTACT_OFFSET * this.pose.state().scale;
	}

	private float getFaintFade() {
		if (this.faintFadeTicks <= 0) {
			return 1.0F;
		}

		return 1.0F - Mth.clamp(this.faintFadeTicks / (float)FAINT_FADE_TICKS, 0.0F, 1.0F);
	}

	private float getRagdollFadeProgress(float elapsedTicks) {
		float fadeWindowTicks = Math.min(this.durationTicks, this.ragdollFadeDurationTicks);
		float fadeStartTicks = this.durationTicks - fadeWindowTicks;
		return Mth.clamp((elapsedTicks - fadeStartTicks) / fadeWindowTicks, 0.0F, 1.0F);
	}

	private void renderSilhouette(PoseStack matrices, MultiBufferSource consumers, int alpha) {
		int red = this.color >> 16 & 0xFF;
		int green = this.color >> 8 & 0xFF;
		int blue = this.color & 0xFF;
		VertexConsumer vertices = new FixedColorVertexConsumer(
			consumers.getBuffer(RenderTypes.entityTranslucentEmissive(getWhiteTexture(), false)),
			ARGB.color(alpha, red, green, blue)
		);
		matrices.pushPose();
		matrices.scale(this.scale, this.scale, this.scale);
		this.renderBasePlayerModel(matrices, vertices, -1);
		matrices.popPose();
	}

	private void renderPlayerGhost(PoseStack matrices, MultiBufferSource consumers, SubmitNodeCollector commandQueue, CameraRenderState cameraState, int alpha) {
		AvatarRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderTypes.entityTranslucent(state.skin.body().texturePath(), false));
		this.renderBasePlayerModel(matrices, skinVertices, ARGB.color(alpha, 255, 255, 255));

		if (!this.renderGhostFeatures) {
			return;
		}

		this.renderVanillaFeaturePass(matrices, commandQueue, cameraState, state, alpha);
	}

	private void renderRagdoll(PoseStack matrices, MultiBufferSource consumers, int alpha, float progress, float elapsedTicks) {
		AvatarRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderTypes.entityTranslucent(state.skin.body().texturePath(), false));
		this.renderFaintModel(matrices, skinVertices, ARGB.color(alpha, 255, 255, 255), progress, elapsedTicks);
	}

	private void renderMorph(PoseStack matrices, SubmitNodeCollector commandQueue, CameraRenderState cameraState, int alpha, float tickDelta) {
		if (this.morphEntity == null) {
			return;
		}

		this.morphEntity.tickCount = this.ageTicks;
		EntityRenderState renderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(this.morphEntity, tickDelta);
		SubmitNodeCollector alphaQueue = alpha >= 255 ? commandQueue : new AlphaSubmitNodeCollector(commandQueue, alpha / 255.0F);
		Minecraft.getInstance().getEntityRenderDispatcher().submit(
			renderState,
			cameraState,
			0.0,
			0.0,
			0.0,
			matrices,
			alphaQueue
		);
	}

	private void renderRagdollBaseModel(PoseStack matrices, VertexConsumer vertices, int argb, float progress, float elapsedTicks) {
		if (this.ragdollBody == null) {
			return;
		}

		matrices.pushPose();
		this.ragdollBody.render(matrices, vertices, argb, this.pose.slim(), elapsedTicks - (float)Math.floor(elapsedTicks));
		matrices.popPose();
	}

	private void renderFaintModel(PoseStack matrices, VertexConsumer vertices, int argb, float progress, float elapsedTicks) {
		AvatarRenderState state = this.pose.state();
		this.model.setupAnim(state);
		float fall = Mth.clamp(elapsedTicks / (float)this.faintFallDurationTicks, 0.0F, 1.0F);
		float settle = Mth.clamp((elapsedTicks - this.faintFallDurationTicks) / (float)FAINT_SETTLE_TICKS, 0.0F, 1.0F);
		float crawlAmount = this.getFaintCrawlAmount(elapsedTicks);
		float crawlCycle = crawlAmount > 0.0F && !this.faintReachedTarget
			? Mth.sin(elapsedTicks * (0.34F + this.faintCrawlSpeed / 260.0F))
			: 0.0F;
		this.applyFaintPose(state, fall, settle, crawlCycle, crawlAmount);
		showPlayerModelParts(this.model);

		matrices.pushPose();
		matrices.scale(state.scale, state.scale, state.scale);
		matrices.mulPose(Axis.YP.rotationDegrees(180.0F - this.getFaintBodyYaw(state, crawlAmount)));
		float bodyPitch = this.faintAnimationType == FaintAnimationType.CRAWL
			? Mth.lerp(crawlAmount, FAINT_FALL_PITCH, FAINT_CRAWL_PITCH)
			: FAINT_FALL_PITCH;
		matrices.mulPose(Axis.XP.rotationDegrees(Mth.lerp(easeOutCubic(fall), 10.0F, bodyPitch)));
		float settledRoll = Mth.lerp(smoothStep(settle), 0.0F, 24.0F);
		float crawlRoll = crawlCycle * 3.0F;
		matrices.mulPose(Axis.ZP.rotationDegrees(this.faintAnimationType == FaintAnimationType.CRAWL ? Mth.lerp(crawlAmount, settledRoll, crawlRoll) : settledRoll));
		matrices.translate(0.0F, -0.07F * smoothStep(settle) - 0.04F * crawlAmount, 0.0F);
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		this.model.renderToBuffer(matrices, vertices, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
		matrices.popPose();
	}

	private float getFaintCrawlAmount(float elapsedTicks) {
		if (this.faintAnimationType != FaintAnimationType.CRAWL) {
			return 0.0F;
		}

		float crawlPrepareTicks = this.faintFallDurationTicks + FAINT_SETTLE_TICKS + FAINT_CRAWL_GROUND_HOLD_TICKS;
		return smoothStep(Mth.clamp((elapsedTicks - crawlPrepareTicks) / (float)FAINT_CRAWL_PREP_TICKS, 0.0F, 1.0F));
	}

	private float getFaintBodyYaw(AvatarRenderState state, float crawlAmount) {
		if (this.faintAnimationType != FaintAnimationType.CRAWL || Float.isNaN(this.faintCrawlYaw)) {
			return state.bodyRot;
		}

		return Mth.rotLerp(crawlAmount, state.bodyRot, this.faintCrawlYaw);
	}

	private void applyFaintPose(AvatarRenderState state, float fall, float settle, float crawlCycle, float crawlAmount) {
		float limp = smoothStep(fall);
		float grounded = smoothStep(settle);
		float rightPull = (crawlCycle + 1.0F) * 0.5F;
		float leftPull = 1.0F - rightPull;
		float bodySway = crawlCycle * crawlAmount;

		this.model.head.xRot = Mth.lerp(grounded, 0.24F * limp, Mth.lerp(crawlAmount, 0.34F, -0.18F));
		this.model.head.yRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, -0.42F, bodySway * 0.10F));
		this.model.head.zRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, 0.10F, -bodySway * 0.05F));
		this.model.body.xRot = Mth.lerp(grounded, 0.10F * limp, Mth.lerp(crawlAmount, 0.03F, -0.08F + Math.abs(crawlCycle) * 0.04F));
		this.model.body.zRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, -0.04F, bodySway * 0.07F));
		this.model.rightArm.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.70F + limp * 0.25F, 1.20F), -1.20F + rightPull * 0.58F);
		this.model.rightArm.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.16F, -0.48F), -0.58F + rightPull * 0.28F);
		this.model.rightArm.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.12F, 0.26F), 0.44F - rightPull * 0.20F);
		this.model.leftArm.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.68F + limp * 0.24F, 1.16F), -1.20F + leftPull * 0.58F);
		this.model.leftArm.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.16F, 0.46F), 0.58F - leftPull * 0.28F);
		this.model.leftArm.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.12F, -0.25F), -0.44F + leftPull * 0.20F);
		this.model.rightLeg.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.08F, 0.10F), 0.32F - rightPull * 0.12F);
		this.model.rightLeg.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.06F, 0.18F), 0.16F + rightPull * 0.12F);
		this.model.rightLeg.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.03F, 0.08F), 0.05F);
		this.model.leftLeg.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.08F, -0.08F), 0.32F - leftPull * 0.12F);
		this.model.leftLeg.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.06F, -0.16F), -0.16F - leftPull * 0.12F);
		this.model.leftLeg.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.03F, -0.07F), -0.05F);
		resetPlayerOverlayTransforms(this.model);
	}

	private static void drawTexturedCuboid(PoseStack matrices, VertexConsumer vertices, int argb, float width, float height, float depth, float u0, float v0, float u1, float v1) {
		PoseStack.Pose entry = matrices.last();
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
		PoseStack.Pose entry,
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

	private static void emitVertex(VertexConsumer vertices, PoseStack.Pose entry, int argb, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
		vertices.addVertex(entry, x, y, z)
			.setColor(ARGB.red(argb), ARGB.green(argb), ARGB.blue(argb), ARGB.alpha(argb))
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(FULL_BRIGHT)
			.setNormal(entry, normalX, normalY, normalZ);
	}

	private void renderVanillaFeaturePass(PoseStack matrices, SubmitNodeCollector commandQueue, CameraRenderState cameraState, AvatarRenderState state, int alpha) {
		if (commandQueue == null || cameraState == null) {
			return;
		}

		EntityRenderer<?, ? super AvatarRenderState> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state);
		if (!(renderer instanceof AvatarRenderer playerRenderer)) {
			return;
		}

		boolean invisible = state.isInvisible;
		boolean invisibleToPlayer = state.isInvisibleToPlayer;
		int outlineColor = state.outlineColor;
		RenderType suppressedBodyLayer = RenderTypes.entityTranslucent(state.skin.body().texturePath(), false);
		SubmitNodeCollector alphaQueue = new AlphaSubmitNodeCollector(commandQueue, alpha / 255.0F, suppressedBodyLayer);

		state.isInvisible = true;
		state.isInvisibleToPlayer = false;
		state.outlineColor = EntityRenderState.NO_OUTLINE;

		try {
			playerRenderer.submit(state, matrices, alphaQueue, cameraState);
		} finally {
			state.isInvisible = invisible;
			state.isInvisibleToPlayer = invisibleToPlayer;
			state.outlineColor = outlineColor;
		}
	}

	private void renderBasePlayerModel(PoseStack matrices, VertexConsumer vertices, int argb) {
		AvatarRenderState state = this.pose.state();
		this.model.setupAnim(state);

		matrices.pushPose();
		if (state.isCrouching) {
			matrices.translate(0.0F, state.scale * -2.0F / 16.0F, 0.0F);
		}

		matrices.scale(state.scale, state.scale, state.scale);
		matrices.mulPose(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
		if (state.isAutoSpinAttack) {
			matrices.mulPose(Axis.XP.rotationDegrees(-90.0F - state.xRot));
			matrices.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * -75.0F));
		} else if (state.isUpsideDown) {
			matrices.translate(0.0F, (state.boundingBoxHeight + 0.1F) / state.scale, 0.0F);
			matrices.mulPose(Axis.ZP.rotationDegrees(180.0F));
		}

		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		this.model.renderToBuffer(matrices, vertices, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
		matrices.popPose();
	}

	private static float silhouetteFade(float progress) {
		if (progress < 0.62F) {
			return 1.0F;
		}

		return 1.0F - Mth.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F);
	}

	private static float morphFade(float progress) {
		return 1.0F - smoothStep(progress);
	}

	private static float smoothStep(float value) {
		float progress = Mth.clamp(value, 0.0F, 1.0F);
		return progress * progress * (3.0F - 2.0F * progress);
	}

	private static float easeOutCubic(float progress) {
		float inverse = 1.0F - progress;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float easeInCubic(float progress) {
		float clamped = Mth.clamp(progress, 0.0F, 1.0F);
		return clamped * clamped * clamped;
	}

	private static void resetPlayerOverlayTransforms(PlayerModel model) {
		model.hat.resetPose();
		model.leftSleeve.resetPose();
		model.rightSleeve.resetPose();
		model.leftPants.resetPose();
		model.rightPants.resetPose();
		model.jacket.resetPose();
	}

	private static void showPlayerModelParts(PlayerModel model) {
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

	private static Vec3 entityPosition(Entity entity) {
		return new Vec3(entity.getX(), entity.getY(), entity.getZ());
	}

	private static int stateSeed(AvatarRenderState state) {
		int nameSeed = state.scoreText == null ? 0 : state.scoreText.getString().hashCode();
		return nameSeed ^ state.id * 31;
	}

	private static Identifier getWhiteTexture() {
		if (whiteTexture == null) {
			whiteTexture = new DynamicTexture("KoHs Death Effects Solid White", 2, 2, false);
			Minecraft.getInstance().getTextureManager().register(WHITE_TEXTURE, whiteTexture);
			NativeImage image = whiteTexture.getPixels();
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

		private static RagdollBody create(PoseSnapshot pose, RagdollShape shape, Vec3 explosionImpulse) {
			AvatarRenderState state = pose.state();
			Vec3 inheritedVelocity = pose.velocity().multiply(0.16, 0.03, 0.16);
			float yaw = 180.0F - shape.yawDegrees();
			int seed = stateSeed(state) ^ Float.floatToIntBits(shape.phase());
			Vec3 forward = rotateY(new Vec3(0.0, 0.0, 1.0), yaw);
			Vec3 side = rotateY(new Vec3(1.0, 0.0, 0.0), yaw);

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
			Vec3 inheritedVelocity,
			Vec3 explosionImpulse,
			Vec3 forward,
			Vec3 side,
			RagdollShape shape,
			int seed,
			int index
		) {
			Vec3 position = rotateY(new Vec3(x, y, z), yaw);
			double heightFactor = Mth.clamp((float)(y / 2.0), 0.0F, 1.0F);
			Vec3 center = rotateY(new Vec3(0.0, 0.85, 0.0), yaw);
			Vec3 radial = position.subtract(center);
			Vec3 spinDirection = radial.horizontalDistanceSqr() > 0.0001
				? new Vec3(-radial.z, 0.0, radial.x).normalize()
				: side;
			double fallSign = shape.faceDown() ? 1.0 : -0.65;
			Vec3 fallVelocity = forward.scale(0.055 * fallSign * heightFactor)
				.add(0.0, -0.018 * heightFactor, 0.0);
			Vec3 spinVelocity = spinDirection.scale(shape.initialSpinVelocity() * 0.045 * heightFactor);
			Vec3 looseVelocity = side.scale(randomSigned(seed, 30 + index) * 0.018 * (0.35 + heightFactor));
			Vec3 explosiveVelocity = explosionImpulse.scale(0.65 + heightFactor * 0.65 + randomSigned(seed, 60 + index) * 0.2)
				.add(side.scale(randomSigned(seed, 80 + index) * explosionImpulse.length() * 0.28));
			Vec3 velocity = inheritedVelocity.add(fallVelocity).add(spinVelocity).add(looseVelocity).add(explosiveVelocity);
			return new RagdollNode(position, position.subtract(velocity), 1.0 / mass);
		}

		private static RagdollJoint joint(int first, int second, double distance, double stiffness) {
			return new RagdollJoint(first, second, distance, stiffness);
		}

		private void tick() {
			for (RagdollNode node : this.nodes) {
				Vec3 velocity = node.position.subtract(node.previous).scale(AIR_DAMPING);
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
			Vec3 delta = second.position.subtract(first.position);
			double length = delta.length();
			if (length < 0.0001) {
				return;
			}

			double difference = (length - joint.distance()) / length;
			double totalInverseMass = first.inverseMass + second.inverseMass;
			if (totalInverseMass <= 0.0) {
				return;
			}

			Vec3 correction = delta.scale(difference * joint.stiffness());
			first.position = first.position.add(correction.scale(first.inverseMass / totalInverseMass));
			second.position = second.position.subtract(correction.scale(second.inverseMass / totalInverseMass));
		}

		private void collideFloor(RagdollNode node) {
			if (node.position.y >= FLOOR_Y) {
				return;
			}

			Vec3 velocity = node.position.subtract(node.previous);
			node.position = new Vec3(node.position.x, FLOOR_Y, node.position.z);
			node.previous = new Vec3(
				node.position.x - velocity.x * FLOOR_FRICTION,
				FLOOR_Y + Math.abs(velocity.y) * 0.08,
				node.position.z - velocity.z * FLOOR_FRICTION
			);
		}

		private void render(PoseStack matrices, VertexConsumer vertices, int argb, boolean slim, float tickDelta) {
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

		private void applyClientPush(Vec3 direction, Vec3 playerVelocity, double strength) {
			Vec3 impulse = direction.scale(0.048 * strength).add(playerVelocity.scale(0.12));
			for (RagdollNode node : this.nodes) {
				double heightFactor = Mth.clamp((float)(node.position.y / 1.8), 0.0F, 1.0F);
				node.previous = node.previous.subtract(impulse.scale(0.35 + heightFactor * 0.65));
			}
		}

		private void renderSegment(
			PoseStack matrices,
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
			Vec3 from = this.lerp(first, tickDelta);
			Vec3 to = this.lerp(second, tickDelta);
			Vec3 delta = to.subtract(from);
			float length = (float)delta.length();
			if (length < 0.001F) {
				return;
			}

			Vector3f direction = new Vector3f((float)delta.x, (float)delta.y, (float)delta.z).normalize();
			Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
			Vec3 center = from.add(delta.scale(0.5));

			matrices.pushPose();
			matrices.translate(center.x, center.y, center.z);
			matrices.mulPose(rotation);
			drawTexturedCuboid(matrices, vertices, argb, width, length, depth, u0, v0, u1, v1);
			matrices.popPose();
		}

		private void renderLayeredSegment(
			PoseStack matrices,
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

		private Vec3 lerp(int index, float tickDelta) {
			RagdollNode node = this.nodes[index];
			return node.previous.lerp(node.position, tickDelta);
		}

		private static Vec3 rotateY(Vec3 vector, float degrees) {
			double radians = Math.toRadians(degrees);
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
		}

		private static final class RagdollNode {
			private Vec3 position;
			private Vec3 previous;
			private final double inverseMass;

			private RagdollNode(Vec3 position, Vec3 previous, double inverseMass) {
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
			AvatarRenderState state = pose.state();
			Vec3 velocity = pose.velocity();
			float speed = (float)velocity.horizontalDistance();
			int seed = stateSeed(state)
				^ state.id * 31
				^ Float.floatToIntBits(state.bodyRot)
				^ Float.floatToIntBits((float)velocity.x)
				^ Float.floatToIntBits((float)velocity.z);
			float velocityYaw = speed > 0.035F ? (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : state.bodyRot;
			float yawInfluence = Mth.clamp(speed * 2.75F, 0.0F, 1.0F);
			float yaw = Mth.lerp(yawInfluence, state.bodyRot, velocityYaw) + randomSigned(seed, 1) * 22.0F;
			boolean faceDown = randomSigned(seed, 2) > -0.25F;
			float phase = (randomSigned(seed, 5) + 1.0F) * (float)Math.PI;
			float spinVelocity = randomSigned(seed, 8) * 0.28F + speed * randomSigned(seed, 9) * 0.55F;

			return new RagdollShape(yaw, faceDown, phase, spinVelocity);
		}
	}

	private record PoseSnapshot(AvatarRenderState state, boolean slim, Vec3 velocity) {
		private static PoseSnapshot from(Player player, KohsDeathEffectsConfig config) {
			AvatarRenderState state = new AvatarRenderState();
			boolean slim = false;

			if (player instanceof AbstractClientPlayer clientPlayer) {
				EntityRenderer<? super AbstractClientPlayer, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(clientPlayer);
				if (renderer instanceof AvatarRenderer playerRenderer) {
					playerRenderer.extractRenderState(clientPlayer, state, 1.0F);
					slim = state.skin.model() == PlayerModelType.SLIM;
					prepareForEffect(state, config);
					return new PoseSnapshot(state, slim, player.getDeltaMovement());
				}
			}

			captureFallback(player, state);
			prepareForEffect(state, config);
			return new PoseSnapshot(state, slim, player.getDeltaMovement());
		}

		private static void captureFallback(Player player, AvatarRenderState state) {
			state.entityType = player.getType();
			state.x = player.getX();
			state.y = player.getY();
			state.z = player.getZ();
			state.ageInTicks = player.tickCount;
			state.boundingBoxWidth = player.getBbWidth();
			state.boundingBoxHeight = player.getBbHeight();
			state.eyeHeight = player.getEyeHeight();
			state.bodyRot = player.getVisualRotationYInDegrees();
			state.yRot = Mth.wrapDegrees(player.getYHeadRot() - player.getVisualRotationYInDegrees());
			state.xRot = player.getXRot();
			state.scale = player.getScale();
			state.ageScale = player.getAgeScale();
			state.mainArm = player.getMainArm();
			state.attackArm = player.swingingArm == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
			state.useItemHand = player.getUsedItemHand();
			state.isUsingItem = player.isUsingItem();
			state.isCrouching = player.isCrouching();
			state.attackTime = player.getAttackAnim(1.0F);
			state.leftArmPose = fallbackArmPose(player, HumanoidArm.LEFT);
			state.rightArmPose = fallbackArmPose(player, HumanoidArm.RIGHT);
		}

		private static HumanoidModel.ArmPose fallbackArmPose(Player player, HumanoidArm arm) {
			if (player.isUsingItem() && getArmForHand(player, player.getUsedItemHand()) == arm) {
				return HumanoidModel.ArmPose.ITEM;
			}

			if (player.swinging && player.getMainArm() == arm) {
				return HumanoidModel.ArmPose.ITEM;
			}

			return player.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}

		private static HumanoidArm getArmForHand(Player player, InteractionHand hand) {
			HumanoidArm mainArm = player.getMainArm();
			return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
		}

		private static void prepareForEffect(AvatarRenderState state, KohsDeathEffectsConfig config) {
			state.deathTime = 0.0F;
			state.hasRedOverlay = false;
			state.isInvisible = false;
			state.isInvisibleToPlayer = false;
			state.outlineColor = EntityRenderState.NO_OUTLINE;
			state.isSpectator = false;
			state.displayFireAnimation = false;
			state.nameTag = null;
			state.scoreText = null;
			state.nameTagAttachment = null;
			state.leashStates = null;

			if (config.deathEffectMode == DeathEffectMode.SILHOUETTE) {
				hideSkinLayers(state);
				return;
			}

			showSkinLayers(state);

			if (config.deathEffectMode == DeathEffectMode.PLAYER_GHOST) {
				if (!config.playerGhostArmorEnabled) {
					state.headEquipment = ItemStack.EMPTY;
					state.chestEquipment = ItemStack.EMPTY;
					state.legsEquipment = ItemStack.EMPTY;
					state.feetEquipment = ItemStack.EMPTY;
				}

				if (!config.playerGhostHeldItemsEnabled) {
					state.rightHandItemState.clear();
					state.leftHandItemState.clear();
					state.heldOnHead.clear();
				}
			}
		}

		private static void hideSkinLayers(AvatarRenderState state) {
			state.showHat = false;
			state.showJacket = false;
			state.showLeftPants = false;
			state.showRightPants = false;
			state.showLeftSleeve = false;
			state.showRightSleeve = false;
			state.showCape = false;
		}

		private static void showSkinLayers(AvatarRenderState state) {
			state.showHat = true;
			state.showJacket = true;
			state.showLeftPants = true;
			state.showRightPants = true;
			state.showLeftSleeve = true;
			state.showRightSleeve = true;
		}

		private PlayerModel createModel() {
			Minecraft client = Minecraft.getInstance();
			return new PlayerModel(
				client.getEntityModels().bakeLayer(ModelLayers.PLAYER),
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
		public VertexConsumer addVertex(float x, float y, float z) {
			this.delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			this.delegate.setColor(this.color);
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			this.delegate.setColor(this.color);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			this.delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.delegate.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			this.delegate.setLineWidth(width);
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
			this.delegate.addVertex(x, y, z, this.color, u, v, overlay, light, normalX, normalY, normalZ);
		}
	}

	private static class AlphaRenderCommandQueue implements OrderedSubmitNodeCollector {
		protected final OrderedSubmitNodeCollector delegate;
		protected final float alphaMultiplier;
		protected final RenderType suppressedLayer;

		private AlphaRenderCommandQueue(OrderedSubmitNodeCollector delegate, float alphaMultiplier) {
			this(delegate, alphaMultiplier, null);
		}

		private AlphaRenderCommandQueue(OrderedSubmitNodeCollector delegate, float alphaMultiplier, RenderType suppressedLayer) {
			this.delegate = delegate;
			this.alphaMultiplier = Mth.clamp(alphaMultiplier, 0.0F, 1.0F);
			this.suppressedLayer = suppressedLayer;
		}

		@Override
		public void submitShadow(PoseStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces) {
			this.delegate.submitShadow(matrices, shadowRadius, shadowPieces);
		}

		@Override
		public void submitNameTag(PoseStack matrices, Vec3 nameLabelPos, int y, Component label, boolean notSneaking, int light, double squaredDistanceToCamera, CameraRenderState cameraState) {
			this.delegate.submitNameTag(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState);
		}

		@Override
		public void submitText(PoseStack matrices, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode layerType, int light, int color, int backgroundColor, int outlineColor) {
			this.delegate.submitText(matrices, x, y, text, dropShadow, layerType, light, this.fadeColor(color), this.fadeColor(backgroundColor), outlineColor);
		}

		@Override
		public void submitFlame(PoseStack matrices, EntityRenderState renderState, Quaternionf rotation) {
			this.delegate.submitFlame(matrices, renderState, rotation);
		}

		@Override
		public void submitLeash(PoseStack matrices, EntityRenderState.LeashState leashData) {
			this.delegate.submitLeash(matrices, leashData);
		}

		@Override
		public <S> void submitModel(
			Model<? super S> model,
			S state,
			PoseStack matrices,
			RenderType renderLayer,
			int light,
			int overlay,
			int tintedColor,
			TextureAtlasSprite sprite,
			int outlineColor,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
		) {
			if (this.isSuppressed(renderLayer)) {
				return;
			}

			this.delegate.submitModel(model, state, matrices, renderLayer, light, overlay, this.fadeColor(tintedColor), sprite, outlineColor, crumblingOverlay);
		}

		@Override
		public void submitModelPart(
			ModelPart part,
			PoseStack matrices,
			RenderType renderLayer,
			int light,
			int overlay,
			TextureAtlasSprite sprite,
			boolean sheeted,
			boolean hasGlint,
			int tintedColor,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			int outlineColor
		) {
			if (this.isSuppressed(renderLayer)) {
				return;
			}

			this.delegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, this.fadeColor(tintedColor), crumblingOverlay, outlineColor);
		}

		@Override
		public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state) {
			this.delegate.submitMovingBlock(matrices, state);
		}

		@Override
		public void submitBlockModel(PoseStack matrices, RenderType renderLayer, List<BlockStateModelPart> parts, int[] tintLayers, int light, int overlay, int outlineColor) {
			this.delegate.submitBlockModel(matrices, renderLayer, parts, this.fadeColors(tintLayers), light, overlay, outlineColor);
		}

		@Override
		public void submitBreakingBlockModel(PoseStack matrices, BlockStateModel model, long seed, int overlay) {
			this.delegate.submitBreakingBlockModel(matrices, model, seed, overlay);
		}

		@Override
		public void submitItem(
			PoseStack matrices,
			ItemDisplayContext displayContext,
			int light,
			int overlay,
			int outlineColors,
			int[] tintLayers,
			List<BakedQuad> quads,
			ItemStackRenderState.FoilType glintType
		) {
			this.delegate.submitItem(matrices, displayContext, light, overlay, outlineColors, this.fadeColors(tintLayers), quads, glintType);
		}

		@Override
		public void submitCustomGeometry(PoseStack matrices, RenderType renderLayer, SubmitNodeCollector.CustomGeometryRenderer customRenderer) {
			this.delegate.submitCustomGeometry(matrices, renderLayer, customRenderer);
		}

		@Override
		public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer customRenderer) {
			this.delegate.submitParticleGroup(customRenderer);
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
			int alpha = Mth.clamp((int)(ARGB.alpha(color) * this.alphaMultiplier), 0, 255);
			return ARGB.color(alpha, ARGB.red(color), ARGB.green(color), ARGB.blue(color));
		}

		protected boolean isSuppressed(RenderType layer) {
			return this.suppressedLayer != null && (layer == this.suppressedLayer || layer.equals(this.suppressedLayer));
		}
	}

	private static final class AlphaSubmitNodeCollector extends AlphaRenderCommandQueue implements SubmitNodeCollector {
		private final SubmitNodeCollector orderedDelegate;

		private AlphaSubmitNodeCollector(SubmitNodeCollector delegate, float alphaMultiplier) {
			this(delegate, alphaMultiplier, null);
		}

		private AlphaSubmitNodeCollector(SubmitNodeCollector delegate, float alphaMultiplier, RenderType suppressedLayer) {
			super(delegate, alphaMultiplier, suppressedLayer);
			this.orderedDelegate = delegate;
		}

		@Override
		public OrderedSubmitNodeCollector order(int order) {
			return new AlphaRenderCommandQueue(this.orderedDelegate.order(order), this.alphaMultiplier, this.suppressedLayer);
		}
	}

	private static final class AlphaVertexConsumerProvider implements MultiBufferSource {
		private final MultiBufferSource delegate;
		private final float alphaMultiplier;
		private final RenderType suppressedLayer;
		private final boolean remapEntityLayers;

		private AlphaVertexConsumerProvider(MultiBufferSource delegate, float alphaMultiplier, RenderType suppressedLayer) {
			this(delegate, alphaMultiplier, suppressedLayer, false);
		}

		private AlphaVertexConsumerProvider(MultiBufferSource delegate, float alphaMultiplier, RenderType suppressedLayer, boolean remapEntityLayers) {
			this.delegate = delegate;
			this.alphaMultiplier = Mth.clamp(alphaMultiplier, 0.0F, 1.0F);
			this.suppressedLayer = suppressedLayer;
			this.remapEntityLayers = remapEntityLayers;
		}

		@Override
		public VertexConsumer getBuffer(RenderType layer) {
			if (this.suppressedLayer != null && (layer == this.suppressedLayer || layer.equals(this.suppressedLayer))) {
				return NoopVertexConsumer.INSTANCE;
			}

			return new AlphaVertexConsumer(this.delegate.getBuffer(this.remapLayer(layer)), this.alphaMultiplier);
		}

		private RenderType remapLayer(RenderType layer) {
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
		public VertexConsumer addVertex(float x, float y, float z) {
			this.delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			this.delegate.setColor(red, green, blue, this.multiplyAlpha(alpha));
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			int alpha = this.multiplyAlpha(ARGB.alpha(color));
			this.delegate.setColor(ARGB.color(alpha, ARGB.red(color), ARGB.green(color), ARGB.blue(color)));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			this.delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.delegate.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			this.delegate.setLineWidth(width);
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
			int alpha = this.multiplyAlpha(ARGB.alpha(color));
			int fadedColor = ARGB.color(alpha, ARGB.red(color), ARGB.green(color), ARGB.blue(color));
			this.delegate.addVertex(x, y, z, fadedColor, u, v, overlay, light, normalX, normalY, normalZ);
		}

		private int multiplyAlpha(int alpha) {
			return Mth.clamp((int)(alpha * this.alphaMultiplier), 0, 255);
		}
	}

	private static final class NoopVertexConsumer implements VertexConsumer {
		private static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
		}
	}
}
