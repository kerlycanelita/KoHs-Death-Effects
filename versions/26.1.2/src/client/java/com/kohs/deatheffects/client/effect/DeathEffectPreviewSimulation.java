package com.kohs.deatheffects.client.effect;

import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.KohsDeathEffectsConfig.DeathEffectMode;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Runs the same effect object created by {@link DeathEffectManager#spawnForDeath}
 * on a disposable preview player. Rendering remains inside the config screen,
 * while timing, fades, movement phases and effect snapshots come from the real
 * death-effect implementation.
 */
public final class DeathEffectPreviewSimulation {
	private RisingSilhouetteEffect effect;
	private Player previewPlayer;
	private KohsDeathEffectsConfig config;
	private long lastUpdateMillis;
	private long tickAccumulatorMillis;

	public void restart(Player player, KohsDeathEffectsConfig config) {
		this.previewPlayer = player;
		this.config = config;
		this.lastUpdateMillis = Util.getMillis();
		this.tickAccumulatorMillis = 0L;
		this.effect = this.createEffect();
	}

	public RisingSilhouetteEffect.PreviewFrame frame(boolean paused) {
		if (this.effect == null) {
			return null;
		}

		this.update(paused);
		RisingSilhouetteEffect.PreviewFrame frame = this.effect.previewFrame(this.tickAccumulatorMillis / 50.0F);
		if (frame.expired()) {
			this.effect = this.createEffect();
			this.tickAccumulatorMillis = 0L;
			this.lastUpdateMillis = Util.getMillis();
			return this.effect == null ? null : this.effect.previewFrame(0.0F);
		}

		return frame;
	}

	public void clear() {
		this.effect = null;
		this.previewPlayer = null;
		this.config = null;
		this.tickAccumulatorMillis = 0L;
	}

	private void update(boolean paused) {
		long now = Util.getMillis();
		long elapsedMillis = Math.max(0L, Math.min(1000L, now - this.lastUpdateMillis));
		this.lastUpdateMillis = now;
		if (paused) {
			return;
		}

		this.tickAccumulatorMillis += elapsedMillis;
		int ticks = 0;
		while (this.tickAccumulatorMillis >= 50L && ticks++ < 20) {
			this.effect.tick();
			this.tickAccumulatorMillis -= 50L;
		}
	}

	private RisingSilhouetteEffect createEffect() {
		if (this.previewPlayer == null || this.config == null || this.config.deathEffectMode == DeathEffectMode.KIDS) {
			return null;
		}

		return RisingSilhouetteEffect.from(this.previewPlayer, this.config, false, null);
	}
}
