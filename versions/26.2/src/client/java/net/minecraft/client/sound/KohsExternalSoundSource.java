package net.minecraft.client.sound;

import com.kohs.deatheffects.client.mixin.SourceAccessor;
import com.kohs.deatheffects.client.mixin.StaticSoundAccessor;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.world.phys.Vec3;

public final class KohsExternalSoundSource {
	private KohsExternalSoundSource() {
	}

	public static SourceHandle play(SoundBuffer sound, Vec3 position, float volume, float pitch, float attenuationDistance) {
		Channel source = SourceAccessor.kohsDeathEffects$create();
		if (source == null) {
			return null;
		}

		source.setPitch(pitch);
		source.setVolume(volume);
		source.linearAttenuation(attenuationDistance);
		source.setLooping(false);
		source.setSelfPosition(position);
		source.setRelative(false);
		source.attachStaticBuffer(sound);
		source.play();
		return new SourceHandle(source, sound);
	}

	public static boolean preload(SoundBuffer sound) {
		return ((StaticSoundAccessor)(Object)sound).kohsDeathEffects$getStreamBufferPointer().isPresent();
	}

	public static final class SourceHandle {
		private final Channel source;
		private final SoundBuffer sound;

		private SourceHandle(Channel source, SoundBuffer sound) {
			this.source = source;
			this.sound = sound;
		}

		public boolean isStopped() {
			return this.source.stopped();
		}

		public void close() {
			this.source.destroy();
			this.sound.discardAlBuffer();
		}
	}
}

