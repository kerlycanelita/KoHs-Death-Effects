package com.kohs.deatheffects.client.mixin;

import com.kohs.deatheffects.client.KohsDeathEffectsClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClientMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void kohsDeathEffects$handlePlayerDeathStatus(byte status, CallbackInfo ci) {
		if (status == 3 && (Object)this instanceof Player player) {
			KohsDeathEffectsClient.onPlayerDeathStatus(player);
		}
	}
}

