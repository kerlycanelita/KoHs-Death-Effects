package com.kohs.deatheffects.client.mixin;

import com.kohs.deatheffects.client.KohsDeathEffectsClient;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClientMixin {
	@Inject(method = "handleStatus", at = @At("HEAD"))
	private void kohsDeathEffects$handlePlayerDeathStatus(byte status, CallbackInfo ci) {
		if (status == 3 && (Object)this instanceof PlayerEntity player) {
			KohsDeathEffectsClient.onPlayerDeathStatus(player);
		}
	}
}
