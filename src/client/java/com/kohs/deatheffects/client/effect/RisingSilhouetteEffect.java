package com.kohs.deatheffects.client.effect;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.GhostMovementMode;

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
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
	private final PoseSnapshot pose;
	private final RagdollShape ragdollShape;
	private final RagdollBody ragdollBody;
	private final PlayerEntityModel model;
	private Vec3d ragdollOffset = Vec3d.ZERO;
	private Vec3d ragdollVelocity = Vec3d.ZERO;
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
		this.pose = pose;
		this.ragdollShape = RagdollShape.from(pose);
		this.ragdollBody = mode == DeathEffectMode.RAGDOLL ? RagdollBody.create(pose, this.ragdollShape, ragdollExplosionImpulse) : null;
		this.model = pose.createModel();
		if (mode == DeathEffectMode.RAGDOLL) {
			this.ragdollVelocity = pose.velocity().multiply(0.11, 0.0, 0.11).add(ragdollExplosionImpulse.multiply(0.28, 0.0, 0.28));
		}
	}

	public static RisingSilhouetteEffect from(PlayerEntity player, KohsDeathEffectsConfig config) {
		return from(player, config, false, null);
	}

	public static RisingSilhouetteEffect from(PlayerEntity player, KohsDeathEffectsConfig config, boolean explosionDeath, Vec3d explosionPosition) {
		DeathEffectMode mode = config.deathEffectMode;
		GhostMovementMode movementMode = mode == DeathEffectMode.PLAYER_GHOST ? config.playerGhostMovement : GhostMovementMode.RISING;
		Vec3d ragdollExplosionImpulse = createExplosionImpulse(player, config, explosionDeath, explosionPosition);

		return new RisingSilhouetteEffect(
			player.getPos(),
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
			ragdollExplosionImpulse,
			PoseSnapshot.from(player, config)
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

		Vec3d center = player.getPos().add(0.0, 0.9, 0.0);
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
			case RAGDOLL -> config.ragdollDurationTicks();
			case MORPH -> config.morphDurationTicks();
			case SILHOUETTE -> config.durationTicks();
		};
	}

	public void tick() {
		if (this.ragdollBody != null) {
			this.ragdollBody.tick();
		}
		this.tickRagdollInteraction();
		this.ageTicks++;
	}

	private void tickRagdollInteraction() {
		if (this.mode != DeathEffectMode.RAGDOLL) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (this.ragdollClientCollisionEnabled && client.player != null && client.player.isAlive() && !client.player.isSpectator()) {
			Vec3d ragdollPos = this.position.add(this.ragdollOffset);
			Vec3d playerPos = client.player.getPos();
			double verticalDistance = Math.abs(playerPos.y - ragdollPos.y);
			Vec3d horizontalDelta = new Vec3d(ragdollPos.x - playerPos.x, 0.0, ragdollPos.z - playerPos.z);
			double distance = horizontalDelta.length();

			if (verticalDistance < 1.2 && distance < RAGDOLL_PUSH_RADIUS) {
				Vec3d fallback = client.player.getRotationVec(1.0F).multiply(1.0, 0.0, 1.0);
				Vec3d pushDirection = distance > 0.001 ? horizontalDelta.normalize() : (fallback.lengthSquared() > 0.001 ? fallback.normalize() : new Vec3d(0.0, 0.0, 1.0));
				Vec3d playerVelocity = client.player.getVelocity().multiply(1.0, 0.0, 1.0);
				this.ragdollVelocity = this.ragdollVelocity.add(pushDirection.multiply(0.04 * (RAGDOLL_PUSH_RADIUS - distance))).add(playerVelocity.multiply(0.16));
				if (this.ragdollBody != null) {
					this.ragdollBody.applyClientPush(pushDirection, playerVelocity, RAGDOLL_PUSH_RADIUS - distance);
				}
			}
		}

		this.ragdollOffset = this.ragdollOffset.add(this.ragdollVelocity);
		this.ragdollVelocity = this.ragdollVelocity.multiply(0.84, 0.0, 0.84);
		if (this.ragdollOffset.horizontalLength() > RAGDOLL_MAX_OFFSET) {
			this.ragdollOffset = this.ragdollOffset.normalize().multiply(RAGDOLL_MAX_OFFSET, 0.0, RAGDOLL_MAX_OFFSET);
			this.ragdollVelocity = this.ragdollVelocity.multiply(0.35, 0.0, 0.35);
		}
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

		float tickProgress = context.tickCounter().getTickProgress(false);
		float elapsedTicks = this.ageTicks + tickProgress;
		float progress = MathHelper.clamp(elapsedTicks / (float)this.durationTicks, 0.0F, 1.0F);
		float alphaNow = this.alpha * this.getFade(progress, elapsedTicks);
		if (alphaNow <= 0.01F) {
			return;
		}

		Vec3d cameraPos = context.camera().getPos();
		int alphaInt = MathHelper.clamp((int)(alphaNow * 255.0F), 0, 255);
		float rise = this.shouldRise() ? this.riseHeight * easeOutCubic(progress) : 0.0F;

		matrices.push();
		Vec3d renderPosition = this.position.add(this.mode == DeathEffectMode.RAGDOLL ? this.ragdollOffset : Vec3d.ZERO);
		matrices.translate(renderPosition.x - cameraPos.x, renderPosition.y - cameraPos.y + rise, renderPosition.z - cameraPos.z);

		switch (this.mode) {
			case PLAYER_GHOST -> this.renderPlayerGhost(matrices, consumers, alphaInt);
			case RAGDOLL -> this.renderRagdoll(matrices, consumers, alphaInt, progress, elapsedTicks);
			case MORPH -> this.renderMorph(matrices, consumers, alphaInt, tickProgress);
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
			case RAGDOLL -> this.ragdollFadeEnabled ? 1.0F - this.getRagdollFadeProgress(elapsedTicks) : 1.0F;
			case MORPH -> silhouetteFade(progress);
			case SILHOUETTE -> silhouetteFade(progress);
		};
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
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(getWhiteTexture(), false));
		matrices.push();
		matrices.scale(this.scale, this.scale, this.scale);
		this.renderBasePlayerModel(matrices, vertices, ColorHelper.getArgb(alpha, red, green, blue));
		matrices.pop();
	}

	private void renderPlayerGhost(MatrixStack matrices, VertexConsumerProvider consumers, int alpha) {
		PlayerEntityRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(state.skinTextures.texture(), false));
		this.renderBasePlayerModel(matrices, skinVertices, ColorHelper.getArgb(alpha, 255, 255, 255));

		if (!this.renderGhostFeatures) {
			return;
		}

		RenderLayer suppressedBodyLayer = RenderLayer.getItemEntityTranslucentCull(state.skinTextures.texture());
		VertexConsumerProvider alphaConsumers = new AlphaVertexConsumerProvider(consumers, alpha / 255.0F, suppressedBodyLayer);
		this.renderVanillaFeaturePass(matrices, alphaConsumers, state);
	}

	private void renderRagdoll(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float progress, float elapsedTicks) {
		PlayerEntityRenderState state = this.pose.state();
		VertexConsumer skinVertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(state.skinTextures.texture(), false));
		this.renderRagdollBaseModel(matrices, skinVertices, ColorHelper.getArgb(alpha, 255, 255, 255), progress, elapsedTicks);
	}

	private void renderMorph(MatrixStack matrices, VertexConsumerProvider consumers, int alpha, float tickDelta) {
		if (this.morphEntity == null) {
			return;
		}

		this.morphEntity.age = this.ageTicks;
		VertexConsumerProvider alphaConsumers = new AlphaVertexConsumerProvider(consumers, alpha / 255.0F, null, true);
		MinecraftClient.getInstance().getEntityRenderDispatcher().render(
			this.morphEntity,
			0.0,
			0.0,
			0.0,
			this.morphEntity.getYaw(tickDelta),
			matrices,
			alphaConsumers,
			LightmapTextureManager.MAX_LIGHT_COORDINATE
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

	private void renderVanillaFeaturePass(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntityRenderState state) {
		EntityRenderer<?, ? super PlayerEntityRenderState> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(state);
		if (!(renderer instanceof PlayerEntityRenderer playerRenderer)) {
			return;
		}

		boolean invisible = state.invisible;
		boolean invisibleToPlayer = state.invisibleToPlayer;
		boolean hasOutline = state.hasOutline;

		state.invisible = true;
		state.invisibleToPlayer = false;
		state.hasOutline = false;

		try {
			playerRenderer.render(state, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		} finally {
			state.invisible = invisible;
			state.invisibleToPlayer = invisibleToPlayer;
			state.hasOutline = hasOutline;
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

	private static float easeOutCubic(float progress) {
		float inverse = 1.0F - progress;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float randomSigned(int seed, int salt) {
		int value = seed ^ salt * 0x45D9F3B;
		value ^= value >>> 16;
		value *= 0x45D9F3B;
		value ^= value >>> 16;
		return (value & 0xFFFF) / 32767.5F - 1.0F;
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
			int seed = state.name.hashCode() ^ state.id * 31 ^ Float.floatToIntBits(shape.phase());
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
			this.renderSegment(matrices, vertices, argb, PIVOT, NECK, 0.50F, 0.25F, 20.0F, 20.0F, 28.0F, 32.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, NECK, HEAD, 0.50F, 0.50F, 8.0F, 8.0F, 16.0F, 16.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, RIGHT_SHOULDER, RIGHT_ARM, armWidth, 0.25F, 44.0F, 20.0F, 48.0F, 32.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, LEFT_SHOULDER, LEFT_ARM, armWidth, 0.25F, 36.0F, 52.0F, 40.0F, 64.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, RIGHT_HIP, RIGHT_KNEE, 0.25F, 0.25F, 4.0F, 20.0F, 8.0F, 26.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, RIGHT_KNEE, RIGHT_LEG, 0.25F, 0.25F, 4.0F, 26.0F, 8.0F, 32.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, LEFT_HIP, LEFT_KNEE, 0.25F, 0.25F, 20.0F, 52.0F, 24.0F, 58.0F, tickDelta);
			this.renderSegment(matrices, vertices, argb, LEFT_KNEE, LEFT_LEG, 0.25F, 0.25F, 20.0F, 58.0F, 24.0F, 64.0F, tickDelta);
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
			int seed = state.name.hashCode()
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
					slim = state.skinTextures.model() == SkinTextures.Model.SLIM;
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
			state.hasOutline = false;
			state.spectator = false;
			state.onFire = false;
			state.customName = null;
			state.displayName = null;
			state.playerName = null;
			state.nameLabelPos = null;
			state.leashDatas = null;
			state.hitbox = null;
			state.debugInfo = null;

			if (config.deathEffectMode == DeathEffectMode.SILHOUETTE) {
				hideSkinLayers(state);
				return;
			}

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

		private PlayerEntityModel createModel() {
			MinecraftClient client = MinecraftClient.getInstance();
			return new PlayerEntityModel(
				client.getLoadedEntityModels().getModelPart(this.slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER),
				this.slim
			);
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
			RenderLayer armorLayer = this.remapOpaqueArmorLayer(layer);
			if (!this.remapEntityLayers || this.alphaMultiplier >= 0.995F) {
				return armorLayer;
			}

			String name = armorLayer.getName();
			if (!name.startsWith("entity_cutout") && !name.startsWith("entity_solid") && !name.startsWith("entity_no_outline")) {
				return armorLayer;
			}

			Identifier texture = extractLayerTexture(armorLayer);
			return texture == null ? armorLayer : RenderLayer.getEntityTranslucent(texture, false);
		}

		private RenderLayer remapOpaqueArmorLayer(RenderLayer layer) {
			if (!ARMOR_CUTOUT_LAYER.equals(layer.getName())) {
				return layer;
			}

			Identifier texture = extractLayerTexture(layer);
			return texture == null ? layer : RenderLayer.createArmorTranslucent(texture);
		}

		private static Identifier extractLayerTexture(RenderLayer layer) {
			String layerText = layer.toString();
			int start = layerText.indexOf(LAYER_TEXTURE_PREFIX);
			if (start < 0) {
				return null;
			}

			start += LAYER_TEXTURE_PREFIX.length();
			int end = layerText.indexOf(']', start);
			if (end <= start) {
				return null;
			}

			return Identifier.tryParse(layerText.substring(start, end));
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
		public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
		}
	}
}
