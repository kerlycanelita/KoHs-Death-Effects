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
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

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

		PayloadTypeRegistry.playC2S().register(KidsSettingsPayload.ID, KidsSettingsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(KidsEventPayload.ID, KidsEventPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KidsSettingsPayload.ID, KidsNetworking::receiveSettings);
		ServerLivingEntityEvents.AFTER_DEATH.register(KidsNetworking::afterPlayerDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYER_SETTINGS.remove(handler.player.getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLAYER_SETTINGS.clear());
		registered = true;
	}

	private static void receiveSettings(KidsSettingsPayload payload, ServerPlayNetworking.Context context) {
		ServerKidsSettings next = ServerKidsSettings.from(payload);
		UUID playerUuid = context.player().getUuid();
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

	private static void afterPlayerDeath(net.minecraft.entity.LivingEntity entity, DamageSource damageSource) {
		if (!(entity instanceof ServerPlayerEntity victim)) {
			return;
		}

		Entity attacker = damageSource.getAttacker();
		if (!(attacker instanceof ServerPlayerEntity killer) || killer.getUuid().equals(victim.getUuid())) {
			return;
		}

		ServerKidsSettings settings = PLAYER_SETTINGS.get(killer.getUuid());
		if (settings == null || !settings.enabled()) {
			return;
		}

		broadcast(killer.getServer(), KidsEventPayload.add(
			killer.getUuid(),
			UUID.randomUUID(),
			victim.getUuid(),
			victim.getGameProfile().getName(),
			settings
		));
	}

	private static void broadcast(MinecraftServer server, KidsEventPayload payload) {
		if (server == null) {
			return;
		}

		for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
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
	) implements CustomPayload {
		public static final Id<KidsSettingsPayload> ID = new Id<>(Identifier.of(KohsDeathEffects.MOD_ID, "kids_settings"));
		public static final PacketCodec<RegistryByteBuf, KidsSettingsPayload> CODEC = CustomPayload.codecOf(KidsSettingsPayload::write, KidsSettingsPayload::new);

		private KidsSettingsPayload(RegistryByteBuf buffer) {
			this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
		}

		private void write(RegistryByteBuf buffer) {
			buffer.writeBoolean(this.enabled);
			buffer.writeVarInt(this.modeOrdinal);
			buffer.writeVarInt(this.timerSeconds);
			buffer.writeVarInt(this.ropeSizePercent);
			buffer.writeVarInt(this.trainFacingOrdinal);
			buffer.writeBoolean(this.animationEnabled);
			buffer.writeBoolean(this.draggedHandMovementEnabled);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
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
	) implements CustomPayload {
		public static final Id<KidsEventPayload> ID = new Id<>(Identifier.of(KohsDeathEffects.MOD_ID, "kids_event"));
		public static final PacketCodec<RegistryByteBuf, KidsEventPayload> CODEC = CustomPayload.codecOf(KidsEventPayload::write, KidsEventPayload::new);

		private KidsEventPayload(RegistryByteBuf buffer) {
			this(
				buffer.readVarInt(),
				buffer.readUuid(),
				buffer.readUuid(),
				buffer.readUuid(),
				buffer.readString(MAX_NAME_LENGTH),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readBoolean(),
				buffer.readBoolean()
			);
		}

		private void write(RegistryByteBuf buffer) {
			buffer.writeVarInt(this.action);
			buffer.writeUuid(this.carrierUuid);
			buffer.writeUuid(this.killUuid);
			buffer.writeUuid(this.victimUuid);
			buffer.writeString(this.victimName, MAX_NAME_LENGTH);
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
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
