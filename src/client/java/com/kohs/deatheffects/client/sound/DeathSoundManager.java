package com.kohs.deatheffects.client.sound;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;

import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.mixin.SoundManagerAccessor;
import com.kohs.deatheffects.client.mixin.SoundSystemAccessor;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.KohsExternalSoundSource;
import net.minecraft.client.sound.SoundExecutor;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class DeathSoundManager {
	private static final Executor DECODE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "KoHs Death Effects Sound Decoder");
		thread.setDaemon(true);
		return thread;
	});
	private static final float ATTENUATION_DISTANCE = 24.0F;
	private static final String BUILTIN_PREFIX = "builtin:";
	private static final String BUILTIN_RESOURCE_ROOT = "/assets/" + KohsDeathEffects.MOD_ID + "/death_sounds/";
	private static final List<String> BUILTIN_SOUND_FILES = List.of(
		"aaaah.mp3",
		"ack.mp3",
		"Bell.mp3",
		"bone-crack.mp3",
		"bruh.mp3",
		"enrique.mp3",
		"error-soundss.mp3",
		"Fah !.mp3",
		"fart.mp3",
		"i-got-this.mp3",
		"metal pipe.mp3",
		"pop.mp3",
		"pop2.mp3",
		"spongebob-fail.mp3",
		"the-undertaker-bell.mp3",
		"Thud.mp3"
	);
	private static final Map<String, CompletableFuture<DecodedSound>> CACHE = new HashMap<>();
	private static final List<SoundFile> SOUND_FILES = new ArrayList<>();
	private static final List<KohsExternalSoundSource.SourceHandle> ACTIVE_SOURCES = new CopyOnWriteArrayList<>();
	private static boolean reflectionFailed;

	private DeathSoundManager() {
	}

	public static void preloadSelected() {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.customDeathSoundId.isBlank()) {
			preload(config.customDeathSoundId);
		}
	}

	private static InputStream openSoundStream(String id) throws IOException {
		if (id.startsWith(BUILTIN_PREFIX)) {
			String fileName = id.substring(BUILTIN_PREFIX.length());
			InputStream inputStream = DeathSoundManager.class.getResourceAsStream(BUILTIN_RESOURCE_ROOT + fileName);
			if (inputStream == null) {
				throw new IOException("Missing builtin death sound " + fileName);
			}
			return inputStream;
		}

		return Files.newInputStream(Path.of(id));
	}

	public static void initialize() {
		ensureCustomSoundDirectory();
		refresh();
		preloadSelected();
	}

	public static synchronized void refresh() {
		List<SoundFile> discovered = discoverSoundFiles();
		SOUND_FILES.clear();
		SOUND_FILES.addAll(discovered);
		preloadSelected();
	}

	public static synchronized List<SoundFile> getSoundFiles() {
		return List.copyOf(SOUND_FILES);
	}

	public static Path getCustomSoundDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve("kohs_death_effects").resolve("sounds");
	}

	public static void openCustomSoundDirectory() {
		Path directory = ensureCustomSoundDirectory();
		Util.getOperatingSystem().open(directory);
	}

	public static void playAt(Vec3d position) {
		KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
		if (!config.customDeathSoundEnabled || config.customDeathSoundId.isBlank()) {
			return;
		}

		CompletableFuture<DecodedSound> soundFuture = preload(config.customDeathSoundId);
		if (!soundFuture.isDone() || soundFuture.isCompletedExceptionally()) {
			return;
		}

		DecodedSound decodedSound;
		try {
			decodedSound = soundFuture.join();
		} catch (CompletionException exception) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		SoundExecutor soundExecutor = getSoundExecutor(client);
		if (soundExecutor == null) {
			return;
		}

		float categoryVolume = client.options.getSoundVolume(SoundCategory.PLAYERS);
		float volume = MathHelper.clamp(config.customDeathSoundVolume * categoryVolume, 0.0F, 2.0F);
		if (volume <= 0.0F) {
			return;
		}

		soundExecutor.execute(() -> {
			StaticSound sound = decodedSound.createSound();
			KohsExternalSoundSource.SourceHandle source = KohsExternalSoundSource.play(sound, position, volume, 1.0F, ATTENUATION_DISTANCE);
			if (source != null) {
				ACTIVE_SOURCES.add(source);
			} else {
				sound.close();
			}
		});
	}

	public static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		SoundExecutor soundExecutor = getSoundExecutor(client);
		if (soundExecutor == null || ACTIVE_SOURCES.isEmpty()) {
			return;
		}

		soundExecutor.execute(() -> {
			for (KohsExternalSoundSource.SourceHandle source : ACTIVE_SOURCES) {
				if (source.isStopped()) {
					source.close();
					ACTIVE_SOURCES.remove(source);
				}
			}
		});
	}

	private static CompletableFuture<DecodedSound> preload(String id) {
		synchronized (DeathSoundManager.class) {
			return CACHE.computeIfAbsent(id, DeathSoundManager::decodeAsync);
		}
	}

	private static CompletableFuture<DecodedSound> decodeAsync(String id) {
		return CompletableFuture.supplyAsync(() -> decode(id), DECODE_EXECUTOR)
			.whenComplete((decodedSound, throwable) -> {
				if (throwable != null || decodedSound == null) {
					KohsDeathEffects.LOGGER.warn("Could not preload custom death sound {}", id, throwable);
					return;
				}

				// Keep decoded PCM warm, but create a fresh StaticSound at play time.
				// Reusing OpenAL-backed StaticSound instances can go stale after long idle periods.
			});
	}

	private static DecodedSound decode(String id) {
		try (InputStream inputStream = openSoundStream(id)) {
			DecodedPcm pcm = decodeMp3(inputStream);
			return new DecodedSound(pcm.bytes(), pcm.format());
		} catch (IOException | JavaLayerException exception) {
			throw new CompletionException(exception);
		}
	}

	private static DecodedPcm decodeMp3(InputStream inputStream) throws JavaLayerException {
		Bitstream bitstream = new Bitstream(inputStream);
		Decoder decoder = new Decoder();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		AudioFormat format = null;

		try {
			Header header;
			while ((header = bitstream.readFrame()) != null) {
				SampleBuffer sampleBuffer = (SampleBuffer)decoder.decodeFrame(header, bitstream);
				if (format == null) {
					format = new AudioFormat(sampleBuffer.getSampleFrequency(), 16, sampleBuffer.getChannelCount(), true, false);
				}

				short[] samples = sampleBuffer.getBuffer();
				int sampleCount = sampleBuffer.getBufferLength();
				for (int index = 0; index < sampleCount; index++) {
					short sample = samples[index];
					output.write(sample & 0xFF);
					output.write(sample >> 8 & 0xFF);
				}
				bitstream.closeFrame();
			}
		} finally {
			bitstream.close();
		}

		if (format == null) {
			throw new JavaLayerException("Empty MP3 stream");
		}

		return new DecodedPcm(output.toByteArray(), format);
	}

	private static List<SoundFile> discoverSoundFiles() {
		Set<Path> directories = new HashSet<>();
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		directories.add(workingDirectory);
		if (workingDirectory.getFileName() != null && "run".equalsIgnoreCase(workingDirectory.getFileName().toString()) && workingDirectory.getParent() != null) {
			directories.add(workingDirectory.getParent());
		}
		directories.add(FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize());
		directories.add(getCustomSoundDirectory().toAbsolutePath().normalize());

		Map<String, SoundFile> filesById = new HashMap<>();
		addBuiltinSounds(filesById);
		for (Path directory : directories) {
			if (Files.isDirectory(directory)) {
				scanDirectory(directory, filesById);
			}
		}

		return filesById.values().stream()
			.sorted(Comparator.comparing(SoundFile::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static void addBuiltinSounds(Map<String, SoundFile> filesById) {
		for (String fileName : BUILTIN_SOUND_FILES) {
			filesById.putIfAbsent(BUILTIN_PREFIX + fileName, new SoundFile(BUILTIN_PREFIX + fileName, displayName(fileName), false));
		}
	}

	private static void scanDirectory(Path directory, Map<String, SoundFile> filesById) {
		try (var stream = Files.list(directory)) {
			stream.filter(Files::isRegularFile)
				.filter(DeathSoundManager::isMp3)
				.forEach(path -> {
					Path normalized = path.toAbsolutePath().normalize();
					boolean custom = isCustomSound(normalized);
					if (!custom && isBuiltinFileName(normalized.getFileName().toString())) {
						return;
					}
					filesById.putIfAbsent(normalized.toString(), new SoundFile(normalized.toString(), displayName(normalized), custom));
				});
		} catch (IOException exception) {
			KohsDeathEffects.LOGGER.warn("Could not scan custom death sounds from {}", directory, exception);
		}
	}

	private static boolean isBuiltinFileName(String fileName) {
		for (String builtinFileName : BUILTIN_SOUND_FILES) {
			if (builtinFileName.equalsIgnoreCase(fileName)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isMp3(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return fileName.endsWith(".mp3");
	}

	private static boolean isCustomSound(Path path) {
		Path customDirectory = getCustomSoundDirectory().toAbsolutePath().normalize();
		return path.startsWith(customDirectory);
	}

	private static String displayName(Path path) {
		String name = path.getFileName().toString();
		return name.substring(0, name.length() - 4);
	}

	private static String displayName(String fileName) {
		return fileName.substring(0, fileName.length() - 4);
	}

	private static Path ensureCustomSoundDirectory() {
		Path directory = getCustomSoundDirectory();
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			KohsDeathEffects.LOGGER.warn("Could not create custom death sound directory {}", directory, exception);
		}
		return directory;
	}

	private static SoundExecutor getSoundExecutor(MinecraftClient client) {
		if (client == null || client.getSoundManager() == null || reflectionFailed) {
			return null;
		}

		try {
			return ((SoundSystemAccessor)((SoundManagerAccessor)client.getSoundManager()).kohsDeathEffects$getSoundSystem()).kohsDeathEffects$getTaskQueue();
		} catch (RuntimeException exception) {
			reflectionFailed = true;
			KohsDeathEffects.LOGGER.warn("Could not access Minecraft sound executor for custom death sounds", exception);
			return null;
		}
	}

	public record SoundFile(String id, String displayName, boolean custom) {
	}

	private record DecodedSound(byte[] bytes, AudioFormat format) {
		private StaticSound createSound() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(this.bytes.length);
			buffer.put(this.bytes);
			buffer.flip();
			return new StaticSound(buffer, this.format);
		}
	}

	private record DecodedPcm(byte[] bytes, AudioFormat format) {
	}
}
