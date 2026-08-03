package com.kohs.deatheffects.client.effect;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class MorphMobCatalog {
	private static final String DEFAULT_MOB_ID = "minecraft:zombie";

	private MorphMobCatalog() {
	}

	public static List<MobOption> options() {
		return Registries.ENTITY_TYPE.stream()
			.filter(MorphMobCatalog::isMinecraftMob)
			.map(type -> new MobOption(Registries.ENTITY_TYPE.getId(type).toString(), type.getName(), type))
			.sorted(Comparator.comparing(option -> option.name().getString(), String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public static EntityType<?> selectedType(String id) {
		return typeById(id).orElse(EntityType.ZOMBIE);
	}

	public static Text selectedName(String id) {
		return selectedType(id).getName();
	}

	public static Entity createEntity(World world, String id) {
		EntityType<?> type = selectedType(id);
		Entity entity = type.create(world);
		if (entity != null) {
			entity.setNoGravity(true);
		}
		return entity;
	}

	public static LivingEntity createLivingEntity(ClientWorld world, String id) {
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

		return Registries.ENTITY_TYPE.getOrEmpty(identifier);
	}

	private static boolean isMinecraftMob(EntityType<?> type) {
		Identifier id = Registries.ENTITY_TYPE.getId(type);
		return "minecraft".equals(id.getNamespace())
			&& type.isSummonable()
			&& type.getSpawnGroup() != SpawnGroup.MISC;
	}

	public record MobOption(String id, Text name, EntityType<?> type) {
	}
}
