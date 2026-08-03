package net.minecraft.client.sound;

import com.kohs.deatheffects.client.mixin.SourceAccessor;
import com.kohs.deatheffects.client.mixin.StaticSoundAccessor;

import net.minecraft.util.math.Vec3d;

public final class KohsExternalSoundSource {
	private KohsExternalSoundSource() {
	}

	public static SourceHandle play(StaticSound sound, Vec3d position, float volume, float pitch, float attenuationDistance) {
		Source source = SourceAccessor.kohsDeathEffects$create();
		if (source == null) {
			return null;
		}

		source.setPitch(pitch);
		source.setVolume(volume);
		source.setAttenuation(attenuationDistance);
		source.setLooping(false);
		source.setPosition(position);
		source.setRelative(false);
		source.setBuffer(sound);
		source.play();
		return new SourceHandle(source, sound);
	}

	public static boolean preload(StaticSound sound) {
		return ((StaticSoundAccessor)(Object)sound).kohsDeathEffects$getStreamBufferPointer().isPresent();
	}

	public static final class SourceHandle {
		private final Source source;
		private final StaticSound sound;

		private SourceHandle(Source source, StaticSound sound) {
			this.source = source;
			this.sound = sound;
		}

		public boolean isStopped() {
			return this.source.isStopped();
		}

		public void close() {
			this.source.close();
			this.sound.close();
		}
	}
}
