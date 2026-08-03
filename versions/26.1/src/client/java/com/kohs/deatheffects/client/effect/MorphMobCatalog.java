package com.kohs.deatheffects.client.effect;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

public final class MorphMobCatalog {
	private static final String DEFAULT_MOB_ID = "minecraft:zombie";

	private MorphMobCatalog() {
	}

	public static List<MobOption> options() {
		return BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(MorphMobCatalog::isMinecraftMob)
			.map(type -> new MobOption(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription(), type))
			.sorted(Comparator.comparing(option -> option.name().getString(), String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public static EntityType<?> selectedType(String id) {
		return typeById(id).orElse(EntityType.ZOMBIE);
	}

	public static Component selectedName(String id) {
		return selectedType(id).getDescription();
	}

	public static Entity createEntity(Level world, String id) {
		EntityType<?> type = selectedType(id);
		Entity entity = type.create(world, EntitySpawnReason.TRIGGERED);
		if (entity != null) {
			entity.setNoGravity(true);
		}
		return entity;
	}

	public static LivingEntity createLivingEntity(ClientLevel world, String id) {
		Entity entity = createEntity(world, id);
		return entity instanceof LivingEntity livingEntity ? livingEntity : null;
	}

	public static boolean isValidMobId(String id) {
		return typeById(id).filter(MorphMobCatalog::isMinecraftMob).isPresent();
	}

	public static String sanitizeMobId(String id) {
		return isValidMobId(id) ? id : DEFAULT_MOB_ID;
	}

	private static Optional<EntityType<?>> typeById(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return Optional.empty();
		}

		return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier);
	}

	private static boolean isMinecraftMob(EntityType<?> type) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return "minecraft".equals(id.getNamespace())
			&& type.canSummon()
			&& type.getCategory() != MobCategory.MISC;
	}

	public record MobOption(String id, Component name, EntityType<?> type) {
	}
}

