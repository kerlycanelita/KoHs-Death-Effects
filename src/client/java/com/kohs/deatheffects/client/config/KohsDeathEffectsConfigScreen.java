package com.kohs.deatheffects.client.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.effect.MorphMobCatalog;
import com.kohs.deatheffects.client.effect.MorphMobSoundPlayer;
import com.kohs.deatheffects.client.sound.DeathSoundManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

public final class KohsDeathEffectsConfigScreen extends Screen {
	private static final int ROW_STEP = 34;
	private static final int CHILD_INDENT = 14;
	private static final String PREVIEW_PLAYER_NAME = "zymekoh";
	private static final UUID PREVIEW_FALLBACK_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + PREVIEW_PLAYER_NAME).getBytes(StandardCharsets.UTF_8));
	private static final GameProfile PREVIEW_FALLBACK_PROFILE = new GameProfile(PREVIEW_FALLBACK_UUID, PREVIEW_PLAYER_NAME);
	private static final HttpClient SKIN_HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Identifier SILHOUETTE_PREVIEW_TEXTURE_ID = Identifier.of(KohsDeathEffects.MOD_ID, "preview/silhouette");
	private static CompletableFuture<SkinTextures> previewSkinFuture;
	private static SkinTextures previewSkinTextures = DefaultSkinHelper.getSkinTextures(PREVIEW_FALLBACK_PROFILE);
	private static NativeImageBackedTexture silhouettePreviewTexture;
	private static int silhouettePreviewArgb = Integer.MIN_VALUE;
	private static final int[] PRESET_COLORS = {
		0xB96BFF,
		0x7C3CFF,
		0x55D7FF,
		0xD86BFF,
		0x76FF9A,
		0xFFD166,
		0xFF6363,
		0xF8F8F2
	};
	private static final int COLOR_SCREEN_SHADE = 0xD60A0613;
	private static final int COLOR_PANEL = 0xF0140A24;
	private static final int COLOR_PANEL_DARK = 0xF00C0616;
	private static final int COLOR_PANEL_SOFT = 0xE51D0E35;
	private static final int COLOR_PURPLE = 0xFF9D63FF;
	private static final int COLOR_PURPLE_SOFT = 0x775E2DA8;
	private static final int COLOR_PURPLE_DARK = 0xFF2A1648;
	private static final int COLOR_BORDER = 0xFF7043B9;
	private static final int COLOR_TEXT = 0xFFF4ECFF;
	private static final int COLOR_TEXT_MUTED = 0xFFCDBAE8;
	private static final int COLOR_TEXT_DIM = 0xFF9F8ABF;

	private final Screen parent;
	private final List<Swatch> swatches = new ArrayList<>();
	private KohsDeathEffectsConfig config;
	private MainTab currentTab = MainTab.EFFECTS;
	private EffectTab currentEffectTab;
	private TextFieldWidget colorField;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;
	private int optionsX;
	private int optionsY;
	private int optionsWidth;
	private int optionsScrollTop;
	private int optionsScrollBottom;
	private int scrollOffset;
	private int maxScroll;
	private long previewStartedAt;
	private PreviewPlayerEntity previewPlayer;
	private LivingEntity previewMorphEntity;
	private String previewMorphEntityId = "";
	private float morphPreviewYawOffset;
	private float morphPreviewPitchOffset;
	private float morphPreviewZoom = 1.0F;
	private PlayerEntityModel silhouettePreviewModel;
	private boolean silhouettePreviewModelSlim;
	private PlayerEntityModel ragdollPreviewModel;
	private boolean ragdollPreviewModelSlim;

	public KohsDeathEffectsConfigScreen(Screen parent) {
		super(Text.literal("KoHs Death Effects"));
		this.parent = parent;
		this.config = KohsDeathEffectsConfig.get();
		this.currentEffectTab = effectTabFromMode(this.config.deathEffectMode);
		this.activateOnlyEffect(this.currentEffectTab, false);
		this.previewStartedAt = Util.getMeasuringTimeMs();
		requestPreviewSkin();
	}

	@Override
	protected void init() {
		this.computeLayout();
		this.updateScrollBounds();
		this.swatches.clear();
		this.colorField = null;
		this.addMainTabButtons();

		if (this.currentTab == MainTab.EFFECTS) {
			this.addEffectSubTabs();
			this.addPreviewControls();
			this.initEffectOptions();
		} else if (this.currentTab == MainTab.SOUND) {
			this.initSoundTab();
		} else {
			this.initAdvancedTab();
		}

		this.addDrawableChild(this.purpleButton(Text.literal("Listo"), button -> this.close(),
			this.panelX + this.panelWidth - 90, this.panelY + this.panelHeight - 30, 76, 20, ButtonTone.PRIMARY));
	}

	private void initSoundTab() {
		DeathSoundManager.refresh();
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop();

		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.customDeathSoundEnabled), button -> {
			if (this.config.customDeathSoundEnabled) {
				this.config.customDeathSoundEnabled = false;
				this.config.save();
				this.clearAndInit();
				return;
			}

			if (this.config.morphMobSoundEnabled && this.client != null) {
				this.client.setScreen(new CustomSoundWarningScreen(this, null));
			} else {
				this.applyCustomDeathSound(null);
				this.clearAndInit();
			}
		}, controlX, y, 104, 20, this.config.customDeathSoundEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("Carpeta"), button -> DeathSoundManager.openCustomSoundDirectory(),
			controlX - 112, y, 104, 20, ButtonTone.NORMAL));
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("Recargar"), button -> {
			DeathSoundManager.refresh();
			this.clearAndInit();
		}, controlX, y, 104, 20, ButtonTone.NORMAL));

		y += ROW_STEP;
		y += 24;
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("Ninguno"), button -> {
			this.config.customDeathSoundId = "";
			this.config.save();
			this.clearAndInit();
		}, this.optionsX + CHILD_INDENT, y, Math.max(104, this.optionsWidth - CHILD_INDENT), 20, this.config.customDeathSoundId.isBlank() ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		for (DeathSoundManager.SoundFile soundFile : DeathSoundManager.getSoundFiles()) {
			y += 24;
			boolean selected = soundFile.id().equals(this.config.customDeathSoundId);
			String prefix = selected ? "> " : "";
			String suffix = soundFile.custom() ? "  [custom]" : "";
			this.addScrolledDrawableChild(this.purpleButton(Text.literal(prefix + trimButtonLabel(soundFile.displayName() + suffix, 34)), button -> {
				if (this.config.morphMobSoundEnabled && this.client != null) {
					this.client.setScreen(new CustomSoundWarningScreen(this, soundFile.id()));
				} else {
					this.applyCustomDeathSound(soundFile.id());
					this.clearAndInit();
				}
			}, this.optionsX + CHILD_INDENT, y, Math.max(104, this.optionsWidth - CHILD_INDENT), 20, selected ? ButtonTone.SELECTED : ButtonTone.NORMAL));
		}
	}

	private void applyCustomDeathSound(String soundId) {
		if (soundId != null) {
			this.config.customDeathSoundId = soundId;
		}
		this.config.customDeathSoundEnabled = true;
		this.config.morphMobSoundEnabled = false;
		this.config.save();
		DeathSoundManager.preloadSelected();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		this.drawFrame(context);
		this.drawContent(context, mouseX, mouseY);
		super.render(context, mouseX, mouseY, deltaTicks);
		this.drawScrollbar(context);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.currentTab == MainTab.EFFECTS && this.currentEffectTab == EffectTab.SILHOUETTE) {
			for (Swatch swatch : this.swatches) {
				if (swatch.contains(mouseX, mouseY)) {
					this.config.silhouetteColor = swatch.color();
					this.config.save();

					if (this.colorField != null) {
						this.colorField.setText(formatColor(this.config.silhouetteColor));
					}

					return true;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.previewCanOrbit() && this.isMouseOverPreview(mouseX, mouseY)) {
			this.morphPreviewZoom = MathHelper.clamp(this.morphPreviewZoom + (float)verticalAmount * 0.12F, 0.45F, 2.5F);
			return true;
		}

		if (this.maxScroll > 0 && this.isMouseOverOptions(mouseX, mouseY)) {
			this.scrollOffset = MathHelper.clamp(this.scrollOffset - (int)(verticalAmount * 24.0), 0, this.maxScroll);
			this.clearAndInit();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && this.previewCanOrbit() && this.isMouseOverPreview(mouseX, mouseY)) {
			this.morphPreviewYawOffset = MathHelper.clamp(this.morphPreviewYawOffset + (float)deltaX * 1.6F, -140.0F, 140.0F);
			this.morphPreviewPitchOffset = MathHelper.clamp(this.morphPreviewPitchOffset + (float)deltaY * 1.2F, -80.0F, 80.0F);
			return true;
		}

		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		this.config.save();
		this.client.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.previewPlayer = null;
		this.previewMorphEntity = null;
	}

	private void addMainTabButtons() {
		int x = this.panelX + 14;
		int y = this.panelY + 30;
		int gap = this.panelWidth < 360 ? 5 : 8;
		int width = MathHelper.clamp((this.panelWidth - 28 - gap * (MainTab.values().length - 1)) / MainTab.values().length, 54, 112);

		for (MainTab tab : MainTab.values()) {
			this.addDrawableChild(this.purpleButton(Text.literal(tab.label), button -> {
				this.currentTab = tab;
				this.scrollOffset = 0;
				this.clearAndInit();
			}, x, y, width, 20, this.currentTab == tab ? ButtonTone.SELECTED : ButtonTone.NORMAL));
			x += width + gap;
		}
	}

	private void addEffectSubTabs() {
		int gap = this.optionsWidth < 300 ? 4 : 8;
		int minWidth = this.optionsWidth < 340 ? 54 : 78;
		int buttonWidth = MathHelper.clamp((this.optionsWidth - gap * (EffectTab.values().length - 1)) / EffectTab.values().length, minWidth, 150);
		int x = this.optionsX;
		int y = this.optionsY;

		for (EffectTab tab : EffectTab.values()) {
			this.addDrawableChild(this.purpleButton(Text.literal(tab.label), button -> this.selectEffectTab(tab),
				x, y, buttonWidth, 20, this.currentEffectTab == tab ? ButtonTone.SELECTED : ButtonTone.NORMAL));
			x += buttonWidth + gap;
		}
	}

	private void selectEffectTab(EffectTab tab) {
		this.currentEffectTab = tab;
		this.activateOnlyEffect(tab, true);
		this.scrollOffset = 0;
		this.morphPreviewYawOffset = 0.0F;
		this.morphPreviewPitchOffset = 0.0F;
		this.morphPreviewZoom = 1.0F;
		this.restartPreview();
		this.clearAndInit();
	}

	private void activateOnlyEffect(EffectTab tab, boolean save) {
		this.config.effectsEnabled = true;
		this.config.deathEffectMode = tab.mode;
		this.config.risingSilhouetteEnabled = tab == EffectTab.SILHOUETTE;
		this.config.playerGhostEnabled = tab == EffectTab.PLAYER;
		this.config.ragdollEnabled = tab == EffectTab.RAGDOLL;
		this.config.morphEnabled = tab == EffectTab.MORPH;
		if (save) {
			this.config.save();
		}
	}

	private void addPreviewControls() {
		if (this.previewWidth <= 0 || this.previewHeight < 74) {
			return;
		}

		int buttonWidth = MathHelper.clamp(this.previewWidth - 20, 64, 92);
		if (this.currentEffectTab == EffectTab.MORPH) {
			this.addDrawableChild(this.purpleButton(Text.literal("Play sound"), button -> this.playPreviewMorphSound(),
				this.previewX + 10, this.previewY + this.previewHeight - 26, buttonWidth, 20, ButtonTone.SMALL));
		} else {
			this.addDrawableChild(this.purpleButton(Text.literal("Reiniciar"), button -> this.restartPreview(),
				this.previewX + 10, this.previewY + this.previewHeight - 26, buttonWidth, 20, ButtonTone.SMALL));
		}
	}

	private void playPreviewMorphSound() {
		if (this.client == null || this.client.player == null) {
			return;
		}

		MorphMobSoundPlayer.playConfigured(this.client.player.getPos(), this.config);
	}

	private void initEffectOptions() {
		switch (this.currentEffectTab) {
			case SILHOUETTE -> this.initSilhouetteOptions();
			case PLAYER -> this.initPlayerOptions();
			case RAGDOLL -> this.initRagdollOptions();
			case MORPH -> this.initMorphOptions();
		}
	}

	private void initSilhouetteOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.colorField = new TextFieldWidget(this.textRenderer, controlX - 10, y, 114, 20, Text.literal("Color"));
		this.colorField.setMaxLength(7);
		this.colorField.setPlaceholder(Text.literal("#B96BFF"));
		this.colorField.setText(formatColor(this.config.silhouetteColor));
		this.colorField.setDrawsBackground(false);
		this.colorField.setEditableColor(COLOR_TEXT);
		this.colorField.setChangedListener(this::applyColorText);
		this.addScrolledDrawableChild(this.colorField);

		int swatchY = this.scrolledY(y + 42);
		int swatchX = this.optionsX + CHILD_INDENT;
		for (int color : PRESET_COLORS) {
			if (this.isFullyVisible(swatchY, 18)) {
				this.swatches.add(new Swatch(swatchX, swatchY, 18, color));
			}
			swatchX += 24;
		}

		y += 88;
		this.addStepButtons(controlX, y, () -> {
			this.config.silhouetteAlpha = Math.max(0.05F, this.config.silhouetteAlpha - 0.05F);
			this.config.save();
		}, () -> {
			this.config.silhouetteAlpha = Math.min(1.0F, this.config.silhouetteAlpha + 0.05F);
			this.config.save();
		});

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.silhouetteScale = Math.max(0.5F, this.config.silhouetteScale - 0.1F);
			this.config.save();
		}, () -> {
			this.config.silhouetteScale = Math.min(2.5F, this.config.silhouetteScale + 0.1F);
			this.config.save();
		});

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.silhouetteDurationSeconds = Math.max(1, this.config.silhouetteDurationSeconds - 1);
			this.config.save();
		}, () -> {
			this.config.silhouetteDurationSeconds = Math.min(60, this.config.silhouetteDurationSeconds + 1);
			this.config.save();
		});

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.silhouetteRiseHeight = Math.max(0.5F, this.config.silhouetteRiseHeight - 0.5F);
			this.config.save();
		}, () -> {
			this.config.silhouetteRiseHeight = Math.min(64.0F, this.config.silhouetteRiseHeight + 0.5F);
			this.config.save();
		});
	}

	private void initPlayerOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(movementText(this.config.playerGhostMovement), button -> {
			this.config.playerGhostMovement = this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING
				? KohsDeathEffectsConfig.GhostMovementMode.STATIC
				: KohsDeathEffectsConfig.GhostMovementMode.RISING;
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.playerGhostDurationSeconds = Math.max(1, this.config.playerGhostDurationSeconds - 1);
			this.config.save();
		}, () -> {
			this.config.playerGhostDurationSeconds = Math.min(60, this.config.playerGhostDurationSeconds + 1);
			this.config.save();
		});

		if (this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING) {
			y += ROW_STEP;
			this.addStepButtons(controlX, y, () -> {
				this.config.playerGhostRiseHeight = Math.max(0.5F, this.config.playerGhostRiseHeight - 0.5F);
				this.config.save();
			}, () -> {
				this.config.playerGhostRiseHeight = Math.min(64.0F, this.config.playerGhostRiseHeight + 0.5F);
				this.config.save();
			});
		}

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.playerGhostAlpha = Math.max(0.05F, this.config.playerGhostAlpha - 0.05F);
			this.config.save();
		}, () -> {
			this.config.playerGhostAlpha = Math.min(1.0F, this.config.playerGhostAlpha + 0.05F);
			this.config.save();
		});

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.playerGhostArmorEnabled), button -> {
			this.config.playerGhostArmorEnabled = !this.config.playerGhostArmorEnabled;
			button.setMessage(onOff(this.config.playerGhostArmorEnabled));
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.playerGhostArmorEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.playerGhostHeldItemsEnabled), button -> {
			this.config.playerGhostHeldItemsEnabled = !this.config.playerGhostHeldItemsEnabled;
			button.setMessage(onOff(this.config.playerGhostHeldItemsEnabled));
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.playerGhostHeldItemsEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));
	}

	private void initRagdollOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.ragdollDurationSeconds = Math.max(1, this.config.ragdollDurationSeconds - 1);
			this.config.save();
		}, () -> {
			this.config.ragdollDurationSeconds = Math.min(60, this.config.ragdollDurationSeconds + 1);
			this.config.save();
		});

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.ragdollFadeEnabled), button -> {
			this.config.ragdollFadeEnabled = !this.config.ragdollFadeEnabled;
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.ragdollFadeEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.ragdollFadeEnabled) {
			y += ROW_STEP;
			this.addStepButtons(controlX, y, () -> {
				this.config.ragdollFadeDurationSeconds = Math.max(5, this.config.ragdollFadeDurationSeconds - 1);
				this.config.save();
			}, () -> {
				this.config.ragdollFadeDurationSeconds = Math.min(60, this.config.ragdollFadeDurationSeconds + 1);
				this.config.save();
			});
		}

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.ragdollClientCollisionEnabled), button -> {
			this.config.ragdollClientCollisionEnabled = !this.config.ragdollClientCollisionEnabled;
			button.setMessage(onOff(this.config.ragdollClientCollisionEnabled));
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.ragdollClientCollisionEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.ragdollExplosionImpulseEnabled), button -> {
			this.config.ragdollExplosionImpulseEnabled = !this.config.ragdollExplosionImpulseEnabled;
			button.setMessage(onOff(this.config.ragdollExplosionImpulseEnabled));
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.ragdollExplosionImpulseEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));
	}

	private void initMorphOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("Morph to"), button -> {
			if (this.client != null) {
				this.client.setScreen(new MorphToScreen(this));
			}
		}, controlX, y, 104, 20, ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addStepButtons(controlX, y, () -> {
			this.config.morphAlpha = Math.max(0.05F, this.config.morphAlpha - 0.05F);
			this.config.save();
		}, () -> {
			this.config.morphAlpha = Math.min(1.0F, this.config.morphAlpha + 0.05F);
			this.config.save();
		});

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.morphElevationEnabled), button -> {
			this.config.morphElevationEnabled = !this.config.morphElevationEnabled;
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.morphElevationEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.morphElevationEnabled) {
			y += ROW_STEP;
			this.addStepButtons(controlX, y, () -> {
				this.config.morphElevationTimeSeconds = Math.max(1, this.config.morphElevationTimeSeconds - 1);
				this.config.save();
			}, () -> {
				this.config.morphElevationTimeSeconds = Math.min(60, this.config.morphElevationTimeSeconds + 1);
				this.config.save();
			});
		}

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.morphMobSoundEnabled), button -> {
			if (this.config.morphMobSoundEnabled) {
				this.config.morphMobSoundEnabled = false;
				this.config.save();
				this.clearAndInit();
				return;
			}

			if (this.client != null) {
				this.client.setScreen(new MorphSoundWarningScreen(this));
			}
		}, controlX, y, 104, 20, this.config.morphMobSoundEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.morphMobSoundEnabled) {
			y += ROW_STEP;
			this.addStepButtons(controlX, y, () -> {
				this.config.morphMobSoundVolume = Math.max(0, this.config.morphMobSoundVolume - 10);
				this.config.save();
			}, () -> {
				this.config.morphMobSoundVolume = Math.min(300, this.config.morphMobSoundVolume + 10);
				this.config.save();
			});

			y += ROW_STEP;
			this.addStepButtons(controlX, y, () -> {
				this.config.morphMobSoundLoops = Math.max(1, this.config.morphMobSoundLoops - 1);
				this.config.save();
			}, () -> {
				this.config.morphMobSoundLoops = Math.min(3, this.config.morphMobSoundLoops + 1);
				this.config.save();
			});
		}
	}

	private void initAdvancedTab() {
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("Restablecer"), button -> {
			this.config = KohsDeathEffectsConfig.resetToDefaults();
			this.currentEffectTab = EffectTab.SILHOUETTE;
			this.activateOnlyEffect(this.currentEffectTab, true);
			this.scrollOffset = 0;
			this.restartPreview();
			this.clearAndInit();
		}, this.optionsX, y, 116, 20, ButtonTone.PRIMARY));
	}

	private void addStepButtons(int x, int y, Runnable decrease, Runnable increase) {
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("-"), button -> decrease.run(), x, y, 48, 20, ButtonTone.SMALL));
		this.addScrolledDrawableChild(this.purpleButton(Text.literal("+"), button -> increase.run(), x + 56, y, 48, 20, ButtonTone.SMALL));
	}

	private PurpleButtonWidget purpleButton(Text message, ButtonWidget.PressAction onPress, int x, int y, int width, int height, ButtonTone tone) {
		return new PurpleButtonWidget(x, y, width, height, message, onPress, tone);
	}

	private void drawFrame(DrawContext context) {
		context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		context.fill(0, 0, this.width, Math.max(48, this.height / 5), 0x331D0B35);
		context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
		context.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelWidth - 1, this.panelY + 55, COLOR_PANEL_DARK);
		context.drawBorder(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
		context.drawBorder(this.panelX + 3, this.panelY + 3, this.panelWidth - 6, this.panelHeight - 6, 0x663D2367);
		context.drawText(this.textRenderer, this.title, this.panelX + 16, this.panelY + 11, COLOR_TEXT, false);

		int tabGap = this.panelWidth < 360 ? 5 : 8;
		int tabWidth = MathHelper.clamp((this.panelWidth - 28 - tabGap * (MainTab.values().length - 1)) / MainTab.values().length, 54, 112);
		int selectedX = this.panelX + 14 + this.currentTab.ordinal() * (tabWidth + tabGap);
		context.fill(selectedX - 2, this.panelY + 28, selectedX + tabWidth + 2, this.panelY + 52, COLOR_PURPLE_SOFT);
		context.fill(selectedX, this.panelY + 51, selectedX + tabWidth, this.panelY + 53, COLOR_PURPLE);
	}

	private void drawContent(DrawContext context, int mouseX, int mouseY) {
		if (this.currentTab == MainTab.EFFECTS) {
			this.drawPreview(context, mouseX, mouseY);
			this.drawEffectOptions(context);
		} else if (this.currentTab == MainTab.SOUND) {
			context.drawText(this.textRenderer, Text.literal("Custom death sound"), this.optionsX, this.contentY, COLOR_TEXT, false);
			this.drawSoundOptions(context);
		} else {
			context.drawText(this.textRenderer, Text.literal("Avanzado"), this.optionsX, this.contentY, COLOR_TEXT, false);
			this.drawAdvancedOptions(context);
		}
	}

	private void drawEffectOptions(DrawContext context) {
		context.drawText(this.textRenderer, Text.literal("Efectos"), this.optionsX, this.contentY, COLOR_TEXT, false);
		this.drawSelectedSubTabUnderline(context);
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);

		switch (this.currentEffectTab) {
			case SILHOUETTE -> this.drawSilhouetteOptions(context);
			case PLAYER -> this.drawPlayerOptions(context);
			case RAGDOLL -> this.drawRagdollOptions(context);
			case MORPH -> this.drawMorphOptions(context);
		}

		this.drawSwatches(context);
		context.disableScissor();
	}

	private void drawSelectedSubTabUnderline(DrawContext context) {
		int gap = this.optionsWidth < 300 ? 4 : 8;
		int minWidth = this.optionsWidth < 340 ? 54 : 78;
		int buttonWidth = MathHelper.clamp((this.optionsWidth - gap * (EffectTab.values().length - 1)) / EffectTab.values().length, minWidth, 150);
		int x = this.optionsX + this.currentEffectTab.ordinal() * (buttonWidth + gap);
		context.fill(x, this.optionsY + 21, x + buttonWidth, this.optionsY + 24, COLOR_PURPLE);
	}

	private void drawSilhouetteOptions(DrawContext context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Modo activo: Silueta", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Color", y);
		int fieldX = this.optionsX + this.optionsWidth - 124;
		context.fill(fieldX - 2, y - 2, fieldX + 116, y + 22, COLOR_PANEL_SOFT);
		context.drawBorder(fieldX - 2, y - 2, 118, 24, COLOR_BORDER);

		y += 46;
		this.drawChildLabel(context, "Paleta", y);

		y += 42;
		this.drawChildLabel(context, "Opacidad", y);
		this.drawValue(context, Math.round(this.config.silhouetteAlpha * 100.0F) + "%", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Tamano", y);
		this.drawValue(context, String.format(Locale.ROOT, "%.1fx", this.config.silhouetteScale), y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Duracion", y);
		this.drawValue(context, this.config.silhouetteDurationSeconds + "s", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Altura", y);
		this.drawValue(context, String.format(Locale.ROOT, "%.1f", this.config.silhouetteRiseHeight), y);
	}

	private void drawPlayerOptions(DrawContext context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Modo activo: Jugador fantasma", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Movimiento", y);
		this.drawValue(context, movementText(this.config.playerGhostMovement).getString(), y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Duracion", y);
		this.drawValue(context, this.config.playerGhostDurationSeconds + "s", y);

		if (this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Altura", y);
			this.drawValue(context, String.format(Locale.ROOT, "%.1f", this.config.playerGhostRiseHeight), y);
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Opacidad", y);
		this.drawValue(context, Math.round(this.config.playerGhostAlpha * 100.0F) + "%", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Armadura", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Items en manos", y);
	}

	private void drawRagdollOptions(DrawContext context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Modo activo: Ragdoll", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Duracion", y);
		this.drawValue(context, this.config.ragdollDurationSeconds + "s", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Opacidad", y);

		if (this.config.ragdollFadeEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Tiempo opacidad", y);
			this.drawValue(context, this.config.ragdollFadeDurationSeconds + "s", y);
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Client collision", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Affect explosion", y);
	}

	private void drawMorphOptions(DrawContext context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Modo activo: Morph", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Morph to", y);
		this.drawValue(context, MorphMobCatalog.selectedName(this.config.morphEntityTypeId).getString(), y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Transparencia", y);
		this.drawValue(context, Math.round(this.config.morphAlpha * 100.0F) + "%", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Elevacion", y);

		if (this.config.morphElevationEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Tiempo", y);
			this.drawValue(context, this.config.morphElevationTimeSeconds + "s", y);
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Mob sound", y);

		if (this.config.morphMobSoundEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Volume", y);
			this.drawValue(context, this.config.morphMobSoundVolume + "%", y);

			y += ROW_STEP;
			this.drawChildLabel(context, "Sound loop", y);
			this.drawValue(context, this.config.morphMobSoundLoops + "x", y);
		}
	}

	private void drawAdvancedOptions(DrawContext context) {
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);
		this.drawLabel(context, "Valores por defecto", this.scrolledY(this.logicalOptionsTop() + 5));
		context.disableScissor();
	}

	private void drawSoundOptions(DrawContext context) {
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Custom death sound", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Custom sound file", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Lista MP3", y);
		if (DeathSoundManager.getSoundFiles().isEmpty()) {
			y += 24;
			context.drawText(this.textRenderer, Text.literal("No hay archivos .mp3"), this.optionsX + CHILD_INDENT, this.scrolledY(this.logicalOptionsTop() + 5 + ROW_STEP * 2 + 24), COLOR_TEXT_DIM, false);
		}

		String selectedName = selectedSoundName();
		if (!selectedName.isBlank()) {
			context.drawText(this.textRenderer, Text.literal("Seleccionado: " + trimButtonLabel(selectedName, 28)), this.optionsX + CHILD_INDENT, this.optionsScrollBottom - 12, COLOR_TEXT_MUTED, false);
		}
		context.disableScissor();
	}

	private void drawPreview(DrawContext context, int mouseX, int mouseY) {
		if (this.previewWidth <= 0 || this.previewHeight <= 0) {
			return;
		}

		context.fill(this.previewX, this.previewY, this.previewX + this.previewWidth, this.previewY + this.previewHeight, COLOR_PANEL_SOFT);
		context.fill(this.previewX + 2, this.previewY + 2, this.previewX + this.previewWidth - 2, this.previewY + 22, 0xAA2B1249);
		context.drawBorder(this.previewX, this.previewY, this.previewWidth, this.previewHeight, COLOR_BORDER);
		context.drawText(this.textRenderer, Text.literal("Preview"), this.previewX + 8, this.previewY + 8, COLOR_TEXT, false);
		if (this.previewHeight >= 96) {
			String previewLabel = this.currentEffectTab == EffectTab.MORPH
				? MorphMobCatalog.selectedName(this.config.morphEntityTypeId).getString()
				: PREVIEW_PLAYER_NAME;
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(previewLabel, Math.max(34, this.previewWidth - 12))),
				this.previewX + this.previewWidth / 2,
				this.previewY + this.previewHeight - 40,
				COLOR_TEXT_MUTED
			);
		}

		int innerLeft = this.previewX + 8;
		int innerTop = this.previewY + 24;
		int innerRight = this.previewX + this.previewWidth - 8;
		int innerBottom = this.previewY + this.previewHeight - (this.previewHeight >= 74 ? 32 : 8);
		if (innerBottom <= innerTop + 12) {
			return;
		}

		context.enableScissor(innerLeft, innerTop, innerRight, innerBottom);
		context.fill(innerLeft, innerTop, innerRight, innerBottom, 0x5510061D);
		context.fill(innerLeft, innerBottom - 1, innerRight, innerBottom, 0x66B96BFF);

		float progress = this.previewProgress();
		switch (this.currentEffectTab) {
			case SILHOUETTE -> this.drawSilhouettePreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case PLAYER -> this.drawPlayerPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case RAGDOLL -> this.drawRagdollPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case MORPH -> this.drawMorphPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
		}

		context.disableScissor();
	}

	private void drawGifPlaceholder(DrawContext context, int left, int top, int right, int bottom, String label) {
		int centerX = (left + right) / 2;
		int centerY = (top + bottom) / 2;
		int width = right - left;
		int height = bottom - top;
		context.drawBorder(left + 8, top + 8, Math.max(20, width - 16), Math.max(20, height - 16), 0x889D63FF);
		context.fill(centerX - 18, centerY - 10, centerX + 18, centerY + 10, 0x665E2DA8);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, Math.max(34, width - 18))), centerX, centerY - 4, COLOR_TEXT_MUTED);
		if (height >= 56) {
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("aqui"), centerX, centerY + 8, COLOR_TEXT_DIM);
		}
	}

	private void drawSilhouettePreview(DrawContext context, int left, int top, int right, int bottom, float progress) {
		float previewVisibility = 0.18F + silhouetteFade(progress) * 0.82F;
		float fade = this.config.risingSilhouetteEnabled ? previewVisibility * this.config.silhouetteAlpha : 0.18F;
		int alpha = MathHelper.clamp((int)(fade * 255.0F), 24, 255);
		int width = right - left;
		int height = bottom - top;
		float rise = this.config.risingSilhouetteEnabled ? easeOutCubic(progress) : 0.0F;
		int lift = (int)(rise * Math.max(8, height - 68));
		int previewTop = top - lift;
		int previewBottom = bottom - lift;
		float baseScale = MathHelper.clamp(Math.min(width, height) / 2.0F, 28.0F, 62.0F);
		float scale = MathHelper.clamp(baseScale * this.config.silhouetteScale * this.morphPreviewZoom, 18.0F, 128.0F);

		this.drawSilhouetteModel(context, left, previewTop, right, previewBottom, scale, alpha);
	}

	private void drawPlayerPreview(DrawContext context, int left, int top, int right, int bottom, float progress) {
		boolean rising = this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING;
		float rise = this.config.playerGhostEnabled && rising ? easeOutCubic(progress) : 0.0F;
		int width = right - left;
		int height = bottom - top;
		int lift = (int)(rise * Math.max(10, height - 68));
		int previewTop = top - lift;
		int previewBottom = bottom - lift;
		int size = MathHelper.clamp((int)(Math.min(width, height) * 0.5F * this.morphPreviewZoom), 18, 96);
		PreviewPlayerEntity entity = this.getPreviewPlayer();

		if (entity == null) {
			this.drawSilhouetteModel(context, left, previewTop, right, previewBottom, size, 255);
			return;
		}

		this.preparePreviewPlayer(entity);
		InventoryScreen.drawEntity(
			context,
			left,
			previewTop,
			right,
			previewBottom,
			size,
			0.0625F,
			(left + right) / 2.0F + this.morphPreviewYawOffset,
			(previewTop + previewBottom) / 2.0F + this.morphPreviewPitchOffset,
			entity
		);
		float fade = this.config.playerGhostEnabled ? (1.0F - progress) * this.config.playerGhostAlpha : 0.24F;
		int coverAlpha = MathHelper.clamp((int)((1.0F - fade) * 190.0F), 0, 210);
		context.fill(left, previewTop, right, previewBottom, ColorHelper.withAlpha(coverAlpha, 0x0B1014));
	}

	private void drawRagdollPreview(DrawContext context, int left, int top, int right, int bottom, float progress) {
		PlayerEntityModel model = this.getRagdollPreviewModel();
		PlayerEntityRenderState state = this.createRagdollPreviewState();
		float elapsedTicks = progress * 28.0F;
		float fall = smoothStep(elapsedTicks / 13.0F);
		float damping = damping(elapsedTicks, 0.12F);
		model.setAngles(state);
		applyRagdollPreviewPose(model, progress, elapsedTicks);

		int width = right - left;
		int height = bottom - top;
		float scale = MathHelper.clamp(
			Math.min(width / 1.75F, height / 0.95F) * this.morphPreviewZoom,
			22.0F,
			128.0F
		);
		float wobble = this.config.ragdollClientCollisionEnabled ? MathHelper.sin(progress * (float)(Math.PI * 2.0)) * 8.0F : 0.0F;
		float impact = MathHelper.sin(MathHelper.clamp(elapsedTicks / 13.0F, 0.0F, 1.0F) * (float)Math.PI) * 7.0F * damping;
		context.addPlayerSkin(
			model,
			previewSkinTextures.texture(),
			scale,
			MathHelper.lerp(fall, 5.0F, 78.0F) + impact + MathHelper.clamp(this.morphPreviewPitchOffset, -18.0F, 18.0F),
			205.0F + wobble + MathHelper.sin(elapsedTicks * 0.34F) * 4.0F * damping + MathHelper.clamp(this.morphPreviewYawOffset, -45.0F, 45.0F),
			0.96F,
			left,
			top,
			right,
			bottom
		);

		if (this.config.ragdollFadeEnabled) {
			int coverAlpha = MathHelper.clamp((int)(ragdollFadePreviewProgress(progress, this.config.ragdollDurationSeconds, this.config.ragdollFadeDurationSeconds) * 190.0F), 0, 210);
			context.fill(left, top, right, bottom, ColorHelper.withAlpha(coverAlpha, 0x0B1014));
		}
	}

	private void drawMorphPreview(DrawContext context, int left, int top, int right, int bottom, float progress) {
		LivingEntity entity = this.getPreviewMorphEntity();
		if (entity == null) {
			this.drawGifPlaceholder(context, left, top, right, bottom, "Mob");
			return;
		}

		float fade = silhouetteFade(progress) * this.config.morphAlpha;
		int coverAlpha = MathHelper.clamp((int)((1.0F - fade) * 190.0F), 0, 210);
		int width = right - left;
		int height = bottom - top;
		float rise = this.config.morphElevationEnabled ? easeOutCubic(progress) : 0.0F;
		int lift = (int)(rise * Math.max(8, height - 64));
		int previewTop = top - lift;
		int previewBottom = bottom - lift;
		float largestSide = Math.max(0.75F, Math.max(entity.getWidth(), entity.getHeight()));
		int size = MathHelper.clamp((int)(Math.min(width, height) * 0.82F * this.morphPreviewZoom / largestSide), 8, 92);

		this.preparePreviewMorphEntity(entity);
		InventoryScreen.drawEntity(
			context,
			left,
			previewTop,
			right,
			previewBottom,
			size,
			0.0625F,
			(left + right) / 2.0F + this.morphPreviewYawOffset,
			(previewTop + previewBottom) / 2.0F + this.morphPreviewPitchOffset,
			entity
		);
		context.fill(left, previewTop, right, previewBottom, ColorHelper.withAlpha(coverAlpha, 0x0B1014));
	}

	private void drawSilhouetteModel(DrawContext context, int left, int top, int right, int bottom, float scale, int alpha) {
		PlayerEntityModel model = this.getSilhouettePreviewModel();
		PlayerEntityRenderState state = this.createSilhouettePreviewState();
		model.setAngles(state);
		showAllPlayerModelParts(model);
		float pitch = MathHelper.clamp(this.morphPreviewPitchOffset, -35.0F, 35.0F);
		float yaw = 180.0F + this.morphPreviewYawOffset;
		context.addPlayerSkin(
			model,
			previewSkinTextures.texture(),
			scale,
			pitch,
			yaw,
			1.6F,
			left,
			top,
			right,
			bottom
		);
		context.addPlayerSkin(
			model,
			getSilhouettePreviewTexture(ColorHelper.withAlpha(alpha, this.config.silhouetteColor)),
			scale,
			pitch,
			yaw,
			1.6F,
			left,
			top,
			right,
			bottom
		);
	}

	private PlayerEntityRenderState createSilhouettePreviewState() {
		PlayerEntityRenderState state = new PlayerEntityRenderState();
		state.entityType = EntityType.PLAYER;
		state.age = 20.0F;
		state.width = 0.6F;
		state.height = 1.8F;
		state.standingEyeHeight = 1.62F;
		state.baseScale = 1.0F;
		state.ageScale = 1.0F;
		state.bodyYaw = 0.0F;
		state.relativeHeadYaw = 0.0F;
		state.pitch = 0.0F;
		state.pose = EntityPose.STANDING;
		state.mainArm = Arm.RIGHT;
		state.preferredArm = Arm.RIGHT;
		state.activeHand = Hand.MAIN_HAND;
		state.skinTextures = previewSkinTextures;
		state.name = PREVIEW_PLAYER_NAME;
		state.hatVisible = false;
		state.jacketVisible = false;
		state.leftPantsLegVisible = false;
		state.rightPantsLegVisible = false;
		state.leftSleeveVisible = false;
		state.rightSleeveVisible = false;
		state.capeVisible = false;
		return state;
	}

	private PlayerEntityRenderState createRagdollPreviewState() {
		PlayerEntityRenderState state = new PlayerEntityRenderState();
		state.entityType = EntityType.PLAYER;
		state.age = 20.0F;
		state.width = 0.6F;
		state.height = 1.8F;
		state.standingEyeHeight = 1.62F;
		state.baseScale = 1.0F;
		state.ageScale = 1.0F;
		state.bodyYaw = 0.0F;
		state.relativeHeadYaw = 0.0F;
		state.pitch = 0.0F;
		state.pose = EntityPose.STANDING;
		state.mainArm = Arm.RIGHT;
		state.preferredArm = Arm.RIGHT;
		state.activeHand = Hand.MAIN_HAND;
		state.skinTextures = previewSkinTextures;
		state.name = PREVIEW_PLAYER_NAME;
		state.hatVisible = true;
		state.jacketVisible = true;
		state.leftPantsLegVisible = true;
		state.rightPantsLegVisible = true;
		state.leftSleeveVisible = true;
		state.rightSleeveVisible = true;
		state.capeVisible = false;
		return state;
	}

	private PlayerEntityModel getSilhouettePreviewModel() {
		boolean slim = previewSkinTextures.model() == SkinTextures.Model.SLIM;
		if (this.silhouettePreviewModel == null || this.silhouettePreviewModelSlim != slim) {
			this.silhouettePreviewModel = new PlayerEntityModel(
				MinecraftClient.getInstance().getLoadedEntityModels().getModelPart(slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER),
				slim
			);
			this.silhouettePreviewModelSlim = slim;
		}

		return this.silhouettePreviewModel;
	}

	private PlayerEntityModel getRagdollPreviewModel() {
		boolean slim = previewSkinTextures.model() == SkinTextures.Model.SLIM;
		if (this.ragdollPreviewModel == null || this.ragdollPreviewModelSlim != slim) {
			this.ragdollPreviewModel = new PlayerEntityModel(
				MinecraftClient.getInstance().getLoadedEntityModels().getModelPart(slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER),
				slim
			);
			this.ragdollPreviewModelSlim = slim;
		}

		return this.ragdollPreviewModel;
	}

	private static void applyRagdollPreviewPose(PlayerEntityModel model, float progress, float elapsedTicks) {
		float loosen = smoothStep(elapsedTicks / 9.0F);
		float damping = damping(elapsedTicks, 0.13F);
		float armSwing = MathHelper.sin(elapsedTicks * 0.72F + 1.4F) * 0.32F * damping;
		float legSwing = MathHelper.sin(elapsedTicks * 0.55F + 2.1F) * 0.22F * damping;
		model.body.pitch += MathHelper.lerp(loosen, 0.0F, 0.11F);
		model.body.roll += MathHelper.lerp(loosen, 0.0F, 0.12F);
		model.head.pitch = MathHelper.lerp(loosen, model.head.pitch, 0.40F + armSwing * 0.16F);
		model.head.roll = MathHelper.lerp(loosen, model.head.roll, -0.25F);
		model.hat.copyTransform(model.head);
		model.rightArm.pitch = MathHelper.lerp(loosen, model.rightArm.pitch, 1.35F + armSwing);
		model.rightArm.yaw = MathHelper.lerp(loosen, model.rightArm.yaw, -0.84F);
		model.rightArm.roll = MathHelper.lerp(loosen, model.rightArm.roll, 0.48F + armSwing * 0.45F);
		model.leftArm.pitch = MathHelper.lerp(loosen, model.leftArm.pitch, 1.10F - armSwing * 0.85F);
		model.leftArm.yaw = MathHelper.lerp(loosen, model.leftArm.yaw, 0.70F);
		model.leftArm.roll = MathHelper.lerp(loosen, model.leftArm.roll, -0.46F - armSwing * 0.4F);
		model.rightLeg.pitch = MathHelper.lerp(loosen, model.rightLeg.pitch, -0.24F + legSwing);
		model.rightLeg.yaw = MathHelper.lerp(loosen, model.rightLeg.yaw, 0.32F);
		model.rightLeg.roll = MathHelper.lerp(loosen, model.rightLeg.roll, 0.24F);
		model.leftLeg.pitch = MathHelper.lerp(loosen, model.leftLeg.pitch, 0.36F - legSwing);
		model.leftLeg.yaw = MathHelper.lerp(loosen, model.leftLeg.yaw, -0.24F);
		model.leftLeg.roll = MathHelper.lerp(loosen, model.leftLeg.roll, -0.22F);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftPants.copyTransform(model.leftLeg);
		model.rightPants.copyTransform(model.rightLeg);
		model.jacket.copyTransform(model.body);
	}

	private static void showAllPlayerModelParts(PlayerEntityModel model) {
		model.head.visible = true;
		model.hat.visible = true;
		model.body.visible = true;
		model.jacket.visible = true;
		model.rightArm.visible = true;
		model.rightSleeve.visible = true;
		model.leftArm.visible = true;
		model.leftSleeve.visible = true;
		model.rightLeg.visible = true;
		model.rightPants.visible = true;
		model.leftLeg.visible = true;
		model.leftPants.visible = true;
	}

	private PreviewPlayerEntity getPreviewPlayer() {
		if (this.client == null || this.client.world == null) {
			return null;
		}

		if (this.previewPlayer == null || this.previewPlayer.clientWorld != this.client.world) {
			this.previewPlayer = new PreviewPlayerEntity(this.client.world, PREVIEW_FALLBACK_PROFILE);
		}

		this.previewPlayer.setPreviewSkin(previewSkinTextures);
		return this.previewPlayer;
	}

	private LivingEntity getPreviewMorphEntity() {
		if (this.client == null || this.client.world == null) {
			return null;
		}

		String mobId = MorphMobCatalog.sanitizeMobId(this.config.morphEntityTypeId);
		if (this.previewMorphEntity == null || !mobId.equals(this.previewMorphEntityId) || this.previewMorphEntity.getWorld() != this.client.world) {
			this.previewMorphEntity = MorphMobCatalog.createLivingEntity(this.client.world, mobId);
			this.previewMorphEntityId = mobId;
		}

		return this.previewMorphEntity;
	}

	private void preparePreviewMorphEntity(LivingEntity entity) {
		float yaw = 180.0F;
		entity.age = (int)((Util.getMeasuringTimeMs() - this.previewStartedAt) / 50L);
		entity.setNoGravity(true);
		entity.setInvisible(false);
		entity.setOnFire(false);
		entity.setPitch(0.0F);
		entity.setYaw(yaw);
		entity.bodyYaw = yaw;
		entity.lastBodyYaw = yaw;
		entity.headYaw = yaw;
		entity.lastHeadYaw = yaw;
	}

	private void preparePreviewPlayer(PreviewPlayerEntity entity) {
		entity.setPose(EntityPose.STANDING);
		entity.setSneaking(false);
		entity.setInvisible(false);
		entity.setOnFire(false);
		entity.setPitch(0.0F);
		entity.setYaw(180.0F);
		entity.bodyYaw = 180.0F;
		entity.headYaw = 180.0F;
		entity.lastHeadYaw = 180.0F;
		entity.handSwinging = false;
		entity.preferredHand = Hand.MAIN_HAND;
		entity.equipStack(EquipmentSlot.HEAD, this.config.playerGhostArmorEnabled ? new ItemStack(Items.NETHERITE_HELMET) : ItemStack.EMPTY);
		entity.equipStack(EquipmentSlot.CHEST, this.config.playerGhostArmorEnabled ? new ItemStack(Items.NETHERITE_CHESTPLATE) : ItemStack.EMPTY);
		entity.equipStack(EquipmentSlot.LEGS, this.config.playerGhostArmorEnabled ? new ItemStack(Items.NETHERITE_LEGGINGS) : ItemStack.EMPTY);
		entity.equipStack(EquipmentSlot.FEET, this.config.playerGhostArmorEnabled ? new ItemStack(Items.NETHERITE_BOOTS) : ItemStack.EMPTY);
		entity.setStackInHand(Hand.MAIN_HAND, this.config.playerGhostHeldItemsEnabled ? new ItemStack(Items.GOLDEN_SWORD) : ItemStack.EMPTY);
		entity.setStackInHand(Hand.OFF_HAND, this.config.playerGhostHeldItemsEnabled ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
	}

	private static void requestPreviewSkin() {
		if (previewSkinFuture != null) {
			return;
		}

		previewSkinFuture = fetchPreviewProfile(PREVIEW_PLAYER_NAME)
			.thenCompose(profile -> MinecraftClient.getInstance().getSkinProvider().fetchSkinTextures(profile)
				.thenApply(skin -> skin.orElse(DefaultSkinHelper.getSkinTextures(profile))))
			.exceptionally(throwable -> DefaultSkinHelper.getSkinTextures(PREVIEW_FALLBACK_PROFILE))
			.thenApply(skin -> {
				previewSkinTextures = skin;
				return skin;
			});
	}

	private static CompletableFuture<GameProfile> fetchPreviewProfile(String username) {
		URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + username);
		HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
		return SKIN_HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApplyAsync(response -> {
				if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
					return PREVIEW_FALLBACK_PROFILE;
				}

				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				if (!json.has("id")) {
					return PREVIEW_FALLBACK_PROFILE;
				}

				UUID uuid = parseMojangUuid(json.get("id").getAsString());
				String resolvedName = json.has("name") ? json.get("name").getAsString() : username;
				ProfileResult result = MinecraftClient.getInstance().getSessionService().fetchProfile(uuid, true);
				return result == null ? new GameProfile(uuid, resolvedName) : result.profile();
			}, Util.getDownloadWorkerExecutor());
	}

	private static UUID parseMojangUuid(String value) {
		String normalized = value.replace("-", "");
		if (normalized.length() != 32) {
			return PREVIEW_FALLBACK_UUID;
		}

		return UUID.fromString(
			normalized.substring(0, 8)
				+ "-"
				+ normalized.substring(8, 12)
				+ "-"
				+ normalized.substring(12, 16)
				+ "-"
				+ normalized.substring(16, 20)
				+ "-"
				+ normalized.substring(20)
		);
	}

	private static Identifier getSilhouettePreviewTexture(int argb) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (silhouettePreviewTexture == null) {
			silhouettePreviewTexture = new NativeImageBackedTexture("KoHs Death Effects Preview Silhouette", 2, 2, false);
			client.getTextureManager().registerTexture(SILHOUETTE_PREVIEW_TEXTURE_ID, silhouettePreviewTexture);
		}

		if (silhouettePreviewArgb != argb) {
			NativeImage image = silhouettePreviewTexture.getImage();
			image.fillRect(0, 0, 2, 2, argb);
			silhouettePreviewTexture.upload();
			silhouettePreviewArgb = argb;
		}

		return SILHOUETTE_PREVIEW_TEXTURE_ID;
	}

	private void drawLabel(DrawContext context, String label, int y) {
		this.drawLabel(context, label, y, 0);
	}

	private void drawChildLabel(DrawContext context, String label, int y) {
		this.drawLabel(context, label, y, CHILD_INDENT);
	}

	private void drawLabel(DrawContext context, String label, int y, int indent) {
		int x = this.optionsX + indent;
		int reservedForControls = indent == 0 ? 0 : (this.optionsWidth < 280 ? 96 : 128);
		int maxWidth = Math.max(28, this.optionsX + this.optionsWidth - x - reservedForControls);
		context.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, maxWidth)), x, y, indent == 0 ? COLOR_TEXT : COLOR_TEXT_MUTED, false);
	}

	private void drawValue(DrawContext context, String value, int y) {
		String trimmed = this.textRenderer.trimToWidth(value, Math.max(30, this.optionsWidth / 3));
		int textWidth = this.textRenderer.getWidth(trimmed);
		int right = this.optionsX + this.optionsWidth - (this.optionsWidth < 280 ? 100 : 122);
		int x = Math.max(this.optionsX + CHILD_INDENT, right - textWidth);
		context.drawText(this.textRenderer, Text.literal(trimmed), x, y, COLOR_TEXT, false);
	}

	private void drawSwatches(DrawContext context) {
		for (Swatch swatch : this.swatches) {
			context.fill(swatch.x(), swatch.y(), swatch.x() + swatch.size(), swatch.y() + swatch.size(), 0xFF000000 | swatch.color());
			context.drawBorder(swatch.x() - 1, swatch.y() - 1, swatch.size() + 2, swatch.size() + 2,
				(this.config.silhouetteColor & 0xFFFFFF) == swatch.color() ? COLOR_TEXT : COLOR_BORDER);
		}
	}

	private void drawScrollbar(DrawContext context) {
		if (this.maxScroll <= 0) {
			return;
		}

		int trackX = this.panelX + this.panelWidth - 10;
		int trackHeight = this.optionsScrollBottom - this.optionsScrollTop;
		int contentHeight = trackHeight + this.maxScroll;
		int thumbHeight = MathHelper.clamp(trackHeight * trackHeight / contentHeight, 18, trackHeight);
		int thumbTravel = trackHeight - thumbHeight;
		int thumbY = this.optionsScrollTop + (thumbTravel == 0 ? 0 : this.scrollOffset * thumbTravel / this.maxScroll);

		context.fill(trackX, this.optionsScrollTop, trackX + 3, this.optionsScrollBottom, 0x663D2367);
		context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, COLOR_PURPLE);
	}

	private void computeLayout() {
		int horizontalMargin = this.width < 560 ? 12 : this.width < 760 ? 48 : 80;
		int verticalMargin = this.height < 320 ? 12 : this.height < 460 ? 54 : 72;
		int maxPanelWidth = Math.max(220, this.width - horizontalMargin);
		int maxPanelHeight = Math.max(170, this.height - verticalMargin);
		int minPanelWidth = Math.min(360, maxPanelWidth);
		int minPanelHeight = Math.min(220, maxPanelHeight);
		int preferredWidth = this.width < 760 ? maxPanelWidth : 860;
		int preferredHeight = this.height < 460 ? maxPanelHeight : 520;
		this.panelWidth = MathHelper.clamp(preferredWidth, minPanelWidth, maxPanelWidth);
		this.panelHeight = MathHelper.clamp(preferredHeight, minPanelHeight, maxPanelHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.sidebarWidth = 0;
		int contentPadding = this.panelWidth < 520 ? 12 : 18;
		int headerHeight = this.panelHeight < 300 ? 56 : 66;
		this.contentX = this.panelX + contentPadding;
		this.contentY = this.panelY + headerHeight;
		this.contentWidth = this.panelWidth - contentPadding * 2;

		int footerTop = this.panelY + this.panelHeight - 36;
		if (this.currentTab == MainTab.EFFECTS) {
			this.optionsX = this.contentX;
			this.optionsY = this.contentY + 26;
			int previewGap = this.contentWidth < 420 ? 8 : 14;
			int previewAvailableHeight = footerTop - (this.contentY + 28) - 8;
			this.previewWidth = 0;
			this.previewHeight = 0;
			this.previewX = 0;
			this.previewY = 0;
			if (this.contentWidth >= 300 && previewAvailableHeight >= 66) {
				int wantedPreviewWidth = MathHelper.clamp((int)(this.contentWidth * 0.30F), 96, 172);
				int minOptionsWidth = this.contentWidth < 420 ? 236 : 260;
				int maxPreviewWidth = Math.max(0, this.contentWidth - previewGap - minOptionsWidth);
				this.previewWidth = Math.min(wantedPreviewWidth, maxPreviewWidth);
				if (this.previewWidth >= 86) {
					int maxPreviewHeight = Math.min(150, previewAvailableHeight);
					this.previewHeight = Math.max(66, maxPreviewHeight);
					this.previewX = this.contentX + this.contentWidth - this.previewWidth;
					this.previewY = this.contentY + 28;
				} else {
					this.previewWidth = 0;
					this.previewHeight = 0;
				}
			}
			this.optionsWidth = this.previewWidth > 0 ? Math.max(170, this.previewX - this.optionsX - previewGap) : this.contentWidth;
			this.optionsScrollTop = this.optionsY + 34;
		} else {
			this.previewX = 0;
			this.previewY = 0;
			this.previewWidth = 0;
			this.previewHeight = 0;
			this.optionsX = this.contentX;
			this.optionsY = this.contentY;
			this.optionsWidth = this.contentWidth;
			this.optionsScrollTop = this.optionsY + 28;
		}

		this.optionsScrollBottom = footerTop - 4;
	}

	private void updateScrollBounds() {
		this.maxScroll = Math.max(0, this.getLogicalContentBottom() - this.optionsScrollBottom + 8);
		this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, this.maxScroll);
	}

	private int getLogicalContentBottom() {
		int y = this.logicalOptionsTop();
		int bottom = this.optionsScrollTop;

		if (this.currentTab == MainTab.ADVANCED) {
			return y + ROW_STEP + 20;
		}

		if (this.currentTab == MainTab.SOUND) {
			int rows = 1 + Math.max(1, DeathSoundManager.getSoundFiles().size());
			return y + 20 + ROW_STEP * 2 + rows * 24;
		}

		switch (this.currentEffectTab) {
			case SILHOUETTE -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += 88;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
			}
			case PLAYER -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				if (this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING) {
					y += ROW_STEP;
					bottom = y + 20;
				}
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
			}
			case RAGDOLL -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				if (this.config.ragdollFadeEnabled) {
					y += ROW_STEP;
					bottom = y + 20;
				}
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
			}
			case MORPH -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				if (this.config.morphElevationEnabled) {
					y += ROW_STEP;
					bottom = y + 20;
				}
				y += ROW_STEP;
				bottom = y + 20;
				if (this.config.morphMobSoundEnabled) {
					y += ROW_STEP;
					bottom = y + 20;
					y += ROW_STEP;
					bottom = y + 20;
				}
			}
		}

		return Math.max(this.optionsScrollTop, bottom);
	}

	private int logicalOptionsTop() {
		return this.optionsScrollTop + 8;
	}

	private <T extends ClickableWidget> T addScrolledDrawableChild(T widget) {
		widget.setY(this.scrolledY(widget.getY()));
		if (this.isFullyVisible(widget.getY(), widget.getHeight())) {
			this.addDrawableChild(widget);
		}

		return widget;
	}

	private int scrolledY(int logicalY) {
		return logicalY - this.scrollOffset;
	}

	private boolean isFullyVisible(int y, int height) {
		return y >= this.optionsScrollTop && y + height <= this.optionsScrollBottom;
	}

	private boolean isMouseOverOptions(double mouseX, double mouseY) {
		return mouseX >= this.optionsX - 4
			&& mouseX <= this.optionsX + this.optionsWidth + 4
			&& mouseY >= this.optionsScrollTop
			&& mouseY <= this.optionsScrollBottom;
	}

	private boolean isMouseOverPreview(double mouseX, double mouseY) {
		return this.previewWidth > 0
			&& mouseX >= this.previewX
			&& mouseX <= this.previewX + this.previewWidth
			&& mouseY >= this.previewY
			&& mouseY <= this.previewY + this.previewHeight;
	}

	private boolean previewCanOrbit() {
		return this.currentTab == MainTab.EFFECTS
			&& (this.currentEffectTab == EffectTab.SILHOUETTE
				|| this.currentEffectTab == EffectTab.PLAYER
				|| this.currentEffectTab == EffectTab.RAGDOLL
				|| this.currentEffectTab == EffectTab.MORPH);
	}

	private float previewProgress() {
		int durationSeconds = switch (this.currentEffectTab) {
			case PLAYER -> this.config.playerGhostDurationSeconds;
			case RAGDOLL -> this.config.ragdollDurationSeconds;
			case MORPH -> this.config.morphElevationTimeSeconds;
			case SILHOUETTE -> this.config.silhouetteDurationSeconds;
		};
		long durationMs = Math.max(1000L, durationSeconds * 1000L);
		return (Util.getMeasuringTimeMs() - this.previewStartedAt) % durationMs / (float)durationMs;
	}

	private void restartPreview() {
		this.previewStartedAt = Util.getMeasuringTimeMs();
	}

	private void applyColorText(String value) {
		String normalized = value.startsWith("#") ? value.substring(1) : value;
		if (normalized.length() == 6 && normalized.matches("[0-9A-Fa-f]{6}")) {
			this.config.silhouetteColor = Integer.parseInt(normalized, 16);
			this.config.save();
			this.colorField.setEditableColor(COLOR_TEXT);
		} else {
			this.colorField.setEditableColor(0xFFFF7777);
		}
	}

	private static float silhouetteFade(float progress) {
		if (progress < 0.62F) {
			return 1.0F;
		}

		return 1.0F - MathHelper.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F);
	}

	private static float easeOutCubic(float progress) {
		float inverse = 1.0F - progress;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float smoothStep(float value) {
		float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}

	private static float damping(float elapsedTicks, float strength) {
		return (float)Math.exp(-elapsedTicks * strength);
	}

	private static float ragdollFadePreviewProgress(float progress, int durationSeconds, int fadeSeconds) {
		float duration = Math.max(1.0F, durationSeconds);
		float fadeWindow = Math.min(duration, Math.max(5.0F, fadeSeconds));
		float elapsed = progress * duration;
		float fadeStart = duration - fadeWindow;
		return MathHelper.clamp((elapsed - fadeStart) / fadeWindow, 0.0F, 1.0F);
	}

	private static Text onOff(boolean value) {
		return Text.literal(value ? "ON" : "OFF");
	}

	private static Text movementText(KohsDeathEffectsConfig.GhostMovementMode mode) {
		return Text.literal(mode == KohsDeathEffectsConfig.GhostMovementMode.STATIC ? "Estatico" : "Ascender");
	}

	private String selectedSoundName() {
		for (DeathSoundManager.SoundFile soundFile : DeathSoundManager.getSoundFiles()) {
			if (soundFile.id().equals(this.config.customDeathSoundId)) {
				return soundFile.displayName();
			}
		}

		return "";
	}

	private static String trimButtonLabel(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, Math.max(1, maxLength - 3)) + "...";
	}

	private static String formatColor(int color) {
		return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
	}

	private static EffectTab effectTabFromMode(KohsDeathEffectsConfig.DeathEffectMode mode) {
		return switch (mode) {
			case PLAYER_GHOST -> EffectTab.PLAYER;
			case RAGDOLL -> EffectTab.RAGDOLL;
			case MORPH -> EffectTab.MORPH;
			case SILHOUETTE -> EffectTab.SILHOUETTE;
		};
	}

	private enum MainTab {
		EFFECTS("Efectos"),
		SOUND("Sonido"),
		ADVANCED("Avanzado");

		private final String label;

		MainTab(String label) {
			this.label = label;
		}
	}

	private enum EffectTab {
		SILHOUETTE("Silueta", KohsDeathEffectsConfig.DeathEffectMode.SILHOUETTE),
		PLAYER("Jugador", KohsDeathEffectsConfig.DeathEffectMode.PLAYER_GHOST),
		RAGDOLL("Ragdoll", KohsDeathEffectsConfig.DeathEffectMode.RAGDOLL),
		MORPH("Morph", KohsDeathEffectsConfig.DeathEffectMode.MORPH);

		private final String label;
		private final KohsDeathEffectsConfig.DeathEffectMode mode;

		EffectTab(String label, KohsDeathEffectsConfig.DeathEffectMode mode) {
			this.label = label;
			this.mode = mode;
		}
	}

	private enum ButtonTone {
		NORMAL,
		SELECTED,
		SMALL,
		PRIMARY
	}

	private static final class PurpleButtonWidget extends ButtonWidget {
		private final ButtonTone tone;

		private PurpleButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress, ButtonTone tone) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
			this.tone = tone;
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			int fill = switch (this.tone) {
				case SELECTED -> COLOR_PURPLE_SOFT;
				case PRIMARY -> 0xCC7C3CFF;
				case SMALL -> COLOR_PURPLE_DARK;
				case NORMAL -> COLOR_PANEL_SOFT;
			};
			int border = this.tone == ButtonTone.SELECTED ? COLOR_PURPLE : COLOR_BORDER;
			int text = COLOR_TEXT;
			if (!this.active) {
				fill = 0x6630203F;
				border = 0x66452D66;
				text = COLOR_TEXT_DIM;
			} else if (this.isHovered()) {
				fill = 0xDD7C3CFF;
				border = COLOR_TEXT;
			}

			context.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), fill);
			context.drawBorder(this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
			drawScrollableText(context, MinecraftClient.getInstance().textRenderer, this.getMessage(), this.getX() + 5, this.getY(), this.getRight() - 5, this.getBottom(), text);
		}
	}

	private static final class MorphToScreen extends Screen {
		private static final int ROW_HEIGHT = 36;
		private final KohsDeathEffectsConfigScreen parent;
		private final List<MorphMobCatalog.MobOption> options = MorphMobCatalog.options();
		private final Map<String, LivingEntity> previewEntities = new HashMap<>();
		private TextFieldWidget searchField;
		private String searchQuery = "";
		private int panelX;
		private int panelY;
		private int panelWidth;
		private int panelHeight;
		private int listTop;
		private int listBottom;
		private int scrollOffset;
		private int maxScroll;

		private MorphToScreen(KohsDeathEffectsConfigScreen parent) {
			super(Text.literal("Morph to"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			this.computeLayout();
			List<MorphMobCatalog.MobOption> filteredOptions = this.filteredOptions();
			this.maxScroll = Math.max(0, filteredOptions.size() * ROW_HEIGHT - (this.listBottom - this.listTop) + 8);
			this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, this.maxScroll);

			this.searchField = new TextFieldWidget(this.textRenderer, this.panelX + 14, this.panelY + 51, this.panelWidth - 28, 20, Text.literal("Buscar mob"));
			this.searchField.setMaxLength(48);
			this.searchField.setDrawsBackground(false);
			this.searchField.setEditableColor(COLOR_TEXT);
			this.searchField.setPlaceholder(Text.literal("Buscar mob"));
			this.searchField.setText(this.searchQuery);
			this.searchField.setChangedListener(value -> {
				this.searchQuery = value;
				this.scrollOffset = 0;
				this.clearAndInit();
			});
			this.searchField.setFocused(true);
			this.addDrawableChild(this.searchField);

			int y = this.listTop + 4 - this.scrollOffset;
			for (MorphMobCatalog.MobOption option : filteredOptions) {
				if (y + 30 >= this.listTop && y <= this.listBottom) {
					boolean selected = option.id().equals(this.parent.config.morphEntityTypeId);
					String label = (selected ? "> " : "") + option.name().getString();
					this.addDrawableChild(new PurpleButtonWidget(
						this.panelX + 54,
						y,
						this.panelWidth - 70,
						30,
						Text.literal(this.textRenderer.trimToWidth(label, this.panelWidth - 92)),
						button -> {
							this.parent.config.morphEntityTypeId = option.id();
							this.parent.previewMorphEntity = null;
							this.parent.previewMorphEntityId = "";
							this.parent.config.save();
							this.close();
						},
						selected ? ButtonTone.SELECTED : ButtonTone.NORMAL
					));
				}
				y += ROW_HEIGHT;
			}

			this.addDrawableChild(new PurpleButtonWidget(
				this.panelX + this.panelWidth - 90,
				this.panelY + this.panelHeight - 30,
				76,
				20,
				Text.literal("Regresar"),
				button -> this.close(),
				ButtonTone.PRIMARY
			));
		}

		@Override
		public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.drawBorder(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.drawText(this.textRenderer, this.title, this.panelX + 14, this.panelY + 12, COLOR_TEXT, false);
			context.drawText(this.textRenderer, Text.literal("Mob actual: " + this.textRenderer.trimToWidth(MorphMobCatalog.selectedName(this.parent.config.morphEntityTypeId).getString(), Math.max(40, this.panelWidth - 110))), this.panelX + 14, this.panelY + 32, COLOR_TEXT_MUTED, false);
			context.fill(this.searchField.getX() - 2, this.searchField.getY() - 2, this.searchField.getRight() + 2, this.searchField.getBottom() + 2, COLOR_PANEL_SOFT);
			context.drawBorder(this.searchField.getX() - 2, this.searchField.getY() - 2, this.searchField.getWidth() + 4, this.searchField.getHeight() + 4, COLOR_BORDER);
			context.enableScissor(this.panelX + 10, this.listTop, this.panelX + this.panelWidth - 10, this.listBottom);
			this.drawMobPreviews(context);
			context.disableScissor();
			super.render(context, mouseX, mouseY, deltaTicks);
			this.drawScrollbar(context);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (mouseY >= this.listTop && mouseY <= this.listBottom && this.maxScroll > 0) {
				this.scrollOffset = MathHelper.clamp(this.scrollOffset - (int)(verticalAmount * 24.0), 0, this.maxScroll);
				this.clearAndInit();
				return true;
			}

			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		@Override
		public void close() {
			this.parent.config.save();
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		}

		@Override
		public boolean shouldPause() {
			return false;
		}

		private void computeLayout() {
			int horizontalMargin = this.width < 420 ? 18 : 90;
			int verticalMargin = this.height < 320 ? 18 : 70;
			this.panelWidth = MathHelper.clamp(this.width - horizontalMargin, Math.min(240, this.width - 12), Math.min(420, this.width - 12));
			this.panelHeight = MathHelper.clamp(this.height - verticalMargin, Math.min(190, this.height - 12), Math.min(460, this.height - 12));
			this.panelX = (this.width - this.panelWidth) / 2;
			this.panelY = (this.height - this.panelHeight) / 2;
			this.listTop = this.panelY + 78;
			this.listBottom = this.panelY + this.panelHeight - 38;
		}

		private List<MorphMobCatalog.MobOption> filteredOptions() {
			String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
			if (query.isBlank()) {
				return this.options;
			}

			return this.options.stream()
				.filter(option -> option.id().toLowerCase(Locale.ROOT).contains(query)
					|| option.name().getString().toLowerCase(Locale.ROOT).contains(query))
				.toList();
		}

		private void drawMobPreviews(DrawContext context) {
			int previewLeft = this.panelX + 15;
			int y = this.listTop + 4 - this.scrollOffset;
			for (MorphMobCatalog.MobOption option : this.filteredOptions()) {
				if (y + 30 >= this.listTop && y <= this.listBottom) {
					context.fill(previewLeft, y, previewLeft + 30, y + 30, COLOR_PANEL_SOFT);
					context.drawBorder(previewLeft, y, 30, 30, option.id().equals(this.parent.config.morphEntityTypeId) ? COLOR_PURPLE : COLOR_BORDER);
					this.drawMobPreview(context, option, previewLeft + 2, y + 2, previewLeft + 28, y + 28);
				}
				y += ROW_HEIGHT;
			}
		}

		private void drawMobPreview(DrawContext context, MorphMobCatalog.MobOption option, int left, int top, int right, int bottom) {
			if (this.client == null || this.client.world == null) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("?"), (left + right) / 2, top + 9, COLOR_TEXT_MUTED);
				return;
			}

			LivingEntity entity = this.previewEntities.computeIfAbsent(option.id(), id -> MorphMobCatalog.createLivingEntity(this.client.world, id));
			if (entity == null) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("?"), (left + right) / 2, top + 9, COLOR_TEXT_MUTED);
				return;
			}

			entity.age = (int)(Util.getMeasuringTimeMs() / 50L);
			entity.setNoGravity(true);
			entity.setInvisible(false);
			entity.setOnFire(false);
			entity.setPitch(0.0F);
			entity.setYaw(180.0F);
			entity.bodyYaw = 180.0F;
			entity.lastBodyYaw = 180.0F;
			entity.headYaw = 180.0F;
			entity.lastHeadYaw = 180.0F;
			float largestSide = Math.max(0.75F, Math.max(entity.getWidth(), entity.getHeight()));
			int size = MathHelper.clamp((int)(23.0F / largestSide), 5, 22);
			InventoryScreen.drawEntity(context, left, top, right, bottom, size, 0.0625F, (left + right) / 2.0F, (top + bottom) / 2.0F, entity);
		}

		private void drawScrollbar(DrawContext context) {
			if (this.maxScroll <= 0) {
				return;
			}

			int trackX = this.panelX + this.panelWidth - 9;
			int trackHeight = this.listBottom - this.listTop;
			int contentHeight = trackHeight + this.maxScroll;
			int thumbHeight = MathHelper.clamp(trackHeight * trackHeight / contentHeight, 18, trackHeight);
			int thumbTravel = trackHeight - thumbHeight;
			int thumbY = this.listTop + (thumbTravel == 0 ? 0 : this.scrollOffset * thumbTravel / this.maxScroll);
			context.fill(trackX, this.listTop, trackX + 3, this.listBottom, 0x663D2367);
			context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, COLOR_PURPLE);
		}
	}

	private static final class MorphSoundWarningScreen extends Screen {
		private final KohsDeathEffectsConfigScreen parent;
		private int panelX;
		private int panelY;
		private int panelWidth;
		private int panelHeight;

		private MorphSoundWarningScreen(KohsDeathEffectsConfigScreen parent) {
			super(Text.literal("Mob sound"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			this.computeLayout();
			int buttonY = this.panelY + this.panelHeight - 34;
			int buttonWidth = MathHelper.clamp((this.panelWidth - 38) / 2, 76, 128);
			this.addDrawableChild(new PurpleButtonWidget(this.panelX + 14, buttonY, buttonWidth, 20, Text.literal("Regresar"), button -> this.close(), ButtonTone.NORMAL));
			this.addDrawableChild(new PurpleButtonWidget(this.panelX + this.panelWidth - 14 - buttonWidth, buttonY, buttonWidth, 20, Text.literal("Continuar"), button -> {
				this.parent.config.morphMobSoundEnabled = true;
				this.parent.config.customDeathSoundEnabled = false;
				this.parent.config.save();
				if (this.client != null) {
					this.client.setScreen(this.parent);
				}
			}, ButtonTone.PRIMARY));
		}

		@Override
		public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.drawBorder(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);
			int textWidth = this.panelWidth - 28;
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth("Al activar Mob sound se desactivara", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 44, COLOR_TEXT_MUTED);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth("el custom death sound elegido.", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 58, COLOR_TEXT_MUTED);
			super.render(context, mouseX, mouseY, deltaTicks);
		}

		@Override
		public void close() {
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		}

		@Override
		public boolean shouldPause() {
			return false;
		}

		private void computeLayout() {
			this.panelWidth = Math.min(Math.max(230, this.width - 36), 340);
			this.panelHeight = Math.min(Math.max(118, this.height - 36), 150);
			this.panelX = (this.width - this.panelWidth) / 2;
			this.panelY = (this.height - this.panelHeight) / 2;
		}
	}

	private static final class CustomSoundWarningScreen extends Screen {
		private final KohsDeathEffectsConfigScreen parent;
		private final String selectedSoundId;
		private int panelX;
		private int panelY;
		private int panelWidth;
		private int panelHeight;

		private CustomSoundWarningScreen(KohsDeathEffectsConfigScreen parent, String selectedSoundId) {
			super(Text.literal("Custom sound"));
			this.parent = parent;
			this.selectedSoundId = selectedSoundId;
		}

		@Override
		protected void init() {
			this.computeLayout();
			int buttonY = this.panelY + this.panelHeight - 34;
			int buttonWidth = MathHelper.clamp((this.panelWidth - 38) / 2, 76, 128);
			this.addDrawableChild(new PurpleButtonWidget(this.panelX + 14, buttonY, buttonWidth, 20, Text.literal("Regresar"), button -> this.close(), ButtonTone.NORMAL));
			this.addDrawableChild(new PurpleButtonWidget(this.panelX + this.panelWidth - 14 - buttonWidth, buttonY, buttonWidth, 20, Text.literal("Continuar"), button -> {
				this.parent.applyCustomDeathSound(this.selectedSoundId);
				if (this.client != null) {
					this.client.setScreen(this.parent);
				}
			}, ButtonTone.PRIMARY));
		}

		@Override
		public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.drawBorder(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);
			int textWidth = this.panelWidth - 28;
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth("Al activar Custom sound se desactivara", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 44, COLOR_TEXT_MUTED);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth("el Mob sound del efecto Morph.", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 58, COLOR_TEXT_MUTED);
			super.render(context, mouseX, mouseY, deltaTicks);
		}

		@Override
		public void close() {
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		}

		@Override
		public boolean shouldPause() {
			return false;
		}

		private void computeLayout() {
			this.panelWidth = Math.min(Math.max(230, this.width - 36), 340);
			this.panelHeight = Math.min(Math.max(118, this.height - 36), 150);
			this.panelX = (this.width - this.panelWidth) / 2;
			this.panelY = (this.height - this.panelHeight) / 2;
		}
	}

	private static final class PreviewPlayerEntity extends AbstractClientPlayerEntity {
		private SkinTextures skinTextures = previewSkinTextures;

		private PreviewPlayerEntity(ClientWorld world, GameProfile profile) {
			super(world, profile);
		}

		private void setPreviewSkin(SkinTextures skinTextures) {
			this.skinTextures = skinTextures;
		}

		@Override
		public SkinTextures getSkinTextures() {
			return this.skinTextures;
		}
	}

	private record Swatch(int x, int y, int size, int color) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.size && mouseY >= this.y && mouseY < this.y + this.size;
		}
	}
}
