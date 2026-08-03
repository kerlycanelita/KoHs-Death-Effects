package com.kohs.deatheffects.client.effect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.KidsMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.KidsTrainFacing;
import com.kohs.deatheffects.network.KidsNetworking;
import com.kohs.deatheffects.network.KidsNetworking.KidsEventPayload;
import com.kohs.deatheffects.network.KidsNetworking.KidsSettingsPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public final class KidsEffectManager {
	private static final int MAX_CUMULATIVE_KIDS = 20;
	private static final int MAX_SHOULDER_KIDS = 2;
	private static final int INFINITY_TIMER_SECONDS = 301;
	private static final int FADE_TICKS = 20;
	private static final int MISSING_CARRIER_TICKS = 200;
	private static final int RECENT_ATTACK_TICKS = 100;
	private static final int DUPLICATE_KILL_WINDOW_TICKS = 40;
	private static final float PLAYER_MODEL_SCALE = 0.9375F;
	private static final float SHOULDER_DOLL_SCALE = 0.22F;
	private static final float DRAGGED_DOLL_SCALE = 0.18F;
	private static final float SHOULDER_MODEL_SIDE_OFFSET = 0.40F;
	private static final float SHOULDER_MODEL_SEAT_HEIGHT = 0.33F;
	private static final double DRAGGED_FIRST_DISTANCE = 0.90;
	private static final double DRAGGED_SPACING = 0.42;
	private static final double TRAIL_MIN_SAMPLE_DISTANCE = 0.06;
	private static final double TRAIL_TELEPORT_DISTANCE = 3.0;
	private static final double MAX_TRAIL_DISTANCE = 40.0;
	private static final int ROPE_RED = 112;
	private static final int ROPE_GREEN = 73;
	private static final int ROPE_BLUE = 43;

	private final Map<UUID, CarrierState> carriers = new HashMap<>();
	private final Map<UUID, Integer> recentlyAttackedPlayers = new HashMap<>();
	private PlayerEntityModel regularModel;
	private PlayerEntityModel slimModel;
	private LocalSettings lastAppliedLocalSettings;
	private LocalSettings lastSentSettings;
	private PreviewShoulderState previewShoulderState;
	private boolean networkAvailableLastTick;
	private boolean registered;

	public void register() {
		if (this.registered) {
			return;
		}

		ClientPlayNetworking.registerGlobalReceiver(KidsEventPayload.ID, (payload, context) -> this.receive(payload, context.client()));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()
				&& entity instanceof PlayerEntity target
				&& MinecraftClient.getInstance().player != null
				&& player.getUuid().equals(MinecraftClient.getInstance().player.getUuid())) {
				this.recentlyAttackedPlayers.put(target.getUuid(), RECENT_ATTACK_TICKS);
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(this::render);
		this.registered = true;
	}

	public void onPlayerDeath(PlayerEntity victim) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}

		DamageSource source = victim.getRecentDamageSource();
		Entity attacker = source == null ? null : source.getAttacker();
		boolean localPlayerCausedDamage = attacker instanceof PlayerEntity killer
			&& killer.getUuid().equals(client.player.getUuid());
		if (!localPlayerCausedDamage && !this.recentlyAttackedPlayers.containsKey(victim.getUuid())) {
			return;
		}

		LocalSettings settings = LocalSettings.fromConfig(KohsDeathEffectsConfig.get());
		if (!settings.enabled()) {
			return;
		}

		this.addDoll(
			client,
			client.player.getUuid(),
			UUID.randomUUID(),
			victim.getUuid(),
			victim.getName().getString(),
			settings
		);
	}

	public void setPreviewShoulders(int entityId, SkinTextures skin, float wave) {
		this.previewShoulderState = new PreviewShoulderState(entityId, skin, wave);
	}

	public void clearPreviewShoulders(int entityId) {
		if (this.previewShoulderState != null && this.previewShoulderState.entityId() == entityId) {
			this.previewShoulderState = null;
		}
	}

	private void receive(KidsEventPayload payload, MinecraftClient client) {
		LocalSettings settings = LocalSettings.fromPayload(payload);
		if (payload.action() == KidsNetworking.ACTION_CLEAR) {
			this.carriers.remove(payload.carrierUuid());
			return;
		}

		CarrierState state = this.carriers.computeIfAbsent(payload.carrierUuid(), uuid -> new CarrierState(settings));
		if (state.settings.mode() != settings.mode()) {
			state.dolls.clear();
			state.seenKills.clear();
		}
		state.applySettings(settings);
		if (payload.action() == KidsNetworking.ACTION_ADD) {
			this.addDoll(client, payload.carrierUuid(), payload.killUuid(), payload.victimUuid(), payload.victimName(), settings);
		}
	}

	private void addDoll(MinecraftClient client, UUID carrierUuid, UUID killUuid, UUID victimUuid, String victimName, LocalSettings settings) {
		CarrierState state = this.carriers.computeIfAbsent(carrierUuid, uuid -> new CarrierState(settings));
		if (state.settings.mode() != settings.mode()) {
			state.dolls.clear();
			state.seenKills.clear();
		}
		state.applySettings(settings);
		if (state.seenKills.contains(killUuid)) {
			return;
		}
		for (DollState existing : state.dolls) {
			if (existing.victimUuid.equals(victimUuid) && existing.ageTicks <= DUPLICATE_KILL_WINDOW_TICKS) {
				return;
			}
		}
		state.removeExpiredDolls();
		int limit = settings.mode() == KidsMode.CUMULATIVE ? MAX_CUMULATIVE_KIDS : MAX_SHOULDER_KIDS;
		if (state.dolls.size() >= limit) {
			if (settings.mode() == KidsMode.CUMULATIVE && client.player != null && carrierUuid.equals(client.player.getUuid())) {
				client.inGameHud.setOverlayMessage(Text.literal("Kids limit reached: 20 players"), false);
			}
			return;
		}
		state.seenKills.add(killUuid);

		GameProfile profile = new GameProfile(victimUuid, victimName == null || victimName.isBlank() ? "Player" : victimName);
		SkinTextures fallback = DefaultSkinHelper.getSkinTextures(profile);
		DollState doll = new DollState(killUuid, victimUuid, profile.name(), fallback, settings.lifetimeTicks());
		AbstractClientPlayerEntity onlineVictim = findPlayer(client, victimUuid);
		if (onlineVictim != null) {
			doll.skin = onlineVictim.getSkin();
		} else {
			client.getSkinProvider().fetchSkinTextures(profile).thenAccept(result -> client.execute(() -> {
				if (result.isPresent()) {
					doll.skin = result.get();
				}
			}));
		}
		state.dolls.add(doll);
	}

	private void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			this.carriers.clear();
			this.recentlyAttackedPlayers.clear();
			this.lastAppliedLocalSettings = null;
			this.lastSentSettings = null;
			this.networkAvailableLastTick = false;
			return;
		}
		this.recentlyAttackedPlayers.replaceAll((uuid, ticks) -> ticks - 1);
		this.recentlyAttackedPlayers.values().removeIf(ticks -> ticks <= 0);

		LocalSettings current = LocalSettings.fromConfig(KohsDeathEffectsConfig.get());
		if (!current.equals(this.lastAppliedLocalSettings)) {
			this.applyLocalSettings(client.player.getUuid(), current);
			this.lastAppliedLocalSettings = current;
		}

		boolean networkAvailable = ClientPlayNetworking.canSend(KidsSettingsPayload.ID);
		if (!networkAvailable) {
			this.lastSentSettings = null;
		} else if (!networkAvailableLastTick || !current.equals(this.lastSentSettings)) {
			ClientPlayNetworking.send(current.toPayload());
			this.lastSentSettings = current;
		}
		this.networkAvailableLastTick = networkAvailable;

		for (Iterator<Map.Entry<UUID, CarrierState>> iterator = this.carriers.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UUID, CarrierState> entry = iterator.next();
			CarrierState state = entry.getValue();
			for (DollState doll : state.dolls) {
				doll.ageTicks++;
			}
			state.removeExpiredDolls();
			AbstractClientPlayerEntity carrier = findPlayer(client, entry.getKey());
			if (carrier == null) {
				state.missingCarrierTicks++;
			} else {
				state.missingCarrierTicks = 0;
				state.recordCarrierPosition(new Vec3d(carrier.getX(), carrier.getY(), carrier.getZ()), carrier.bodyYaw);
			}
			if (state.missingCarrierTicks > MISSING_CARRIER_TICKS || state.dolls.isEmpty() && !state.settings.enabled()) {
				iterator.remove();
			}
		}
	}

	private void applyLocalSettings(UUID localPlayerUuid, LocalSettings current) {
		CarrierState state = this.carriers.get(localPlayerUuid);
		if (!current.enabled()) {
			this.carriers.remove(localPlayerUuid);
			return;
		}

		if (state == null) {
			return;
		}

		if (state.settings.mode() != current.mode()) {
			state.dolls.clear();
			state.seenKills.clear();
		}
		state.applySettings(current);
	}

	void renderShoulderDolls(
		MatrixStack matrices,
		OrderedRenderCommandQueue commandQueue,
		int light,
		PlayerEntityRenderState carrierRenderState,
		PlayerEntityModel carrierModel
	) {
		PreviewShoulderState preview = this.previewShoulderState;
		if (preview != null && carrierRenderState.id == preview.entityId()) {
			for (int index = 0; index < MAX_SHOULDER_KIDS; index++) {
				this.submitShoulderDoll(
					matrices,
					commandQueue,
					light,
					carrierRenderState,
					carrierModel,
					preview.skin(),
					preview.wave(),
					SHOULDER_DOLL_SCALE,
					255,
					index
				);
			}
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		Entity entity = client.world.getEntityById(carrierRenderState.id);
		if (!(entity instanceof AbstractClientPlayerEntity carrier)) {
			return;
		}
		if (client.player != null
			&& carrier.getUuid().equals(client.player.getUuid())
			&& client.options.getPerspective().isFirstPerson()) {
			return;
		}

		CarrierState state = this.carriers.get(carrier.getUuid());
		if (state == null || state.dolls.isEmpty()) {
			return;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(false);
		for (int index = 0; index < Math.min(MAX_SHOULDER_KIDS, state.dolls.size()); index++) {
			DollState doll = state.dolls.get(index);
			float alpha = doll.alpha();
			if (alpha <= 0.01F) {
				continue;
			}

			float wave = state.settings.animationEnabled()
				? MathHelper.sin((doll.ageTicks + tickProgress + index * 7.0F) * 0.38F)
				: 0.0F;
			float spawnScale = 0.65F + smoothStep(MathHelper.clamp(doll.ageTicks / 10.0F, 0.0F, 1.0F)) * 0.35F;
			float scale = SHOULDER_DOLL_SCALE * spawnScale;
			int alphaInt = MathHelper.clamp((int)(alpha * 255.0F), 0, 255);
			this.submitShoulderDoll(
				matrices,
				commandQueue,
				light,
				carrierRenderState,
				carrierModel,
				doll.skin,
				wave,
				scale,
				alphaInt,
				index
			);
		}
	}

	private void submitShoulderDoll(
		MatrixStack matrices,
		OrderedRenderCommandQueue commandQueue,
		int light,
		PlayerEntityRenderState carrierRenderState,
		PlayerEntityModel carrierModel,
		SkinTextures skin,
		float wave,
		float scale,
		int alpha,
		int index
	) {
		KidsDollRenderState dollRenderState = createDollRenderState();
		dollRenderState.skinTextures = skin;
		dollRenderState.shoulderPose = true;
		dollRenderState.shoulderWave = wave;

		PlayerEntityModel dollModel = this.getModel(skin.model() == PlayerSkinType.SLIM);
		matrices.push();
		carrierModel.body.applyTransform(matrices);
		float side = index == 0 ? SHOULDER_MODEL_SIDE_OFFSET : -SHOULDER_MODEL_SIDE_OFFSET;
		matrices.translate(side, -SHOULDER_MODEL_SEAT_HEIGHT, 0.0F);
		matrices.scale(scale, scale, scale);
		commandQueue.submitModel(
			dollModel,
			dollRenderState,
			matrices,
			RenderLayer.getEntityTranslucent(skin.body().texturePath(), false),
			light,
			OverlayTexture.DEFAULT_UV,
			ColorHelper.getArgb(alpha, 255, 255, 255),
			null,
			carrierRenderState.outlineColor,
			null
		);
		matrices.pop();
	}

	private void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (client.world == null || matrices == null || consumers == null || this.carriers.isEmpty()) {
			return;
		}

		CameraRenderState cameraState = context.worldState().cameraRenderState;
		Vec3d cameraPos = cameraState != null && cameraState.pos != null ? cameraState.pos : Vec3d.ZERO;
		float tickProgress = client.getRenderTickCounter().getTickProgress(false);
		for (Map.Entry<UUID, CarrierState> entry : this.carriers.entrySet()) {
			AbstractClientPlayerEntity carrier = findPlayer(client, entry.getKey());
			CarrierState state = entry.getValue();
			if (carrier == null || carrier.isDead() || state.dolls.isEmpty()) {
				continue;
			}

			this.renderCarrierKids(context, carrier, state, cameraPos, tickProgress);
		}
	}

	private void renderCarrierKids(WorldRenderContext context, AbstractClientPlayerEntity carrier, CarrierState state, Vec3d cameraPos, float tickProgress) {
		Vec3d carrierPos = carrier.getLerpedPos(tickProgress);
		float bodyYaw = MathHelper.lerpAngleDegrees(tickProgress, carrier.lastBodyYaw, carrier.bodyYaw);
		double crouchOffset = carrier.isSneaking() ? -0.18 : 0.0;

		if (state.settings.mode() != KidsMode.CUMULATIVE || state.dolls.size() <= MAX_SHOULDER_KIDS) {
			return;
		}

		float ropeSizeMultiplier = state.settings.ropeSizePercent() / 100.0F;
		Vec3d behind = behindForYaw(bodyYaw);
		Vec3d previousRopePoint = carrierPos.add(behind.multiply(0.16)).add(0.0, 0.86 + crouchOffset, 0.0);
		List<RopeSegment> ropeSegments = new ArrayList<>();
		for (int index = MAX_SHOULDER_KIDS; index < state.dolls.size(); index++) {
			DollState doll = state.dolls.get(index);
			int draggedIndex = index - MAX_SHOULDER_KIDS;
			double distance = (DRAGGED_FIRST_DISTANCE + draggedIndex * DRAGGED_SPACING) * ropeSizeMultiplier;
			TrailSample trailSample = state.sampleTrail(carrierPos, distance, bodyYaw);
			Vec3d right = new Vec3d(-trailSample.direction().z, 0.0, trailSample.direction().x);
			double sway = MathHelper.sin((doll.ageTicks + tickProgress) * 0.08F + draggedIndex * 0.75F) * 0.10 * ropeSizeMultiplier;
			double alternatingSide = ((draggedIndex & 1) == 0 ? -0.07 : 0.07) * ropeSizeMultiplier;
			Vec3d horizontal = trailSample.position().add(right.multiply(sway + alternatingSide));
			double groundY = findGroundY(
				clientWorld(carrier),
				horizontal.x,
				horizontal.z,
				trailSample.position().y + 1.25,
				trailSample.position().y
			);
			if (Double.isNaN(doll.smoothedGroundY)) {
				doll.smoothedGroundY = groundY;
			} else {
				doll.smoothedGroundY = MathHelper.lerp(0.30, doll.smoothedGroundY, groundY);
			}
			Vec3d position = new Vec3d(horizontal.x, doll.smoothedGroundY + 0.04, horizontal.z);
			float frantic = state.settings.animationEnabled() && state.settings.draggedHandMovementEnabled()
				? MathHelper.sin((doll.ageTicks + tickProgress + draggedIndex * 5.0F) * 0.48F)
				: 0.0F;
			float trailYaw = yawFromBehindDirection(trailSample.direction());
			float bodyPitch = state.settings.trainFacing() == KidsTrainFacing.LOOK_UP ? 90.0F : -90.0F;
			this.renderDoll(context, doll, position, trailYaw, bodyPitch, DRAGGED_DOLL_SCALE * ropeSizeMultiplier, frantic, false, cameraPos);
			Vec3d ropePoint = position.add(0.0, 0.08 * ropeSizeMultiplier, 0.0);
			ropeSegments.add(new RopeSegment(previousRopePoint, ropePoint, doll.alpha()));
			previousRopePoint = ropePoint;
		}

		this.renderRope(context, ropeSegments, cameraPos, ropeSizeMultiplier);
	}

	private void renderDoll(
		WorldRenderContext context,
		DollState doll,
		Vec3d position,
		float yaw,
		float bodyPitch,
		float baseScale,
		float animation,
		boolean shoulder,
		Vec3d cameraPos
	) {
		float alpha = doll.alpha();
		if (alpha <= 0.01F) {
			return;
		}

		PlayerEntityModel model = this.getModel(doll.skin.model() == PlayerSkinType.SLIM);
		PlayerEntityRenderState state = createDollRenderState();
		model.setAngles(state);
		if (shoulder) {
			applyShoulderPose(model, animation);
		} else {
			applyDraggedPose(model, animation);
		}
		showAllParts(model);

		float spawnScale = 0.65F + smoothStep(MathHelper.clamp(doll.ageTicks / 10.0F, 0.0F, 1.0F)) * 0.35F;
		float scale = baseScale * spawnScale;
		int alphaInt = MathHelper.clamp((int)(alpha * 255.0F), 0, 255);
		VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(doll.skin.body().texturePath(), false));
		MatrixStack matrices = context.matrices();
		matrices.push();
		matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		if (bodyPitch != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bodyPitch));
		}
		matrices.scale(scale, scale, scale);
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		model.render(
			matrices,
			vertices,
			LightmapTextureManager.MAX_LIGHT_COORDINATE,
			OverlayTexture.DEFAULT_UV,
			ColorHelper.getArgb(alphaInt, 255, 255, 255)
		);
		matrices.pop();
	}

	private void renderRope(WorldRenderContext context, List<RopeSegment> segments, Vec3d cameraPos, float sizeMultiplier) {
		if (segments.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();
		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
		for (RopeSegment segment : segments) {
			if (segment.alpha() <= 0.01F) {
				continue;
			}
			Vec3d from = segment.from();
			Vec3d to = segment.to();
			Vec3d direction = to.subtract(from);
			if (direction.lengthSquared() < 0.0001) {
				continue;
			}
			direction = direction.normalize();
			int alpha = MathHelper.clamp((int)(segment.alpha() * 235.0F), 0, 235);
			vertices.vertex(entry, (float)from.x, (float)from.y, (float)from.z)
				.color(ROPE_RED, ROPE_GREEN, ROPE_BLUE, alpha)
				.normal(entry, (float)direction.x, (float)direction.y, (float)direction.z);
			vertices.vertex(entry, (float)to.x, (float)to.y, (float)to.z)
				.color(ROPE_RED, ROPE_GREEN, ROPE_BLUE, alpha)
				.normal(entry, (float)direction.x, (float)direction.y, (float)direction.z);
		}
		matrices.pop();
	}

	private PlayerEntityModel getModel(boolean slim) {
		if (slim) {
			if (this.slimModel == null) {
				this.slimModel = new KidsDollModel(MinecraftClient.getInstance().getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), true);
			}
			return this.slimModel;
		}

		if (this.regularModel == null) {
			this.regularModel = new KidsDollModel(MinecraftClient.getInstance().getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
		}
		return this.regularModel;
	}

	private static KidsDollRenderState createDollRenderState() {
		KidsDollRenderState state = new KidsDollRenderState();
		state.entityType = EntityType.PLAYER;
		state.width = 0.6F;
		state.height = 1.8F;
		state.standingEyeHeight = 1.62F;
		state.baseScale = 1.0F;
		state.ageScale = 1.0F;
		state.pose = EntityPose.STANDING;
		state.mainArm = Arm.RIGHT;
		state.preferredArm = Arm.RIGHT;
		state.activeHand = Hand.MAIN_HAND;
		state.hatVisible = true;
		state.jacketVisible = true;
		state.leftPantsLegVisible = true;
		state.rightPantsLegVisible = true;
		state.leftSleeveVisible = true;
		state.rightSleeveVisible = true;
		return state;
	}

	private static void applyShoulderPose(PlayerEntityModel model, float wave) {
		model.head.pitch = -0.08F;
		model.head.yaw = wave * 0.08F;
		model.body.pitch = 0.04F;
		model.rightArm.pitch = -1.55F + wave * 0.62F;
		model.rightArm.yaw = -0.18F;
		model.rightArm.roll = 0.18F;
		model.leftArm.pitch = -1.55F - wave * 0.62F;
		model.leftArm.yaw = 0.18F;
		model.leftArm.roll = -0.18F;
		model.rightLeg.pitch = -1.38F;
		model.rightLeg.yaw = 0.12F;
		model.leftLeg.pitch = -1.38F;
		model.leftLeg.yaw = -0.12F;
	}

	private static void applyDraggedPose(PlayerEntityModel model, float frantic) {
		model.head.pitch = -0.16F;
		model.head.yaw = frantic * 0.08F;
		model.body.pitch = 0.06F;
		model.rightArm.pitch = -0.82F + frantic * 0.62F;
		model.rightArm.yaw = -0.32F;
		model.rightArm.roll = 0.24F;
		model.leftArm.pitch = -0.82F - frantic * 0.62F;
		model.leftArm.yaw = 0.32F;
		model.leftArm.roll = -0.24F;
		model.rightLeg.pitch = 0.22F;
		model.rightLeg.yaw = 0.12F;
		model.leftLeg.pitch = -0.12F;
		model.leftLeg.yaw = -0.12F;
	}

	private static void showAllParts(PlayerEntityModel model) {
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

	private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
		if (client.world == null) {
			return null;
		}

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.getUuid().equals(uuid)) {
				return player;
			}
		}
		return null;
	}

	private static net.minecraft.client.world.ClientWorld clientWorld(AbstractClientPlayerEntity player) {
		return (net.minecraft.client.world.ClientWorld)player.getEntityWorld();
	}

	private static Vec3d behindForYaw(float yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		return new Vec3d(Math.sin(radians), 0.0, -Math.cos(radians));
	}

	private static float yawFromBehindDirection(Vec3d direction) {
		return (float)Math.toDegrees(Math.atan2(direction.x, -direction.z));
	}

	private static double horizontalDistance(Vec3d from, Vec3d to) {
		double deltaX = to.x - from.x;
		double deltaZ = to.z - from.z;
		return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
	}

	private static Vec3d horizontalDirection(Vec3d from, Vec3d to) {
		double distance = horizontalDistance(from, to);
		if (distance < 0.0001) {
			return Vec3d.ZERO;
		}
		return new Vec3d((to.x - from.x) / distance, 0.0, (to.z - from.z) / distance);
	}

	private static double findGroundY(net.minecraft.client.world.ClientWorld world, double x, double z, double startSurfaceY, double fallbackY) {
		int startY = MathHelper.floor(startSurfaceY);
		int minY = Math.max(world.getBottomY(), startY - 16);
		for (int y = startY; y >= minY; y--) {
			BlockPos blockPos = BlockPos.ofFloored(x, y, z);
			BlockState state = world.getBlockState(blockPos);
			VoxelShape shape = state.getCollisionShape(world, blockPos);
			if (shape.isEmpty()) {
				continue;
			}
			double top = blockPos.getY() + shape.getMax(Direction.Axis.Y);
			if (top <= startSurfaceY) {
				return top;
			}
		}
		return fallbackY;
	}

	private static float smoothStep(float value) {
		float progress = MathHelper.clamp(value, 0.0F, 1.0F);
		return progress * progress * (3.0F - 2.0F * progress);
	}

	private static final class KidsDollRenderState extends PlayerEntityRenderState {
		private boolean shoulderPose;
		private float shoulderWave;
	}

	private static final class KidsDollModel extends PlayerEntityModel {
		private KidsDollModel(ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setAngles(PlayerEntityRenderState state) {
			super.setAngles(state);
			if (state instanceof KidsDollRenderState dollState && dollState.shoulderPose) {
				applyShoulderPose(this, dollState.shoulderWave);
				showAllParts(this);
			}
		}
	}

	private record LocalSettings(boolean enabled, KidsMode mode, int timerSeconds, int ropeSizePercent, KidsTrainFacing trainFacing, boolean animationEnabled, boolean draggedHandMovementEnabled) {
		private static LocalSettings fromConfig(KohsDeathEffectsConfig config) {
			boolean enabled = config.effectsEnabled && config.deathEffectMode == DeathEffectMode.KIDS && config.kidsEnabled;
			int timerSeconds = config.kidsMode == KidsMode.CUMULATIVE
				? config.kidsCumulativeTimerSeconds
				: config.kidsTimerSeconds;
			return new LocalSettings(enabled, config.kidsMode, timerSeconds, config.kidsRopeSizePercent, config.kidsTrainFacing, config.kidsAnimationEnabled, config.kidsDraggedHandMovementEnabled);
		}

		private static LocalSettings fromPayload(KidsEventPayload payload) {
			KidsMode[] modes = KidsMode.values();
			KidsMode mode = modes[Math.max(0, Math.min(modes.length - 1, payload.modeOrdinal()))];
			KidsTrainFacing[] facings = KidsTrainFacing.values();
			KidsTrainFacing trainFacing = facings[Math.max(0, Math.min(facings.length - 1, payload.trainFacingOrdinal()))];
			int minimumTimer = mode == KidsMode.CUMULATIVE ? 20 : 3;
			return new LocalSettings(
				true,
				mode,
				MathHelper.clamp(payload.timerSeconds(), minimumTimer, 301),
				MathHelper.clamp(payload.ropeSizePercent(), 100, 300),
				trainFacing,
				payload.animationEnabled(),
				payload.draggedHandMovementEnabled()
			);
		}

		private KidsSettingsPayload toPayload() {
			return new KidsSettingsPayload(this.enabled, this.mode.ordinal(), this.timerSeconds, this.ropeSizePercent, this.trainFacing.ordinal(), this.animationEnabled, this.draggedHandMovementEnabled);
		}

		private int lifetimeTicks() {
			return this.timerSeconds < INFINITY_TIMER_SECONDS ? this.timerSeconds * 20 : -1;
		}
	}

	private static final class CarrierState {
		private final List<DollState> dolls = new ArrayList<>();
		private final java.util.Set<UUID> seenKills = new java.util.HashSet<>();
		private final Deque<Vec3d> trailPoints = new ArrayDeque<>();
		private LocalSettings settings;
		private Vec3d lastTrailPosition;
		private Vec3d fallbackTrailDirection = new Vec3d(0.0, 0.0, -1.0);
		private int missingCarrierTicks;

		private CarrierState(LocalSettings settings) {
			this.settings = settings;
		}

		private void applySettings(LocalSettings settings) {
			if (this.settings.timerSeconds() != settings.timerSeconds()) {
				int lifetimeTicks = settings.lifetimeTicks();
				for (DollState doll : this.dolls) {
					doll.setRemainingLifetime(lifetimeTicks);
				}
			}
			this.settings = settings;
		}

		private void removeExpiredDolls() {
			for (Iterator<DollState> iterator = this.dolls.iterator(); iterator.hasNext();) {
				DollState doll = iterator.next();
				if (doll.isExpired()) {
					this.seenKills.remove(doll.killUuid);
					iterator.remove();
				}
			}
		}

		private void recordCarrierPosition(Vec3d position, float bodyYaw) {
			Vec3d current = new Vec3d(position.x, position.y, position.z);
			this.fallbackTrailDirection = behindForYaw(bodyYaw);
			if (this.lastTrailPosition == null) {
				this.resetTrail(current);
				return;
			}

			double horizontalDistance = horizontalDistance(this.lastTrailPosition, current);
			if (horizontalDistance > TRAIL_TELEPORT_DISTANCE || Math.abs(current.y - this.lastTrailPosition.y) > TRAIL_TELEPORT_DISTANCE) {
				this.resetTrail(current);
				for (DollState doll : this.dolls) {
					doll.smoothedGroundY = Double.NaN;
				}
				return;
			}

			if (horizontalDistance < TRAIL_MIN_SAMPLE_DISTANCE) {
				return;
			}

			this.trailPoints.addFirst(current);
			this.lastTrailPosition = current;
			this.trimTrail();
		}

		private void resetTrail(Vec3d position) {
			this.trailPoints.clear();
			this.trailPoints.add(position);
			this.lastTrailPosition = position;
		}

		private void trimTrail() {
			double accumulatedDistance = 0.0;
			Vec3d previous = null;
			for (Iterator<Vec3d> iterator = this.trailPoints.iterator(); iterator.hasNext();) {
				Vec3d point = iterator.next();
				if (previous != null) {
					accumulatedDistance += horizontalDistance(previous, point);
					if (accumulatedDistance > MAX_TRAIL_DISTANCE) {
						iterator.remove();
						while (iterator.hasNext()) {
							iterator.next();
							iterator.remove();
						}
						break;
					}
				}
				previous = point;
			}
		}

		private TrailSample sampleTrail(Vec3d currentPosition, double requestedDistance, float bodyYaw) {
			Vec3d previous = currentPosition;
			Vec3d lastDirection = this.fallbackTrailDirection.lengthSquared() > 0.0001
				? this.fallbackTrailDirection
				: behindForYaw(bodyYaw);
			double accumulatedDistance = 0.0;
			boolean skippedNewestPoint = false;
			for (Vec3d point : this.trailPoints) {
				if (!skippedNewestPoint && this.lastTrailPosition != null && horizontalDistance(point, this.lastTrailPosition) < 0.0001) {
					skippedNewestPoint = true;
					continue;
				}
				double segmentDistance = horizontalDistance(previous, point);
				if (segmentDistance < 0.0001) {
					previous = point;
					continue;
				}

				Vec3d direction = horizontalDirection(previous, point);
				if (accumulatedDistance + segmentDistance >= requestedDistance) {
					double progress = (requestedDistance - accumulatedDistance) / segmentDistance;
					return new TrailSample(previous.lerp(point, progress), direction);
				}

				accumulatedDistance += segmentDistance;
				previous = point;
				lastDirection = direction;
			}

			double remainingDistance = Math.max(0.0, requestedDistance - accumulatedDistance);
			return new TrailSample(previous.add(lastDirection.multiply(remainingDistance)), lastDirection);
		}
	}

	private record TrailSample(Vec3d position, Vec3d direction) {
	}

	private record RopeSegment(Vec3d from, Vec3d to, float alpha) {
	}

	private record PreviewShoulderState(int entityId, SkinTextures skin, float wave) {
	}

	private static final class DollState {
		private final UUID killUuid;
		private final UUID victimUuid;
		private final String victimName;
		private SkinTextures skin;
		private int maxAgeTicks;
		private int ageTicks;
		private double smoothedGroundY = Double.NaN;

		private DollState(UUID killUuid, UUID victimUuid, String victimName, SkinTextures skin, int maxAgeTicks) {
			this.killUuid = killUuid;
			this.victimUuid = victimUuid;
			this.victimName = victimName;
			this.skin = skin;
			this.maxAgeTicks = maxAgeTicks;
		}

		private boolean isExpired() {
			return this.maxAgeTicks >= 0 && this.ageTicks >= this.maxAgeTicks;
		}

		private void setRemainingLifetime(int lifetimeTicks) {
			this.maxAgeTicks = lifetimeTicks < 0 ? -1 : this.ageTicks + lifetimeTicks;
		}

		private float alpha() {
			float spawnAlpha = smoothStep(MathHelper.clamp(this.ageTicks / 10.0F, 0.0F, 1.0F));
			if (this.maxAgeTicks < 0) {
				return spawnAlpha;
			}
			int remainingTicks = this.maxAgeTicks - this.ageTicks;
			float fadeAlpha = smoothStep(MathHelper.clamp(remainingTicks / (float)FADE_TICKS, 0.0F, 1.0F));
			return Math.min(spawnAlpha, fadeAlpha);
		}
	}
}
