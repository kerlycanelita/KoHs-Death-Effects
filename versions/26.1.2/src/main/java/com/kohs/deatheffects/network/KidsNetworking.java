package com.kohs.deatheffects.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig.KidsMode;
import com.kohs.deatheffects.KohsDeathEffectsConfig.KidsTrainFacing;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public final class KidsNetworking {
	public static final int ACTION_ADD = 0;
	public static final int ACTION_CLEAR = 1;
	public static final int ACTION_SETTINGS = 2;
	private static final int MAX_NAME_LENGTH = 64;
	private static final UUID EMPTY_UUID = new UUID(0L, 0L);
	private static final Map<UUID, ServerKidsSettings> PLAYER_SETTINGS = new HashMap<>();
	private static boolean registered;

	private KidsNetworking() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		PayloadTypeRegistry.serverboundPlay().register(KidsSettingsPayload.ID, KidsSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(KidsEventPayload.ID, KidsEventPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KidsSettingsPayload.ID, KidsNetworking::receiveSettings);
		ServerLivingEntityEvents.AFTER_DEATH.register(KidsNetworking::afterPlayerDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYER_SETTINGS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLAYER_SETTINGS.clear());
		registered = true;
	}

	private static void receiveSettings(KidsSettingsPayload payload, ServerPlayNetworking.Context context) {
		ServerKidsSettings next = ServerKidsSettings.from(payload);
		UUID playerUuid = context.player().getUUID();
		ServerKidsSettings previous = PLAYER_SETTINGS.get(playerUuid);
		if (next.equals(previous)) {
			return;
		}
		PLAYER_SETTINGS.put(playerUuid, next);
		boolean reset = !next.enabled()
			|| previous != null && (previous.enabled() != next.enabled() || previous.mode() != next.mode());
		if (reset) {
			broadcast(context.server(), KidsEventPayload.clear(playerUuid, next));
		} else {
			broadcast(context.server(), KidsEventPayload.settings(playerUuid, next));
		}
	}

	private static void afterPlayerDeath(net.minecraft.world.entity.LivingEntity entity, DamageSource damageSource) {
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}

		Entity attacker = damageSource.getEntity();
		if (!(attacker instanceof ServerPlayer killer) || killer.getUUID().equals(victim.getUUID())) {
			return;
		}

		ServerKidsSettings settings = PLAYER_SETTINGS.get(killer.getUUID());
		if (settings == null || !settings.enabled()) {
			return;
		}

		broadcast(killer.level().getServer(), KidsEventPayload.add(
			killer.getUUID(),
			UUID.randomUUID(),
			victim.getUUID(),
			victim.getGameProfile().name(),
			settings
		));
	}

	private static void broadcast(MinecraftServer server, KidsEventPayload payload) {
		if (server == null) {
			return;
		}

		for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(receiver, KidsEventPayload.ID)) {
				ServerPlayNetworking.send(receiver, payload);
			}
		}
	}

	private record ServerKidsSettings(boolean enabled, KidsMode mode, int timerSeconds, int ropeSizePercent, KidsTrainFacing trainFacing, boolean animationEnabled, boolean draggedHandMovementEnabled) {
		private static ServerKidsSettings from(KidsSettingsPayload payload) {
			KidsMode[] modes = KidsMode.values();
			KidsMode mode = modes[Math.max(0, Math.min(modes.length - 1, payload.modeOrdinal()))];
			KidsTrainFacing[] facings = KidsTrainFacing.values();
			KidsTrainFacing trainFacing = facings[Math.max(0, Math.min(facings.length - 1, payload.trainFacingOrdinal()))];
			int minimumTimer = mode == KidsMode.CUMULATIVE ? 20 : 3;
			return new ServerKidsSettings(
				payload.enabled(),
				mode,
				Math.max(minimumTimer, Math.min(301, payload.timerSeconds())),
				Math.max(100, Math.min(300, payload.ropeSizePercent())),
				trainFacing,
				payload.animationEnabled(),
				payload.draggedHandMovementEnabled()
			);
		}
	}

	public record KidsSettingsPayload(
		boolean enabled,
		int modeOrdinal,
		int timerSeconds,
		int ropeSizePercent,
		int trainFacingOrdinal,
		boolean animationEnabled,
		boolean draggedHandMovementEnabled
	) implements CustomPacketPayload {
		public static final Type<KidsSettingsPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "kids_settings"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KidsSettingsPayload> CODEC = CustomPacketPayload.codec(KidsSettingsPayload::write, KidsSettingsPayload::new);

		private KidsSettingsPayload(RegistryFriendlyByteBuf buffer) {
			this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
		}

		private void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeBoolean(this.enabled);
			buffer.writeVarInt(this.modeOrdinal);
			buffer.writeVarInt(this.timerSeconds);
			buffer.writeVarInt(this.ropeSizePercent);
			buffer.writeVarInt(this.trainFacingOrdinal);
			buffer.writeBoolean(this.animationEnabled);
			buffer.writeBoolean(this.draggedHandMovementEnabled);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	public record KidsEventPayload(
		int action,
		UUID carrierUuid,
		UUID killUuid,
		UUID victimUuid,
		String victimName,
		int modeOrdinal,
		int timerSeconds,
		int ropeSizePercent,
		int trainFacingOrdinal,
		boolean animationEnabled,
		boolean draggedHandMovementEnabled
	) implements CustomPacketPayload {
		public static final Type<KidsEventPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "kids_event"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KidsEventPayload> CODEC = CustomPacketPayload.codec(KidsEventPayload::write, KidsEventPayload::new);

		private KidsEventPayload(RegistryFriendlyByteBuf buffer) {
			this(
				buffer.readVarInt(),
				buffer.readUUID(),
				buffer.readUUID(),
				buffer.readUUID(),
				buffer.readUtf(MAX_NAME_LENGTH),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readBoolean(),
				buffer.readBoolean()
			);
		}

		private void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(this.action);
			buffer.writeUUID(this.carrierUuid);
			buffer.writeUUID(this.killUuid);
			buffer.writeUUID(this.victimUuid);
			buffer.writeUtf(this.victimName, MAX_NAME_LENGTH);
			buffer.writeVarInt(this.modeOrdinal);
			buffer.writeVarInt(this.timerSeconds);
			buffer.writeVarInt(this.ropeSizePercent);
			buffer.writeVarInt(this.trainFacingOrdinal);
			buffer.writeBoolean(this.animationEnabled);
			buffer.writeBoolean(this.draggedHandMovementEnabled);
		}

		private static KidsEventPayload add(UUID carrierUuid, UUID killUuid, UUID victimUuid, String victimName, ServerKidsSettings settings) {
			return create(ACTION_ADD, carrierUuid, killUuid, victimUuid, victimName, settings);
		}

		private static KidsEventPayload clear(UUID carrierUuid, ServerKidsSettings settings) {
			return create(ACTION_CLEAR, carrierUuid, EMPTY_UUID, EMPTY_UUID, "", settings);
		}

		private static KidsEventPayload settings(UUID carrierUuid, ServerKidsSettings settings) {
			return create(ACTION_SETTINGS, carrierUuid, EMPTY_UUID, EMPTY_UUID, "", settings);
		}

		private static KidsEventPayload create(int action, UUID carrierUuid, UUID killUuid, UUID victimUuid, String victimName, ServerKidsSettings settings) {
			return new KidsEventPayload(
				action,
				carrierUuid,
				killUuid,
				victimUuid,
				victimName == null ? "" : victimName,
				settings.mode().ordinal(),
				settings.timerSeconds(),
				settings.ropeSizePercent(),
				settings.trainFacing().ordinal(),
				settings.animationEnabled(),
				settings.draggedHandMovementEnabled()
			);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}
}
