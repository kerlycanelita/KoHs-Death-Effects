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
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.kohs.deatheffects.KohsDeathEffects;
import com.kohs.deatheffects.KohsDeathEffectsConfig;
import com.kohs.deatheffects.client.KohsDeathEffectsClient;
import com.kohs.deatheffects.client.effect.DeathEffectPreviewSimulation;
import com.kohs.deatheffects.client.effect.KidsEffectManager;
import com.kohs.deatheffects.client.effect.MorphMobCatalog;
import com.kohs.deatheffects.client.effect.MorphMobSoundPlayer;
import com.kohs.deatheffects.client.effect.RisingSilhouetteEffect;
import com.kohs.deatheffects.client.sound.DeathSoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class KohsDeathEffectsConfigScreen extends Screen {
	private static final int ROW_STEP = 34;
	private static final int CHILD_INDENT = 12;
	private static final int BACKGROUND_PARTICLE_COUNT = 96;
	private static final float PREVIEW_MIN_ZOOM = 0.60F;
	private static final float PREVIEW_MAX_ZOOM = 1.80F;
	private static final float PREVIEW_TORSO_PIVOT_Y = -1.0625F;
	private static final int KIDS_PREVIEW_TRAIN_DOLLS = 3;
	private static final int KIDS_PREVIEW_ENTITY_ID = -1_947_021_111;
	private static final String PREVIEW_PLAYER_NAME = "zymekoh";
	private static final UUID PREVIEW_FALLBACK_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + PREVIEW_PLAYER_NAME).getBytes(StandardCharsets.UTF_8));
	private static final GameProfile PREVIEW_FALLBACK_PROFILE = new GameProfile(PREVIEW_FALLBACK_UUID, PREVIEW_PLAYER_NAME);
	private static final HttpClient SKIN_HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Identifier SILHOUETTE_PREVIEW_TEXTURE_ID = Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "preview/silhouette");
	private static final Identifier KIDS_PREVIEW_TEXTURE_ID = Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "kids_preview");
	private static final Identifier PREVIEW_WORLD_TEXTURE_ID = Identifier.fromNamespaceAndPath(KohsDeathEffects.MOD_ID, "textures/gui/preview_world.png");
	private static final int KIDS_PREVIEW_IMAGE_WIDTH = 476;
	private static final int KIDS_PREVIEW_IMAGE_HEIGHT = 427;
	private static final int PREVIEW_WORLD_IMAGE_WIDTH = 1920;
	private static final int PREVIEW_WORLD_IMAGE_HEIGHT = 990;
	private static CompletableFuture<PlayerSkin> previewSkinFuture;
	private static PlayerSkin previewSkinTextures = DefaultPlayerSkin.get(PREVIEW_FALLBACK_PROFILE);
	private static DynamicTexture silhouettePreviewTexture;
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
	private static final int COLOR_SCREEN_SHADE = 0x660B0614;
	private static final int COLOR_PANEL = 0x86170B2B;
	private static final int COLOR_PANEL_DARK = 0xA00C0616;
	private static final int COLOR_PANEL_SOFT = 0x721D0E35;
	private static final int COLOR_PURPLE = 0xFF9D63FF;
	private static final int COLOR_PURPLE_SOFT = 0x775E2DA8;
	private static final int COLOR_PURPLE_DARK = 0xFF2A1648;
	private static final int COLOR_BORDER = 0xFF7043B9;
	private static final int COLOR_TEXT = 0xFFF4ECFF;
	private static final int COLOR_TEXT_MUTED = 0xFFCDBAE8;
	private static final int COLOR_TEXT_DIM = 0xFF9F8ABF;
	private static final int COLOR_SCROLL_FADE = 0xCC140A24;
	private static final int COLOR_SCROLL_FADE_CLEAR = 0x00140A24;
	private static final int TOOLTIP_MAX_WIDTH = 220;
	private static final float TOOLTIP_FADE_IN_MILLIS = 150.0F;
	private static final float TOOLTIP_FADE_OUT_MILLIS = 120.0F;

	private final Screen parent;
	private final List<Swatch> swatches = new ArrayList<>();
	private final List<HoverRegion> hoverRegions = new ArrayList<>();
	private String displayedTooltip;
	private float tooltipOpacity;
	private long tooltipFrameNanos = System.nanoTime();
	private int tooltipAnchorX;
	private int tooltipAnchorY;
	private KohsDeathEffectsConfig config;
	private MainTab currentTab = MainTab.EFFECTS;
	private EffectTab currentEffectTab;
	private EditBox colorField;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int headerHeight;
	private int footerHeight;
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
	private final DeathEffectPreviewSimulation previewSimulation = new DeathEffectPreviewSimulation();
	private boolean previewSimulationDirty = true;
	private RisingSilhouetteEffect.PreviewFrame previewFrame;
	private PreviewPlayerEntity previewPlayer;
	private LivingEntity previewMorphEntity;
	private String previewMorphEntityId = "";
	private float morphPreviewYawOffset;
	private float morphPreviewPitchOffset;
	private float morphPreviewZoom = 1.0F;
	private boolean previewPaused;
	private float pausedPreviewProgress;
	private float animatedMainTabX = Float.NaN;
	private float animatedEffectTabX = Float.NaN;
	private float animatedEffectTabY = Float.NaN;
	private PlayerModel silhouettePreviewModel;
	private boolean silhouettePreviewModelSlim;
	private PlayerModel ragdollPreviewModel;
	private boolean ragdollPreviewModelSlim;
	private PlayerModel kidsCarrierPreviewModel;
	private boolean kidsCarrierPreviewModelSlim;
	private final PlayerModel[] kidsShoulderPreviewModels = new PlayerModel[2];
	private boolean kidsShoulderPreviewModelSlim;
	private final PlayerModel[] kidsDraggedPreviewModels = new PlayerModel[KIDS_PREVIEW_TRAIN_DOLLS];
	private boolean kidsDraggedPreviewModelSlim;

	public KohsDeathEffectsConfigScreen(Screen parent) {
		super(Component.literal("KoHs Death Effects"));
		this.parent = parent;
		this.config = KohsDeathEffectsConfig.get();
		this.currentEffectTab = effectTabFromMode(this.config.deathEffectMode);
		this.previewStartedAt = Util.getMillis();
		requestPreviewSkin();
	}

	private void clearAndInit() {
		if (this.currentTab == MainTab.EFFECTS) {
			this.restartPreview();
		}
		this.rebuildWidgets();
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

		int footerY = this.panelY + this.panelHeight - this.footerHeight + 5;
		int footerPadding = this.panelWidth < 360 ? 8 : 14;
		int footerGap = 6;
		int availableFooterWidth = Math.max(80, this.panelWidth - footerPadding * 2 - footerGap);
		int resetWidth = Mth.clamp(availableFooterWidth * 58 / 100, 74, 104);
		int doneWidth = Math.max(54, availableFooterWidth - resetWidth);
		int doneX = this.panelX + this.panelWidth - footerPadding - doneWidth;
		this.addRenderableWidget(this.purpleButton(Component.literal("Reset defaults"), button -> this.resetDefaults(),
			doneX - footerGap - resetWidth, footerY, resetWidth, 20, ButtonTone.NORMAL));
		this.addRenderableWidget(this.purpleButton(Component.literal("Done"), button -> this.onClose(),
			doneX, footerY, doneWidth, 20, ButtonTone.PRIMARY));
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

			if (this.config.morphMobSoundEnabled && this.minecraft != null) {
				this.minecraft.setScreen(new CustomSoundWarningScreen(this, null));
			} else {
				this.applyCustomDeathSound(null);
				this.clearAndInit();
			}
		}, controlX, y, 104, 20, this.config.customDeathSoundEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addIntSlider(y, Math.round(this.config.customDeathSoundVolume * 100.0F), 0, 300, 10, value -> {
			this.config.customDeathSoundVolume = value / 100.0F;
			this.config.save();
		}, value -> value + "%");

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Folder"), button -> DeathSoundManager.openCustomSoundDirectory(),
			controlX - 112, y, 104, 20, ButtonTone.NORMAL));
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Reload"), button -> {
			DeathSoundManager.refresh();
			this.rebuildWidgets();
		}, controlX, y, 104, 20, ButtonTone.NORMAL));

		y += ROW_STEP;
		y += 24;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("None"), button -> {
			this.config.customDeathSoundId = "";
			this.config.save();
			this.clearAndInit();
		}, this.optionsX + CHILD_INDENT, y, Math.max(104, this.optionsWidth - CHILD_INDENT), 20, this.config.customDeathSoundId.isBlank() ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		for (DeathSoundManager.SoundFile soundFile : DeathSoundManager.getSoundFiles()) {
			y += 24;
			boolean selected = soundFile.id().equals(this.config.customDeathSoundId);
			String prefix = selected ? "> " : "";
			String suffix = soundFile.custom() ? "  [custom]" : "";
			this.addScrolledDrawableChild(this.purpleButton(Component.literal(prefix + trimButtonLabel(soundFile.displayName() + suffix, 34)), button -> {
				if (this.config.morphMobSoundEnabled && this.minecraft != null) {
					this.minecraft.setScreen(new CustomSoundWarningScreen(this, soundFile.id()));
				} else {
					this.applyCustomDeathSound(soundFile.id());
					this.rebuildWidgets();
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
		if (soundId != null && this.minecraft != null && this.minecraft.player != null) {
			DeathSoundManager.playPreviewAt(entityPosition(this.minecraft.player));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		this.hoverRegions.clear();
		this.advanceTabAnimations(deltaTicks);
		this.drawFrame(context);
		this.drawContent(context, mouseX, mouseY);
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		this.drawOptionScrollFades(context);
		this.drawScrollbar(context);
		this.drawHoverTooltip(context, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
		if (doubleClick && this.previewCanOrbit() && this.isMouseOverPreview(click.x(), click.y())) {
			this.resetPreviewView();
			return true;
		}

		if (this.currentTab == MainTab.EFFECTS && this.currentEffectTab == EffectTab.SILHOUETTE) {
			for (Swatch swatch : this.swatches) {
				if (swatch.contains(click.x(), click.y())) {
					this.config.silhouetteColor = swatch.color();
					this.config.save();
					this.restartPreview();

					if (this.colorField != null) {
						this.colorField.setValue(formatColor(this.config.silhouetteColor));
					}

					return true;
				}
			}
		}

		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.previewCanOrbit() && this.isMouseOverPreview(mouseX, mouseY)) {
			this.morphPreviewZoom = Mth.clamp(this.morphPreviewZoom + (float)verticalAmount * 0.10F, PREVIEW_MIN_ZOOM, PREVIEW_MAX_ZOOM);
			return true;
		}

		if (this.maxScroll > 0 && this.isMouseOverOptions(mouseX, mouseY)) {
			this.scrollOffset = Mth.clamp(this.scrollOffset - (int)(verticalAmount * 24.0), 0, this.maxScroll);
			this.rebuildWidgets();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
		if (click.button() == 0 && this.previewCanOrbit() && this.isMouseOverPreview(click.x(), click.y())) {
			this.morphPreviewYawOffset = Mth.clamp(this.morphPreviewYawOffset + (float)deltaX * 1.6F, -140.0F, 140.0F);
			this.morphPreviewPitchOffset = Mth.clamp(this.morphPreviewPitchOffset + (float)deltaY * 1.2F, -80.0F, 80.0F);
			return true;
		}

		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		KohsDeathEffectsClient.getKidsEffectManager().clearPreviewShoulders(KIDS_PREVIEW_ENTITY_ID);
		this.previewSimulation.clear();
		this.previewPlayer = null;
		this.previewMorphEntity = null;
	}

	private void addMainTabButtons() {
		int horizontalPadding = this.panelWidth < 360 ? 8 : 12;
		int x = this.panelX + horizontalPadding;
		int y = this.panelY + 28;
		int gap = this.panelWidth < 360 ? 4 : 6;
		int count = MainTab.values().length;
		int width = Math.max(1, Math.min(108, (this.panelWidth - horizontalPadding * 2 - gap * (count - 1)) / count));

		for (MainTab tab : MainTab.values()) {
			this.addRenderableWidget(this.purpleButton(Component.literal(tab.label), button -> {
				this.currentTab = tab;
				this.scrollOffset = 0;
				if (tab == MainTab.EFFECTS) {
					this.previewPaused = false;
					this.restartPreview();
				}
				this.rebuildWidgets();
			}, x, y, width, 20, this.currentTab == tab ? ButtonTone.SELECTED : ButtonTone.NORMAL));
			x += width + gap;
		}
	}

	private void addEffectSubTabs() {
		if (this.sidebarWidth > 0) {
			int x = this.contentX;
			int y = this.contentY + 18;
			int buttonHeight = 20;
			int gap = 3;
			for (EffectTab tab : EffectTab.values()) {
				this.addRenderableWidget(this.purpleButton(Component.literal(tab.label), button -> this.selectEffectTab(tab),
					x, y, this.sidebarWidth, buttonHeight, this.currentEffectTab == tab ? ButtonTone.SELECTED : ButtonTone.NORMAL));
				y += buttonHeight + gap;
			}
			return;
		}

		int gap = this.optionsWidth < 360 ? 3 : 6;
		int count = EffectTab.values().length;
		int buttonWidth = Math.max(1, Math.min(150, (this.optionsWidth - gap * (count - 1)) / count));
		int x = this.optionsX;
		int y = this.optionsY;

		for (EffectTab tab : EffectTab.values()) {
			this.addRenderableWidget(this.purpleButton(Component.literal(tab.label), button -> this.selectEffectTab(tab),
				x, y, buttonWidth, 20, this.currentEffectTab == tab ? ButtonTone.SELECTED : ButtonTone.NORMAL));
			x += buttonWidth + gap;
		}
	}

	private void selectEffectTab(EffectTab tab) {
		this.currentEffectTab = tab;
		this.activateOnlyEffect(tab, true);
		this.scrollOffset = 0;
		this.previewPaused = false;
		this.resetPreviewView();
		this.restartPreview();
		this.rebuildWidgets();
	}

	private void resetDefaults() {
		this.config = KohsDeathEffectsConfig.resetToDefaults();
		this.currentEffectTab = EffectTab.SILHOUETTE;
		this.activateOnlyEffect(this.currentEffectTab, true);
		this.scrollOffset = 0;
		this.previewPaused = false;
		this.resetPreviewView();
		this.restartPreview();
		this.rebuildWidgets();
	}

	private void activateOnlyEffect(EffectTab tab, boolean save) {
		this.config.effectsEnabled = true;
		this.config.deathEffectMode = tab.mode;
		this.config.risingSilhouetteEnabled = tab == EffectTab.SILHOUETTE;
		this.config.playerGhostEnabled = tab == EffectTab.PLAYER;
		this.config.ragdollEnabled = tab == EffectTab.RAGDOLL;
		this.config.kidsEnabled = tab == EffectTab.KIDS;
		this.config.morphEnabled = tab == EffectTab.MORPH;
		if (save) {
			this.config.save();
		}
	}

	private void addPreviewControls() {
		if (this.previewWidth < 132 || this.previewHeight < 74) {
			return;
		}

		int availableWidth = this.previewWidth - 20;
		int gap = 4;
		int buttonY = this.previewControlY();
		int firstX = this.previewX + 10;
		if (this.currentEffectTab == EffectTab.KIDS) {
			int kidsButtonWidth = Math.max(44, (availableWidth - gap) / 2);
			this.addRenderableWidget(this.purpleButton(Component.literal(this.previewPaused ? "Play" : "Pause"), button -> {
				this.togglePreviewPaused();
				button.setMessage(Component.literal(this.previewPaused ? "Play" : "Pause"));
			}, firstX, buttonY, kidsButtonWidth, 20, ButtonTone.SMALL));
			this.addRenderableWidget(this.purpleButton(Component.literal("Center"), button -> this.resetPreviewView(),
				firstX + kidsButtonWidth + gap, buttonY, kidsButtonWidth, 20, ButtonTone.SMALL));
			return;
		}

		int buttonWidth = Math.max(30, (availableWidth - gap * 2) / 3);
		this.addRenderableWidget(this.purpleButton(Component.literal("Replay"), button -> {
			this.previewPaused = false;
			this.restartPreview();
			this.rebuildWidgets();
		}, firstX, buttonY, buttonWidth, 20, ButtonTone.SMALL));
		int secondX = firstX + buttonWidth + gap;
		if (this.currentEffectTab == EffectTab.MORPH) {
			this.addRenderableWidget(this.purpleButton(Component.literal("Sound"), button -> this.playPreviewMorphSound(),
				secondX, buttonY, buttonWidth, 20, ButtonTone.SMALL));
		} else {
			this.addRenderableWidget(this.purpleButton(Component.literal(this.previewPaused ? "Play" : "Pause"), button -> {
				this.togglePreviewPaused();
				button.setMessage(Component.literal(this.previewPaused ? "Play" : "Pause"));
			},
				secondX, buttonY, buttonWidth, 20, ButtonTone.SMALL));
		}
		this.addRenderableWidget(this.purpleButton(Component.literal("Center"), button -> this.resetPreviewView(),
			secondX + buttonWidth + gap, buttonY, buttonWidth, 20, ButtonTone.SMALL));
	}

	private void playPreviewMorphSound() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		MorphMobSoundPlayer.playConfigured(entityPosition(this.minecraft.player), this.config);
	}

	private void initEffectOptions() {
			switch (this.currentEffectTab) {
			case SILHOUETTE -> this.initSilhouetteOptions();
			case PLAYER -> this.initPlayerOptions();
			case RAGDOLL -> this.initRagdollOptions();
			case KIDS -> this.initKidsOptions();
			case MORPH -> this.initMorphOptions();
		}
	}

	private void initSilhouetteOptions() {
		int y = this.logicalOptionsTop() + ROW_STEP;
		int fieldWidth = Mth.clamp(this.optionsWidth - CHILD_INDENT, 104, 160);
		int fieldX = this.optionsX + CHILD_INDENT;
		int fieldY = y + 18;
		this.colorField = new EditBox(this.font, fieldX, fieldY, fieldWidth, 20, Component.literal("Color"));
		this.colorField.setMaxLength(7);
		this.colorField.setHint(Component.literal("#B96BFF"));
		this.colorField.setValue(formatColor(this.config.silhouetteColor));
		this.colorField.setBordered(false);
		this.colorField.setTextColor(COLOR_TEXT);
		this.colorField.setResponder(this::applyColorText);
		this.addScrolledDrawableChild(this.colorField);

		int swatchY = this.scrolledY(fieldY + 44);
		int swatchX = this.optionsX + CHILD_INDENT;
		int swatchGap = 2;
		int swatchSize = Mth.clamp((this.optionsWidth - CHILD_INDENT * 2 - swatchGap * (PRESET_COLORS.length - 1)) / PRESET_COLORS.length, 10, 18);
		for (int color : PRESET_COLORS) {
			if (this.isFullyVisible(swatchY, swatchSize)) {
				this.swatches.add(new Swatch(swatchX, swatchY, swatchSize, color));
			}
			swatchX += swatchSize + swatchGap;
		}

		y = fieldY + 86;
		this.addIntSlider(y, Math.round(this.config.silhouetteAlpha * 100.0F), 5, 100, 5, value -> {
			this.config.silhouetteAlpha = value / 100.0F;
			this.config.save();
		}, value -> value + "%");

		y += ROW_STEP;
		this.addIntSlider(y, Math.round(this.config.silhouetteScale * 100.0F), 50, 250, 10, value -> {
			this.config.silhouetteScale = value / 100.0F;
			this.config.save();
		}, value -> String.format(Locale.ROOT, "%.1fx", value / 100.0F));

		y += ROW_STEP;
		this.addIntSlider(y, this.config.silhouetteDurationSeconds, 1, 60, 1, value -> {
			this.config.silhouetteDurationSeconds = value;
			this.config.save();
		}, value -> value + "s");

		y += ROW_STEP;
		this.addIntSlider(y, Math.round(this.config.silhouetteRiseHeight * 2.0F), 1, 128, 1, value -> {
			this.config.silhouetteRiseHeight = value / 2.0F;
			this.config.save();
		}, value -> String.format(Locale.ROOT, "%.1f", value / 2.0F));
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
		this.addIntSlider(y, this.config.playerGhostDurationSeconds, 1, 60, 1, value -> {
			this.config.playerGhostDurationSeconds = value;
			this.config.save();
		}, value -> value + "s");

		if (this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING) {
			y += ROW_STEP;
			this.addIntSlider(y, Math.round(this.config.playerGhostRiseHeight * 2.0F), 1, 128, 1, value -> {
				this.config.playerGhostRiseHeight = value / 2.0F;
				this.config.save();
			}, value -> String.format(Locale.ROOT, "%.1f", value / 2.0F));
		}

		y += ROW_STEP;
		this.addIntSlider(y, Math.round(this.config.playerGhostAlpha * 100.0F), 5, 100, 5, value -> {
			this.config.playerGhostAlpha = value / 100.0F;
			this.config.save();
		}, value -> value + "%");

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
		int y = this.logicalOptionsTop() + ROW_STEP + 20;
		int halfWidth = Mth.clamp((this.optionsWidth - CHILD_INDENT - 8) / 2, 70, 112);
		int firstX = this.optionsX + CHILD_INDENT;
		int secondX = firstX + halfWidth + 8;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Fall"), button -> {
			this.config.faintAnimationType = KohsDeathEffectsConfig.FaintAnimationType.FALL;
			this.config.save();
			this.clearAndInit();
		}, firstX, y, halfWidth, 20, this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.FALL ? ButtonTone.SELECTED : ButtonTone.NORMAL));
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Crawl"), button -> {
			this.config.faintAnimationType = KohsDeathEffectsConfig.FaintAnimationType.CRAWL;
			this.config.save();
			this.clearAndInit();
		}, secondX, y, halfWidth, 20, this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL) {
			y += ROW_STEP;
			this.addIntSlider(y, this.config.faintCrawlSpeed, 100, 300, 10, value -> {
				this.config.faintCrawlSpeed = value;
				this.config.save();
			}, value -> value + "%");
		}
	}

	private void initKidsOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP + 20;
		int halfWidth = Mth.clamp((this.optionsWidth - CHILD_INDENT - 8) / 2, 70, 112);
		int firstX = this.optionsX + CHILD_INDENT;
		int secondX = firstX + halfWidth + 8;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Cumulative"), button -> {
			this.config.kidsMode = KohsDeathEffectsConfig.KidsMode.CUMULATIVE;
			this.config.save();
			this.restartPreview();
			this.clearAndInit();
		}, firstX, y, halfWidth, 20, this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE ? ButtonTone.SELECTED : ButtonTone.NORMAL));
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Only Shoulders"), button -> {
			this.config.kidsMode = KohsDeathEffectsConfig.KidsMode.ONLY_SHOULDERS;
			this.config.save();
			this.restartPreview();
			this.clearAndInit();
		}, secondX, y, halfWidth, 20, this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.ONLY_SHOULDERS ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		y += ROW_STEP;
		int minimumTimer = this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE ? 20 : 3;
		int currentTimer = this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE
			? this.config.kidsCumulativeTimerSeconds
			: this.config.kidsTimerSeconds;
		this.addIntSlider(y, currentTimer, minimumTimer, 301, 1, value -> {
			if (this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE) {
				this.config.kidsCumulativeTimerSeconds = value;
			} else {
				this.config.kidsTimerSeconds = value;
			}
			this.config.save();
		}, KohsDeathEffectsConfigScreen::kidsTimerText);

		if (this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE) {
			y += ROW_STEP;
			this.addIntSlider(y, this.config.kidsRopeSizePercent, 100, 300, 10, value -> {
				this.config.kidsRopeSizePercent = value;
				this.config.save();
			}, value -> value + "%");
			y += ROW_STEP;

			y += ROW_STEP + 20;
			this.addScrolledDrawableChild(this.purpleButton(Component.literal("Look Down"), button -> {
				this.config.kidsTrainFacing = KohsDeathEffectsConfig.KidsTrainFacing.LOOK_DOWN;
				this.config.save();
				this.restartPreview();
				this.clearAndInit();
			}, firstX, y, halfWidth, 20, this.config.kidsTrainFacing == KohsDeathEffectsConfig.KidsTrainFacing.LOOK_DOWN ? ButtonTone.SELECTED : ButtonTone.NORMAL));
			this.addScrolledDrawableChild(this.purpleButton(Component.literal("Look Up"), button -> {
				this.config.kidsTrainFacing = KohsDeathEffectsConfig.KidsTrainFacing.LOOK_UP;
				this.config.save();
				this.restartPreview();
				this.clearAndInit();
			}, secondX, y, halfWidth, 20, this.config.kidsTrainFacing == KohsDeathEffectsConfig.KidsTrainFacing.LOOK_UP ? ButtonTone.SELECTED : ButtonTone.NORMAL));
		}

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.kidsAnimationEnabled), button -> {
			this.config.kidsAnimationEnabled = !this.config.kidsAnimationEnabled;
			this.config.save();
			this.restartPreview();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.kidsAnimationEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.kidsAnimationEnabled) {
			y += ROW_STEP;
			this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.kidsDraggedHandMovementEnabled), button -> {
				this.config.kidsDraggedHandMovementEnabled = !this.config.kidsDraggedHandMovementEnabled;
				this.config.save();
				this.restartPreview();
				this.clearAndInit();
			}, controlX, y, 104, 20, this.config.kidsDraggedHandMovementEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));
		}
	}

	private void initMorphOptions() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Morph to"), button -> {
			if (this.minecraft != null) {
				this.minecraft.setScreen(new MorphToScreen(this));
			}
		}, controlX, y, 104, 20, ButtonTone.NORMAL));

		y += ROW_STEP;
		this.addIntSlider(y, Math.round(this.config.morphAlpha * 100.0F), 5, 100, 5, value -> {
			this.config.morphAlpha = value / 100.0F;
			this.config.save();
		}, value -> value + "%");

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.morphElevationEnabled), button -> {
			this.config.morphElevationEnabled = !this.config.morphElevationEnabled;
			this.config.save();
			this.clearAndInit();
		}, controlX, y, 104, 20, this.config.morphElevationEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.morphElevationEnabled) {
			y += ROW_STEP;
			this.addIntSlider(y, this.config.morphElevationTimeSeconds, 1, 60, 1, value -> {
				this.config.morphElevationTimeSeconds = value;
				this.config.save();
			}, value -> value + "s");
		}

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.morphMobSoundEnabled), button -> {
			if (this.config.morphMobSoundEnabled) {
				this.config.morphMobSoundEnabled = false;
				this.config.save();
				this.clearAndInit();
				return;
			}

			if (this.minecraft != null) {
				this.minecraft.setScreen(new MorphSoundWarningScreen(this));
			}
		}, controlX, y, 104, 20, this.config.morphMobSoundEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));

		if (this.config.morphMobSoundEnabled) {
			y += ROW_STEP;
			this.addIntSlider(y, this.config.morphMobSoundVolume, 0, 300, 10, value -> {
				this.config.morphMobSoundVolume = value;
				this.config.save();
			}, value -> value + "%");

			y += ROW_STEP;
			this.addIntSlider(y, this.config.morphMobSoundLoops, 1, 3, 1, value -> {
				this.config.morphMobSoundLoops = value;
				this.config.save();
			}, value -> value + "x");
		}
	}

	private void initAdvancedTab() {
		int controlX = this.optionsX + this.optionsWidth - 114;
		int y = this.logicalOptionsTop() + ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(Component.literal("Reset"), button -> {
			this.config = KohsDeathEffectsConfig.resetToDefaults();
			this.currentEffectTab = EffectTab.SILHOUETTE;
			this.activateOnlyEffect(this.currentEffectTab, true);
			this.scrollOffset = 0;
			this.restartPreview();
			this.rebuildWidgets();
		}, this.optionsX, y, 116, 20, ButtonTone.PRIMARY));

		y += ROW_STEP;
		this.addScrolledDrawableChild(this.purpleButton(onOff(this.config.vanillaDeathAnimationEnabled), button -> {
			this.config.vanillaDeathAnimationEnabled = !this.config.vanillaDeathAnimationEnabled;
			button.setMessage(onOff(this.config.vanillaDeathAnimationEnabled));
			this.config.save();
		}, controlX, y, 104, 20, this.config.vanillaDeathAnimationEnabled ? ButtonTone.SELECTED : ButtonTone.NORMAL));
	}

	private void addIntSlider(int y, int initialValue, int minimum, int maximum, int step, IntConsumer onValueChanged, IntFunction<String> formatter) {
		int width = Mth.clamp(this.optionsWidth * 55 / 100, 104, 260);
		int x = this.optionsX + this.optionsWidth - width;
		this.addScrolledDrawableChild(new PurpleIntSliderWidget(x, y, width, 20, initialValue, minimum, maximum, step, value -> {
			onValueChanged.accept(value);
			if (this.currentTab == MainTab.EFFECTS) {
				this.restartPreview();
			}
		}, formatter));
	}

	private PurpleButtonWidget purpleButton(Component message, PurplePressAction onPress, int x, int y, int width, int height, ButtonTone tone) {
		return new PurpleButtonWidget(x, y, width, height, message, onPress, tone);
	}

	private void drawFrame(GuiGraphicsExtractor context) {
		this.drawAnimatedParticleBackground(context);
		context.fill(0, 0, this.width, Math.max(48, this.height / 5), 0x331D0B35);
		context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
		context.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelWidth - 1, this.panelY + this.headerHeight - 1, COLOR_PANEL_DARK);
		context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
		int titleX = this.panelX + (this.panelWidth < 360 ? 10 : 14);
		context.text(this.font, this.title, titleX, this.panelY + 9, COLOR_TEXT, false);
		int versionX = titleX + 6 + this.font.width(this.title);
		context.fill(versionX - 3, this.panelY + 6, versionX + this.font.width("26.1.2") + 3, this.panelY + 19, 0x885E2DA8);
		context.text(this.font, Component.literal("26.1.2"), versionX, this.panelY + 9, COLOR_TEXT_MUTED, false);

		int horizontalPadding = this.panelWidth < 360 ? 8 : 12;
		int tabGap = this.panelWidth < 360 ? 4 : 6;
		int tabCount = MainTab.values().length;
		int tabWidth = Math.max(1, Math.min(108, (this.panelWidth - horizontalPadding * 2 - tabGap * (tabCount - 1)) / tabCount));
		int selectedX = Math.round(this.animatedMainTabX);
		context.fill(selectedX, this.panelY + 48, selectedX + tabWidth, this.panelY + 50, COLOR_PURPLE);
		int tabX = this.panelX + horizontalPadding;
		for (MainTab tab : MainTab.values()) {
			this.hoverRegions.add(new HoverRegion(tabX, this.panelY + 28, tabWidth, 20, tab.description));
			tabX += tabWidth + tabGap;
		}
	}

	private void drawAnimatedParticleBackground(GuiGraphicsExtractor context) {
		long now = Util.getMillis();
		for (int index = 0; index < BACKGROUND_PARTICLE_COUNT; index++) {
			int seed = mixParticleSeed(index + 1);
			int cycleMillis = 3600 + Math.floorMod(seed >>> 8, 4200);
			long offset = Math.floorMod(seed * 73L, cycleMillis);
			float progress = ((now + offset) % cycleMillis) / (float)cycleMillis;
			int travelWidth = Math.max(1, this.width + 32);
			int x = Math.floorMod(seed, travelWidth) - 16;
			float sway = Mth.sin(progress * (float)Math.PI * 2.0F + (seed & 31)) * (3.0F + Math.floorMod(seed >>> 13, 9));
			x += Math.round(sway);
			int y = this.height + 12 - Math.round(progress * (this.height + 36));
			int size = 1 + Math.floorMod(seed >>> 18, 3);
			float pulse = 0.45F + 0.55F * Mth.sin(progress * (float)Math.PI);
			int alpha = Mth.clamp(Math.round((38 + Math.floorMod(seed >>> 23, 86)) * pulse), 18, 128);
			int rgb = (index & 3) == 0 ? 0xD8A7FF : (index & 3) == 1 ? 0xB96BFF : 0x7C3CFF;
			if (size >= 2) {
				context.fill(x - 1, y - 1, x + size + 1, y + size + 1, ARGB.color(Math.max(8, alpha / 4), rgb));
			}
			context.fill(x, y, x + size, y + size, ARGB.color(alpha, rgb));
		}
	}

	private static int mixParticleSeed(int value) {
		value ^= value << 13;
		value ^= value >>> 17;
		value ^= value << 5;
		return value * 0x45D9F3B;
	}

	private void advanceTabAnimations(float deltaTicks) {
		int horizontalPadding = this.panelWidth < 360 ? 8 : 12;
		int mainGap = this.panelWidth < 360 ? 4 : 6;
		int mainCount = MainTab.values().length;
		int mainWidth = Math.max(1, Math.min(108, (this.panelWidth - horizontalPadding * 2 - mainGap * (mainCount - 1)) / mainCount));
		float mainTargetX = this.panelX + horizontalPadding + this.currentTab.ordinal() * (mainWidth + mainGap);
		float blend = Mth.clamp(deltaTicks * 0.42F, 0.08F, 1.0F);
		this.animatedMainTabX = Float.isNaN(this.animatedMainTabX) ? mainTargetX : Mth.lerp(blend, this.animatedMainTabX, mainTargetX);

		if (this.sidebarWidth > 0) {
			float targetY = this.contentY + 18 + this.currentEffectTab.ordinal() * 23.0F;
			this.animatedEffectTabY = Float.isNaN(this.animatedEffectTabY) ? targetY : Mth.lerp(blend, this.animatedEffectTabY, targetY);
		} else {
			int gap = this.optionsWidth < 360 ? 3 : 6;
			int count = EffectTab.values().length;
			int width = Math.max(1, Math.min(150, (this.optionsWidth - gap * (count - 1)) / count));
			float targetX = this.optionsX + this.currentEffectTab.ordinal() * (width + gap);
			this.animatedEffectTabX = Float.isNaN(this.animatedEffectTabX) ? targetX : Mth.lerp(blend, this.animatedEffectTabX, targetX);
		}
	}

	private void drawContent(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (this.currentTab == MainTab.EFFECTS) {
			this.drawPreview(context, mouseX, mouseY);
			this.drawEffectOptions(context);
		} else if (this.currentTab == MainTab.SOUND) {
			context.text(this.font, Component.literal("Custom death sound"), this.optionsX, this.contentY, COLOR_TEXT, false);
			this.drawSoundOptions(context);
		} else {
			context.text(this.font, Component.literal("Advanced"), this.optionsX, this.contentY, COLOR_TEXT, false);
			this.drawAdvancedOptions(context);
		}
	}

	private void drawEffectOptions(GuiGraphicsExtractor context) {
		if (this.sidebarWidth > 0) {
			context.fill(this.contentX - 4, this.contentY - 4, this.contentX + this.sidebarWidth + 4, this.optionsScrollBottom, 0x5510061D);
			context.text(this.font, Component.literal("EFFECTS"), this.contentX + 4, this.contentY + 4, COLOR_TEXT_DIM, false);
		}
		if (this.sidebarWidth > 0) {
			context.text(this.font, Component.literal(this.currentEffectTab.label + " Settings"), this.optionsX, this.contentY, COLOR_TEXT, false);
		}
		this.drawSelectedSubTabUnderline(context);
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);

		switch (this.currentEffectTab) {
			case SILHOUETTE -> this.drawSilhouetteOptions(context);
			case PLAYER -> this.drawPlayerOptions(context);
			case RAGDOLL -> this.drawRagdollOptions(context);
			case KIDS -> this.drawKidsOptions(context);
			case MORPH -> this.drawMorphOptions(context);
		}

		this.drawSwatches(context);
		context.disableScissor();
	}

	private void drawSelectedSubTabUnderline(GuiGraphicsExtractor context) {
		if (this.sidebarWidth > 0) {
			int activeY = Math.round(this.animatedEffectTabY);
			context.fill(this.contentX - 1, activeY, this.contentX + 3, activeY + 20, COLOR_PURPLE);
			int y = this.contentY + 18;
			for (EffectTab tab : EffectTab.values()) {
				this.hoverRegions.add(new HoverRegion(this.contentX, y, this.sidebarWidth, 20, tab.description));
				y += 23;
			}
			return;
		}

		int gap = this.optionsWidth < 360 ? 3 : 6;
		int count = EffectTab.values().length;
		int buttonWidth = Math.max(1, Math.min(150, (this.optionsWidth - gap * (count - 1)) / count));
		int x = Math.round(this.animatedEffectTabX);
		context.fill(x, this.optionsY + 20, x + buttonWidth, this.optionsY + 22, COLOR_PURPLE);
		x = this.optionsX;
		for (EffectTab tab : EffectTab.values()) {
			this.hoverRegions.add(new HoverRegion(x, this.optionsY, buttonWidth, 20, tab.description));
			x += buttonWidth + gap;
		}
	}

	private void drawSilhouetteOptions(GuiGraphicsExtractor context) {
		int logicalTop = this.logicalOptionsTop();
		int y = this.scrolledY(logicalTop + 5);
		this.drawLabel(context, "Active mode: Silhouette", y);

		y = this.scrolledY(logicalTop + ROW_STEP);
		this.drawChildLabel(context, "Color", y);
		int fieldWidth = Mth.clamp(this.optionsWidth - CHILD_INDENT, 104, 160);
		int fieldX = this.optionsX + CHILD_INDENT;
		int fieldY = this.scrolledY(logicalTop + ROW_STEP + 18);
		context.fill(fieldX - 2, fieldY - 2, fieldX + fieldWidth + 2, fieldY + 22, COLOR_PANEL_SOFT);
		context.outline(fieldX - 2, fieldY - 2, fieldWidth + 4, 24, COLOR_BORDER);

		y = this.scrolledY(logicalTop + ROW_STEP + 18 + 86);
		this.drawChildLabel(context, "Opacity", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Size", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Duration", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Height", y);
	}

	private void drawPlayerOptions(GuiGraphicsExtractor context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Active mode: Ghost player", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Movement", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Duration", y);

		if (this.config.playerGhostMovement == KohsDeathEffectsConfig.GhostMovementMode.RISING) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Height", y);
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Opacity", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Armor", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Held items", y);
	}

	private void drawRagdollOptions(GuiGraphicsExtractor context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Active mode: Faint", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Animation type", y);

		if (this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL) {
			y += ROW_STEP + 20;
			this.drawChildLabel(context, "Speed", y);
		}
	}

	private void drawKidsOptions(GuiGraphicsExtractor context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Active mode: Kids", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Display mode", y);

		y += ROW_STEP + 20;
		this.drawChildLabel(context, "Timer", y);

		if (this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Rope Size", y);

			y += ROW_STEP;
			this.drawChildLabel(context, "Maximum carried", y);
			this.drawValue(context, "20 players", y);

			y += ROW_STEP;
			this.drawChildLabel(context, "Train Doll Facing", y);
			y += 20;
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Animation", y);

		if (this.config.kidsAnimationEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Dragged Hand Movement", y);
		}
	}

	private void drawMorphOptions(GuiGraphicsExtractor context) {
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Active mode: Morph", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Morph to", y);
		this.drawValue(context, MorphMobCatalog.selectedName(this.config.morphEntityTypeId).getString(), y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Transparency", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Elevation", y);

		if (this.config.morphElevationEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Time", y);
		}

		y += ROW_STEP;
		this.drawChildLabel(context, "Mob sound", y);

		if (this.config.morphMobSoundEnabled) {
			y += ROW_STEP;
			this.drawChildLabel(context, "Volume", y);

			y += ROW_STEP;
			this.drawChildLabel(context, "Sound loop", y);
		}
	}

	private void drawAdvancedOptions(GuiGraphicsExtractor context) {
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);
		this.drawLabel(context, "Defaults", this.scrolledY(this.logicalOptionsTop() + 5));
		int y = this.scrolledY(this.logicalOptionsTop() + 5 + ROW_STEP * 2);
		this.drawChildLabel(context, "Vanilla death animation", y);
		context.disableScissor();
	}

	private void drawSoundOptions(GuiGraphicsExtractor context) {
		context.enableScissor(this.optionsX - 2, this.optionsScrollTop, this.optionsX + this.optionsWidth + 4, this.optionsScrollBottom);
		int y = this.scrolledY(this.logicalOptionsTop() + 5);
		this.drawLabel(context, "Custom death sound", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Volume", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "Custom sound file", y);

		y += ROW_STEP;
		this.drawChildLabel(context, "MP3 list", y);
		this.addOptionHover(y - 5, this.optionsScrollBottom - y + 5, "Select the MP3 used as the custom death sound.");
		if (DeathSoundManager.getSoundFiles().isEmpty()) {
			y += 24;
			context.text(this.font, Component.literal("No .mp3 files found"), this.optionsX + CHILD_INDENT, this.scrolledY(this.logicalOptionsTop() + 5 + ROW_STEP * 3 + 24), COLOR_TEXT_DIM, false);
		}

		String selectedName = selectedSoundName();
		if (!selectedName.isBlank()) {
			context.text(this.font, Component.literal("Selected: " + trimButtonLabel(selectedName, 28)), this.optionsX + CHILD_INDENT, this.optionsScrollBottom - 12, COLOR_TEXT_MUTED, false);
		}
		context.disableScissor();
	}

	private void drawPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (this.previewWidth <= 0 || this.previewHeight <= 0) {
			return;
		}

		int headerHeight = this.previewHeaderHeight();
		context.fill(this.previewX, this.previewY, this.previewX + this.previewWidth, this.previewY + this.previewHeight, COLOR_PANEL_SOFT);
		context.fill(this.previewX + 1, this.previewY + 1, this.previewX + this.previewWidth - 1, this.previewY + headerHeight, 0xAA2B1249);
		context.outline(this.previewX, this.previewY, this.previewWidth, this.previewHeight, COLOR_BORDER);
		context.text(this.font, Component.literal("Live Preview — " + this.currentEffectTab.label), this.previewX + 8, this.previewY + 6, COLOR_TEXT, false);
		if (headerHeight >= 34) {
			String state = this.previewStateText();
			context.text(
				this.font,
				Component.literal(this.font.plainSubstrByWidth(state, Math.max(30, this.previewWidth - 16))),
				this.previewX + 8,
				this.previewY + 19,
				COLOR_TEXT_MUTED,
				false
			);
		}

		int innerLeft = this.previewX + 8;
		int innerTop = this.previewY + headerHeight + 2;
		int innerRight = this.previewX + this.previewWidth - 8;
		int innerBottom = this.previewY + this.previewHeight - this.previewFooterSpace();
		if (innerBottom <= innerTop + 12) {
			return;
		}

		context.enableScissor(innerLeft, innerTop, innerRight, innerBottom);
		this.drawPreviewWorldBackground(context, innerLeft, innerTop, innerRight, innerBottom);
		if (this.currentEffectTab != EffectTab.KIDS) {
			KohsDeathEffectsClient.getKidsEffectManager().clearPreviewShoulders(KIDS_PREVIEW_ENTITY_ID);
		}
		if (this.currentEffectTab == EffectTab.SILHOUETTE) {
			context.fill(innerLeft, innerTop, innerRight, innerBottom, 0x3310061D);
		}
		context.fill(innerLeft, innerBottom - 1, innerRight, innerBottom, 0x66B96BFF);

		this.updatePreviewSimulation();
		float progress = this.previewFrame == null ? this.previewProgress() : this.previewFrame.progress();
		switch (this.currentEffectTab) {
			case SILHOUETTE -> this.drawSilhouettePreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case PLAYER -> this.drawPlayerPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case RAGDOLL -> this.drawRagdollPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case KIDS -> this.drawKidsPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
			case MORPH -> this.drawMorphPreview(context, innerLeft, innerTop, innerRight, innerBottom, progress);
		}
		context.disableScissor();
		this.drawPreviewHelp(context);
	}

	private int previewHeaderHeight() {
		return this.previewHeight >= 148 && this.previewWidth >= 170 ? 34 : 22;
	}

	private void drawPreviewWorldBackground(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
		int width = Math.max(1, right - left);
		int height = Math.max(1, bottom - top);
		float viewportAspect = width / (float)height;
		float imageAspect = PREVIEW_WORLD_IMAGE_WIDTH / (float)PREVIEW_WORLD_IMAGE_HEIGHT;
		float u0 = 0.0F;
		float u1 = 1.0F;
		float v0 = 0.0F;
		float v1 = 1.0F;
		if (viewportAspect < imageAspect) {
			float visibleWidth = viewportAspect / imageAspect;
			u0 = (1.0F - visibleWidth) * 0.5F;
			u1 = u0 + visibleWidth;
		} else {
			float visibleHeight = imageAspect / viewportAspect;
			v0 = (1.0F - visibleHeight) * 0.5F;
			v1 = v0 + visibleHeight;
		}

		context.blit(PREVIEW_WORLD_TEXTURE_ID, left, top, right, bottom, u0, u1, v0, v1);
		context.fill(left, top, right, bottom, 0x220B0614);
	}

	private String previewStateText() {
		return switch (this.currentEffectTab) {
			case SILHOUETTE -> String.format(Locale.ROOT, "Kill > Silhouette | %d%% | %.1fx", Math.round(this.config.silhouetteAlpha * 100.0F), this.config.silhouetteScale);
			case PLAYER -> "Kill > Ghost | " + movementText(this.config.playerGhostMovement).getString() + " | Armor " + (this.config.playerGhostArmorEnabled ? "on" : "off");
			case RAGDOLL -> "Kill > Faint | " + this.faintAnimationLabel() + (this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL ? " " + this.config.faintCrawlSpeed + "%" : "");
			case KIDS -> this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE
				? "Kill > Train | Rope " + this.config.kidsRopeSizePercent + "%"
				: "Kill > Shoulders | " + kidsTimerText(this.config.kidsTimerSeconds);
			case MORPH -> "Kill > Morph | " + MorphMobCatalog.selectedName(this.config.morphEntityTypeId).getString();
		};
	}

	private int previewFooterSpace() {
		return this.previewHeight >= 148 ? 52 : this.previewHeight >= 74 ? 30 : 8;
	}

	private int previewControlY() {
		return this.previewY + this.previewHeight - (this.previewHeight >= 148 ? 42 : 24);
	}

	private void drawPreviewHelp(GuiGraphicsExtractor context) {
		if (this.previewWidth <= 0 || this.previewHeight < 148) {
			return;
		}

		String text = this.currentEffectTab == EffectTab.KIDS
			? this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE
				? "Shoulders + defeated-player train | Drag / wheel"
				: "Two dolls sit on the shoulders | Drag / wheel"
			: this.previewCanOrbit() ? "Drag: rotate | Wheel: zoom" : "Preview follows the selected effect";
		int helpWidth = Math.max(40, this.previewWidth - 16 - (this.previewCanOrbit() ? 34 : 0));
		context.text(
			this.font,
			Component.literal(this.font.plainSubstrByWidth(text, helpWidth)),
			this.previewX + 8,
			this.previewControlY() + 24,
			COLOR_TEXT_DIM,
			false
		);
		if (this.previewCanOrbit()) {
			String zoom = Math.round(this.morphPreviewZoom * 100.0F) + "%";
			context.text(this.font, Component.literal(zoom), this.previewX + this.previewWidth - 8 - this.font.width(zoom), this.previewControlY() + 24, COLOR_TEXT_MUTED, false);
		}
	}

	private void drawGifPlaceholder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, String label) {
		int centerX = (left + right) / 2;
		int centerY = (top + bottom) / 2;
		int width = right - left;
		int height = bottom - top;
		context.outline(left + 8, top + 8, Math.max(20, width - 16), Math.max(20, height - 16), 0x889D63FF);
		context.fill(centerX - 18, centerY - 10, centerX + 18, centerY + 10, 0x665E2DA8);
		context.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(label, Math.max(34, width - 18))), centerX, centerY - 4, COLOR_TEXT_MUTED);
		if (height >= 56) {
			context.centeredText(this.font, Component.literal("here"), centerX, centerY + 8, COLOR_TEXT_DIM);
		}
	}

	private void drawSilhouettePreview(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		float previewVisibility = this.previewFrame == null ? 0.18F + silhouetteFade(progress) * 0.82F : this.previewFrame.alpha();
		float fade = this.config.risingSilhouetteEnabled ? previewVisibility : 0.18F;
		int alpha = Mth.clamp((int)(fade * 255.0F), 0, 255);
		int width = right - left;
		int height = bottom - top;
		float baseScale = Mth.clamp(Math.min(width, height) * 0.94F / 2.125F, 24.0F, 96.0F);
		float scale = Mth.clamp(baseScale * this.config.silhouetteScale * this.morphPreviewZoom, 18.0F, 150.0F);

		this.drawSilhouetteModel(context, left, top, right, bottom, scale, alpha);
	}

	private void drawPlayerPreview(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		int width = right - left;
		int height = bottom - top;
		int size = Mth.clamp((int)(Math.min(width, height) * 0.5F * this.morphPreviewZoom), 18, 96);
		PreviewPlayerEntity entity = this.getPreviewPlayer();

		if (entity == null) {
			this.drawSilhouetteModel(context, left, top, right, bottom, size, 255);
			return;
		}

		float fade = this.config.playerGhostEnabled
			? this.previewFrame == null ? (1.0F - progress) * this.config.playerGhostAlpha : this.previewFrame.alpha()
			: 0.24F;
		if (fade <= 0.035F) {
			return;
		}

		this.preparePreviewPlayer(entity);
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			context,
			left,
			top,
			right,
			bottom,
			size,
			0.0625F,
			(left + right) / 2.0F + this.morphPreviewYawOffset,
			(top + bottom) / 2.0F + this.morphPreviewPitchOffset,
			entity
		);
	}

	private void drawRagdollPreview(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		PlayerModel model = this.getRagdollPreviewModel();
		AvatarRenderState state = this.createRagdollPreviewState();
		PlayerSkin skin = this.currentPreviewSkin();
		float elapsedTicks = this.previewFrame == null ? progress * 120.0F : this.previewFrame.elapsedTicks();
		float fall = this.previewFrame == null ? Mth.clamp(elapsedTicks / 26.0F, 0.0F, 1.0F) : this.previewFrame.faintFall();
		float settle = this.previewFrame == null ? Mth.clamp((elapsedTicks - 26.0F) / 16.0F, 0.0F, 1.0F) : this.previewFrame.faintSettle();
		float crawlAmount = this.previewFrame == null
			? this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL
				? smoothStep(Mth.clamp((elapsedTicks - 54.0F) / 18.0F, 0.0F, 1.0F))
				: 0.0F
			: this.previewFrame.faintCrawlAmount();
		float crawlCycle = this.previewFrame == null
			? crawlAmount > 0.0F && progress < 0.82F
				? Mth.sin(elapsedTicks * (0.34F + this.config.faintCrawlSpeed / 260.0F))
				: 0.0F
			: this.previewFrame.faintCrawlCycle();
		model.setupAnim(state);
		applyFaintPreviewPose(model, fall, settle, crawlCycle, crawlAmount);
		orientFaintPreviewHorizontally(model);
		showAllPlayerModelParts(model);

		int width = right - left;
		int height = bottom - top;
		int cameraInsetX = Math.max(2, width / 18);
		int cameraInsetY = Math.max(2, height / 18);
		int cameraLeft = left + cameraInsetX;
		int cameraTop = top + cameraInsetY;
		int cameraRight = right - cameraInsetX;
		int cameraBottom = bottom - cameraInsetY;
		float horizontalFit = (cameraRight - cameraLeft) * 0.92F / 1.72F;
		float verticalFit = (cameraBottom - cameraTop) * 0.82F / 0.92F;
		float scale = Mth.clamp(Math.min(horizontalFit, verticalFit) * this.morphPreviewZoom, 22.0F, 124.0F);
		float previewAlpha = this.previewFrame == null ? 1.0F : this.previewFrame.alpha();
		if (previewAlpha <= 0.035F) {
			return;
		}
		this.drawTorsoCenteredSkin(
			context,
			model,
			skin.body().texturePath(),
			scale,
			5.0F + Mth.clamp(this.morphPreviewPitchOffset, -16.0F, 16.0F),
			30.0F + Mth.clamp(this.morphPreviewYawOffset, -38.0F, 38.0F),
			(cameraLeft + cameraRight) / 2,
			(cameraTop + cameraBottom) / 2
		);
	}

	private void drawKidsPreview(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		this.drawKidsPreviewLegacy(context, left, top, right, bottom, progress);
	}

	private void drawKidsPreviewImage(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
		int availableWidth = Math.max(1, right - left);
		int availableHeight = Math.max(1, bottom - top);
		float imageScale = Math.min(availableWidth / (float)KIDS_PREVIEW_IMAGE_WIDTH, availableHeight / (float)KIDS_PREVIEW_IMAGE_HEIGHT);
		int imageWidth = Math.max(1, Math.round(KIDS_PREVIEW_IMAGE_WIDTH * imageScale));
		int imageHeight = Math.max(1, Math.round(KIDS_PREVIEW_IMAGE_HEIGHT * imageScale));
		int imageX = left + (availableWidth - imageWidth) / 2;
		int imageY = top + (availableHeight - imageHeight) / 2;
		context.blitSprite(RenderPipelines.GUI_TEXTURED, KIDS_PREVIEW_TEXTURE_ID, imageX, imageY, imageWidth, imageHeight);
	}

	private void drawKidsPreviewLegacy(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		int width = right - left;
		int height = bottom - top;
		boolean cumulative = this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE;
		int carrierLeft = left;
		int carrierRight = cumulative ? left + Math.max(44, Math.round(width * 0.52F)) : right;
		carrierRight = Math.min(right, carrierRight);
		int centerX = (carrierLeft + carrierRight) / 2;
		int centerY = (top + bottom) / 2;
		float carrierScale = Mth.clamp(
			Math.min(carrierRight - carrierLeft, height) * 0.88F / 2.125F * this.morphPreviewZoom,
			20.0F,
			cumulative ? 78.0F : 96.0F
		);
		float elapsedTicks = this.previewFrame == null ? progress * 120.0F : this.previewFrame.elapsedTicks();
		KidsEffectManager kidsEffectManager = KohsDeathEffectsClient.getKidsEffectManager();
		KidsEffectManager.KidsPreviewFrame kidsFrame = kidsEffectManager.previewFrame(this.config, elapsedTicks);
		PlayerSkin skin = this.currentPreviewSkin();

		boolean showTrain = cumulative && width >= 64 && height >= 30;
		int trainStartX = Mth.clamp(centerX + Math.round(carrierScale * 0.26F), left + 4, right - 4);
		int trainEndX = right - Mth.clamp(Math.round(width * 0.08F), 5, 14);
		int trainBaseY = top + Math.round(height * 0.68F);
		int previousRopeX = trainStartX;
		int previousRopeY = Mth.clamp(centerY + Math.round(carrierScale * 0.23F), top + 4, bottom - 4);
		if (showTrain) {
			float maximumDistance = kidsFrame.draggedDolls().get(kidsFrame.draggedDolls().size() - 1).distance();
			int ropeThickness = Mth.clamp(Math.round(kidsFrame.ropeSizeMultiplier()), 1, 3);
			for (KidsEffectManager.DraggedDollPreview doll : kidsFrame.draggedDolls()) {
				float distanceProgress = maximumDistance <= 0.0F ? 0.0F : doll.distance() / maximumDistance;
				int dollX = Math.round(Mth.lerp(distanceProgress, trainStartX, trainEndX));
				int dollY = Mth.clamp(trainBaseY + Math.round(doll.sway() * height * 0.18F), top + 4, bottom - 4);
				drawPreviewRope(context, previousRopeX, previousRopeY, dollX, dollY, ropeThickness);
				previousRopeX = dollX;
				previousRopeY = dollY;
			}
		}

		PreviewPlayerEntity previewCarrier = this.getPreviewPlayer();
		if (previewCarrier != null) {
			this.preparePreviewPlayer(previewCarrier);
			kidsEffectManager.setPreviewShoulders(KIDS_PREVIEW_ENTITY_ID, skin, elapsedTicks, this.config.kidsAnimationEnabled);
			InventoryScreen.extractEntityInInventoryFollowsMouse(
				context,
				carrierLeft,
				top,
				carrierRight,
				bottom,
				Math.round(carrierScale),
				0.0625F,
				centerX + this.morphPreviewYawOffset,
				centerY + this.morphPreviewPitchOffset,
				previewCarrier
			);
		} else {
			kidsEffectManager.clearPreviewShoulders(KIDS_PREVIEW_ENTITY_ID);
			this.drawFallbackKidsCarrier(context, skin, kidsFrame, carrierScale, centerX, centerY);
		}

		if (showTrain) {
			float maximumDistance = kidsFrame.draggedDolls().get(kidsFrame.draggedDolls().size() - 1).distance();
			float draggedScale = Mth.clamp(
				Math.min(width, height) * 0.095F * kidsFrame.ropeSizeMultiplier() * this.morphPreviewZoom,
				7.0F,
				Math.max(9.0F, Math.min(27.0F, height * 0.34F))
			);
			for (int index = 0; index < kidsFrame.draggedDolls().size(); index++) {
				KidsEffectManager.DraggedDollPreview doll = kidsFrame.draggedDolls().get(index);
				PlayerModel draggedModel = this.getKidsDraggedPreviewModel(index);
				AvatarRenderState draggedState = this.createRagdollPreviewState();
				draggedModel.setupAnim(draggedState);
				KidsEffectManager.applyPreviewDraggedPose(draggedModel, doll.handWave());
				float distanceProgress = maximumDistance <= 0.0F ? 0.0F : doll.distance() / maximumDistance;
				int dollX = Math.round(Mth.lerp(distanceProgress, trainStartX, trainEndX));
				int dollY = Mth.clamp(trainBaseY + Math.round(doll.sway() * height * 0.18F), top + 4, bottom - 4);
				this.drawTorsoCenteredSkin(
					context,
					draggedModel,
					skin.body().texturePath(),
					draggedScale,
					kidsFrame.bodyPitch() + Mth.clamp(this.morphPreviewPitchOffset, -14.0F, 14.0F),
					205.0F + Mth.clamp(this.morphPreviewYawOffset, -70.0F, 70.0F),
					dollX,
					dollY
				);
			}
		}
	}

	private void drawFallbackKidsCarrier(
		GuiGraphicsExtractor context,
		PlayerSkin skin,
		KidsEffectManager.KidsPreviewFrame kidsFrame,
		float carrierScale,
		int centerX,
		int centerY
	) {
		float cameraPitch = Mth.clamp(this.morphPreviewPitchOffset, -25.0F, 25.0F);
		float cameraYaw = 30.0F + Mth.clamp(this.morphPreviewYawOffset, -70.0F, 70.0F);
		float shoulderScale = Mth.clamp(carrierScale * 0.22F, 7.0F, 22.0F);
		float yawRadians = cameraYaw * ((float)Math.PI / 180.0F);
		int shoulderOffsetX = Math.round(carrierScale * 0.34F * Math.max(0.30F, Math.abs(Mth.cos(yawRadians))));
		int shoulderY = centerY - Math.round(carrierScale * 0.43F);
		int farShoulder = Mth.sin(yawRadians) >= 0.0F ? 1 : 0;
		int nearShoulder = 1 - farShoulder;

		this.drawFallbackShoulderDoll(context, skin, kidsFrame, shoulderScale, cameraPitch, cameraYaw, centerX, shoulderY, shoulderOffsetX, farShoulder);

		PlayerModel carrierModel = this.getKidsCarrierPreviewModel();
		AvatarRenderState carrierState = this.createRagdollPreviewState();
		carrierModel.setupAnim(carrierState);
		showAllPlayerModelParts(carrierModel);
		this.drawTorsoCenteredSkin(context, carrierModel, skin.body().texturePath(), carrierScale, cameraPitch, cameraYaw, centerX, centerY);

		this.drawFallbackShoulderDoll(context, skin, kidsFrame, shoulderScale, cameraPitch, cameraYaw, centerX, shoulderY, shoulderOffsetX, nearShoulder);
	}

	private void drawFallbackShoulderDoll(
		GuiGraphicsExtractor context,
		PlayerSkin skin,
		KidsEffectManager.KidsPreviewFrame kidsFrame,
		float shoulderScale,
		float cameraPitch,
		float cameraYaw,
		int centerX,
		int shoulderY,
		int shoulderOffsetX,
		int index
	) {
		PlayerModel shoulderModel = this.getKidsShoulderPreviewModel(index);
		AvatarRenderState shoulderState = this.createRagdollPreviewState();
		shoulderModel.setupAnim(shoulderState);
		KidsEffectManager.applyPreviewShoulderPose(shoulderModel, kidsFrame.shoulderWaves().get(index));
		int side = index == 0 ? 1 : -1;
		this.drawTorsoCenteredSkin(
			context,
			shoulderModel,
			skin.body().texturePath(),
			shoulderScale,
			cameraPitch,
			cameraYaw,
			centerX + side * shoulderOffsetX,
			shoulderY
		);
	}

	private static void drawPreviewRope(GuiGraphicsExtractor context, int fromX, int fromY, int toX, int toY, int thickness) {
		int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
		if (steps <= 0) {
			return;
		}
		int radius = Math.max(0, thickness / 2);
		for (int step = 0; step <= steps; step++) {
			float amount = step / (float)steps;
			int x = Math.round(fromX + (toX - fromX) * amount);
			int y = Math.round(fromY + (toY - fromY) * amount);
			context.fill(x - radius - 1, y - radius - 1, x + radius + 2, y + radius + 2, 0xAA28170F);
			context.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, 0xFF70492B);
		}
	}

	private void drawMorphPreview(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float progress) {
		LivingEntity entity = this.getPreviewMorphEntity();
		if (entity == null) {
			this.drawGifPlaceholder(context, left, top, right, bottom, "Mob");
			return;
		}

		float fade = this.previewFrame == null ? morphFade(progress) * this.config.morphAlpha : this.previewFrame.alpha();
		if (fade <= 0.035F) {
			return;
		}
		int width = right - left;
		int height = bottom - top;
		float largestSide = Math.max(0.75F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
		int size = Mth.clamp((int)(Math.min(width, height) * 0.82F * this.morphPreviewZoom / largestSide), 8, 92);

		this.preparePreviewMorphEntity(entity);
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			context,
			left,
			top,
			right,
			bottom,
			size,
			0.0625F,
			(left + right) / 2.0F + this.morphPreviewYawOffset,
			(top + bottom) / 2.0F + this.morphPreviewPitchOffset,
			entity
		);
	}

	private void drawSilhouetteModel(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float scale, int alpha) {
		PlayerModel model = this.getSilhouettePreviewModel();
		AvatarRenderState state = this.createSilhouettePreviewState();
		model.setupAnim(state);
		showAllPlayerModelParts(model);
		float pitch = Mth.clamp(this.morphPreviewPitchOffset, -35.0F, 35.0F);
		float yaw = 180.0F + this.morphPreviewYawOffset;
		int tintAlpha = Mth.clamp(alpha, 0, 255);
		this.drawTorsoCenteredSkin(
			context,
			model,
			getSilhouettePreviewTexture(ARGB.color(tintAlpha, this.config.silhouetteColor)),
			scale,
			pitch,
			yaw,
			(left + right) / 2,
			(top + bottom) / 2
		);
	}

	private void drawTorsoCenteredSkin(
		GuiGraphicsExtractor context,
		PlayerModel model,
		Identifier texture,
		float scale,
		float rotationX,
		float rotationY,
		int torsoX,
		int torsoY
	) {
		int renderHeight = Math.max(24, Math.round(scale * 2.125F / 0.97F));
		int renderWidth = Math.max(renderHeight, Math.round(scale * 2.30F));
		int renderLeft = torsoX - renderWidth / 2;
		int renderTop = torsoY - renderHeight / 2;
		context.skin(
			model,
			texture,
			scale,
			rotationX,
			rotationY,
			PREVIEW_TORSO_PIVOT_Y,
			renderLeft,
			renderTop,
			renderLeft + renderWidth,
			renderTop + renderHeight
		);
	}

	private AvatarRenderState createSilhouettePreviewState() {
		AvatarRenderState state = new AvatarRenderState();
		state.entityType = EntityType.PLAYER;
		state.ageInTicks = 20.0F;
		state.boundingBoxWidth = 0.6F;
		state.boundingBoxHeight = 1.8F;
		state.eyeHeight = 1.62F;
		state.scale = 1.0F;
		state.ageScale = 1.0F;
		state.bodyRot = 0.0F;
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.pose = Pose.STANDING;
		state.mainArm = HumanoidArm.RIGHT;
		state.attackArm = HumanoidArm.RIGHT;
		state.useItemHand = InteractionHand.MAIN_HAND;
		PlayerSkin skin = this.currentPreviewSkin();
		state.skin = skin;
		state.scoreText = Component.literal(this.currentPlayerName());
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.showCape = false;
		return state;
	}

	private AvatarRenderState createRagdollPreviewState() {
		AvatarRenderState state = new AvatarRenderState();
		state.entityType = EntityType.PLAYER;
		state.ageInTicks = 20.0F;
		state.boundingBoxWidth = 0.6F;
		state.boundingBoxHeight = 1.8F;
		state.eyeHeight = 1.62F;
		state.scale = 1.0F;
		state.ageScale = 1.0F;
		state.bodyRot = 0.0F;
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.pose = Pose.STANDING;
		state.mainArm = HumanoidArm.RIGHT;
		state.attackArm = HumanoidArm.RIGHT;
		state.useItemHand = InteractionHand.MAIN_HAND;
		PlayerSkin skin = this.currentPreviewSkin();
		state.skin = skin;
		state.scoreText = Component.literal(this.currentPlayerName());
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.showCape = false;
		return state;
	}

	private PlayerModel getSilhouettePreviewModel() {
		boolean slim = this.currentPreviewSkin().model() == PlayerModelType.SLIM;
		if (this.silhouettePreviewModel == null || this.silhouettePreviewModelSlim != slim) {
			this.silhouettePreviewModel = new PlayerModel(
				Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
				slim
			);
			this.silhouettePreviewModelSlim = slim;
		}

		return this.silhouettePreviewModel;
	}

	private PlayerModel getRagdollPreviewModel() {
		boolean slim = this.currentPreviewSkin().model() == PlayerModelType.SLIM;
		if (this.ragdollPreviewModel == null || this.ragdollPreviewModelSlim != slim) {
			this.ragdollPreviewModel = new PlayerModel(
				Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
				slim
			);
			this.ragdollPreviewModelSlim = slim;
		}

		return this.ragdollPreviewModel;
	}

	private PlayerModel getKidsCarrierPreviewModel() {
		boolean slim = this.currentPreviewSkin().model() == PlayerModelType.SLIM;
		if (this.kidsCarrierPreviewModel == null || this.kidsCarrierPreviewModelSlim != slim) {
			this.kidsCarrierPreviewModel = new PlayerModel(
				Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
				slim
			);
			this.kidsCarrierPreviewModelSlim = slim;
		}

		return this.kidsCarrierPreviewModel;
	}

	private PlayerModel getKidsShoulderPreviewModel(int index) {
		boolean slim = this.currentPreviewSkin().model() == PlayerModelType.SLIM;
		int safeIndex = Mth.clamp(index, 0, this.kidsShoulderPreviewModels.length - 1);
		if (this.kidsShoulderPreviewModelSlim != slim) {
			java.util.Arrays.fill(this.kidsShoulderPreviewModels, null);
			this.kidsShoulderPreviewModelSlim = slim;
		}
		if (this.kidsShoulderPreviewModels[safeIndex] == null) {
			this.kidsShoulderPreviewModels[safeIndex] = new PlayerModel(
				Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
				slim
			);
		}

		return this.kidsShoulderPreviewModels[safeIndex];
	}

	private PlayerModel getKidsDraggedPreviewModel(int index) {
		boolean slim = this.currentPreviewSkin().model() == PlayerModelType.SLIM;
		int safeIndex = Mth.clamp(index, 0, this.kidsDraggedPreviewModels.length - 1);
		if (this.kidsDraggedPreviewModelSlim != slim) {
			java.util.Arrays.fill(this.kidsDraggedPreviewModels, null);
			this.kidsDraggedPreviewModelSlim = slim;
		}
		if (this.kidsDraggedPreviewModels[safeIndex] == null) {
			this.kidsDraggedPreviewModels[safeIndex] = new PlayerModel(
				Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER),
				slim
			);
		}

		return this.kidsDraggedPreviewModels[safeIndex];
	}

	private PlayerSkin currentPreviewSkin() {
		if (this.minecraft != null && this.minecraft.player != null) {
			return this.minecraft.player.getSkin();
		}

		return previewSkinTextures;
	}

	private String currentPlayerName() {
		if (this.minecraft != null && this.minecraft.player != null) {
			return this.minecraft.player.getName().getString();
		}

		return PREVIEW_PLAYER_NAME;
	}

	private String faintAnimationLabel() {
		return this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL ? "Crawl" : "Fall";
	}

	private static void applyFaintPreviewPose(PlayerModel model, float fall, float settle, float crawlCycle, float crawlAmount) {
		float limp = smoothStep(fall);
		float grounded = smoothStep(settle);
		float rightPull = (crawlCycle + 1.0F) * 0.5F;
		float leftPull = 1.0F - rightPull;
		float bodySway = crawlCycle * crawlAmount;

		model.head.xRot = Mth.lerp(grounded, 0.24F * limp, Mth.lerp(crawlAmount, 0.34F, -0.18F));
		model.head.yRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, -0.42F, bodySway * 0.10F));
		model.head.zRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, 0.10F, -bodySway * 0.05F));
		model.body.xRot = Mth.lerp(grounded, 0.10F * limp, Mth.lerp(crawlAmount, 0.03F, -0.08F + Math.abs(crawlCycle) * 0.04F));
		model.body.zRot = Mth.lerp(grounded, 0.0F, Mth.lerp(crawlAmount, -0.04F, bodySway * 0.07F));
		model.rightArm.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.70F + limp * 0.25F, 1.20F), -1.20F + rightPull * 0.58F);
		model.rightArm.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.16F, -0.48F), -0.58F + rightPull * 0.28F);
		model.rightArm.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.12F, 0.26F), 0.44F - rightPull * 0.20F);
		model.leftArm.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.68F + limp * 0.24F, 1.16F), -1.20F + leftPull * 0.58F);
		model.leftArm.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.16F, 0.46F), 0.58F - leftPull * 0.28F);
		model.leftArm.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.12F, -0.25F), -0.44F + leftPull * 0.20F);
		model.rightLeg.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.08F, 0.10F), 0.32F - rightPull * 0.12F);
		model.rightLeg.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.06F, 0.18F), 0.16F + rightPull * 0.12F);
		model.rightLeg.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.03F, 0.08F), 0.05F);
		model.leftLeg.xRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, 0.08F, -0.08F), 0.32F - leftPull * 0.12F);
		model.leftLeg.yRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.06F, -0.16F), -0.16F - leftPull * 0.12F);
		model.leftLeg.zRot = Mth.lerp(crawlAmount, Mth.lerp(grounded, -0.03F, -0.07F), -0.05F);
		resetPlayerOverlayTransforms(model);
	}

	private static void orientFaintPreviewHorizontally(PlayerModel model) {
		float angle = -(float)Math.PI / 2.0F;
		rotatePreviewPartAroundZ(model.head, 0.0F, 12.0F, angle);
		rotatePreviewPartAroundZ(model.body, 0.0F, 12.0F, angle);
		rotatePreviewPartAroundZ(model.rightArm, 0.0F, 12.0F, angle);
		rotatePreviewPartAroundZ(model.leftArm, 0.0F, 12.0F, angle);
		rotatePreviewPartAroundZ(model.rightLeg, 0.0F, 12.0F, angle);
		rotatePreviewPartAroundZ(model.leftLeg, 0.0F, 12.0F, angle);
		resetPlayerOverlayTransforms(model);
	}

	private static void rotatePreviewPartAroundZ(ModelPart part, float pivotX, float pivotY, float angle) {
		float translatedX = part.x - pivotX;
		float translatedY = part.y - pivotY;
		float cosine = Mth.cos(angle);
		float sine = Mth.sin(angle);
		part.x = pivotX + translatedX * cosine - translatedY * sine;
		part.y = pivotY + translatedX * sine + translatedY * cosine;
		part.zRot += angle;
	}

	private static void resetPlayerOverlayTransforms(PlayerModel model) {
		// In 26.1.2 these outer layers are children of their matching body parts.
		// Keeping their local pose at zero lets them inherit the horizontal preview
		// transform exactly once instead of duplicating the parent's rotation.
		model.hat.resetPose();
		model.leftSleeve.resetPose();
		model.rightSleeve.resetPose();
		model.leftPants.resetPose();
		model.rightPants.resetPose();
		model.jacket.resetPose();
	}

	private static void showAllPlayerModelParts(PlayerModel model) {
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
		if (this.minecraft == null || this.minecraft.level == null) {
			return null;
		}

		if (this.previewPlayer == null || this.previewPlayer.level() != this.minecraft.level) {
			this.previewPlayer = new PreviewPlayerEntity(this.minecraft.level, PREVIEW_FALLBACK_PROFILE);
			this.previewPlayer.setId(KIDS_PREVIEW_ENTITY_ID);
		}

		this.previewPlayer.setPreviewSkin(this.currentPreviewSkin());
		return this.previewPlayer;
	}

	private LivingEntity getPreviewMorphEntity() {
		if (this.minecraft == null || this.minecraft.level == null) {
			return null;
		}

		String mobId = MorphMobCatalog.sanitizeMobId(this.config.morphEntityTypeId);
		if (this.previewMorphEntity == null || !mobId.equals(this.previewMorphEntityId) || this.previewMorphEntity.level() != this.minecraft.level) {
			this.previewMorphEntity = MorphMobCatalog.createLivingEntity(this.minecraft.level, mobId);
			this.previewMorphEntityId = mobId;
		}

		return this.previewMorphEntity;
	}

	private void preparePreviewMorphEntity(LivingEntity entity) {
		float yaw = 180.0F;
		entity.tickCount = (int)((Util.getMillis() - this.previewStartedAt) / 50L);
		entity.setNoGravity(true);
		entity.setInvisible(false);
		entity.setSharedFlagOnFire(false);
		entity.setXRot(0.0F);
		entity.setYRot(yaw);
		entity.yBodyRot = yaw;
		entity.yBodyRotO = yaw;
		entity.yHeadRot = yaw;
		entity.yHeadRotO = yaw;
	}

	private void preparePreviewPlayer(PreviewPlayerEntity entity) {
		entity.setPose(Pose.STANDING);
		entity.setShiftKeyDown(false);
		entity.setInvisible(false);
		entity.setSharedFlagOnFire(false);
		entity.setXRot(0.0F);
		entity.setYRot(180.0F);
		entity.yBodyRot = 180.0F;
		entity.yHeadRot = 180.0F;
		entity.yHeadRotO = 180.0F;
		entity.swinging = false;
		entity.swingingArm = InteractionHand.MAIN_HAND;
		LivingEntity source = this.minecraft == null ? null : this.minecraft.player;
		if (source != null) {
			entity.snapTo(source.getX(), source.getY(), source.getZ(), 180.0F, 0.0F);
		}
		boolean showArmor = this.currentEffectTab != EffectTab.PLAYER || this.config.playerGhostArmorEnabled;
		boolean showHeldItems = this.currentEffectTab != EffectTab.PLAYER || this.config.playerGhostHeldItemsEnabled;
		entity.setItemSlot(EquipmentSlot.HEAD, this.copyEquipment(source, EquipmentSlot.HEAD, showArmor));
		entity.setItemSlot(EquipmentSlot.CHEST, this.copyEquipment(source, EquipmentSlot.CHEST, showArmor));
		entity.setItemSlot(EquipmentSlot.LEGS, this.copyEquipment(source, EquipmentSlot.LEGS, showArmor));
		entity.setItemSlot(EquipmentSlot.FEET, this.copyEquipment(source, EquipmentSlot.FEET, showArmor));
		entity.setItemInHand(InteractionHand.MAIN_HAND, this.copyHandStack(source, InteractionHand.MAIN_HAND, showHeldItems));
		entity.setItemInHand(InteractionHand.OFF_HAND, this.copyHandStack(source, InteractionHand.OFF_HAND, showHeldItems));
	}

	private ItemStack copyEquipment(LivingEntity source, EquipmentSlot slot, boolean enabled) {
		if (!enabled || source == null) {
			return ItemStack.EMPTY;
		}

		return source.getItemBySlot(slot).copy();
	}

	private ItemStack copyHandStack(LivingEntity source, InteractionHand hand, boolean enabled) {
		if (!enabled || source == null) {
			return ItemStack.EMPTY;
		}

		return source.getItemInHand(hand).copy();
	}

	private static void requestPreviewSkin() {
		if (previewSkinFuture != null) {
			return;
		}

		previewSkinFuture = fetchPreviewProfile(PREVIEW_PLAYER_NAME)
			.thenCompose(profile -> Minecraft.getInstance().getSkinManager().get(profile)
				.thenApply(skin -> skin.orElse(DefaultPlayerSkin.get(profile))))
			.exceptionally(throwable -> DefaultPlayerSkin.get(PREVIEW_FALLBACK_PROFILE))
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
				return new GameProfile(uuid, resolvedName);
			}, Util.nonCriticalIoPool());
	}

	private static Vec3 entityPosition(LivingEntity entity) {
		return new Vec3(entity.getX(), entity.getY(), entity.getZ());
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
		Minecraft client = Minecraft.getInstance();
		if (silhouettePreviewTexture == null) {
			silhouettePreviewTexture = new DynamicTexture("KoHs Death Effects Preview Silhouette", 2, 2, false);
			client.getTextureManager().register(SILHOUETTE_PREVIEW_TEXTURE_ID, silhouettePreviewTexture);
		}

		if (silhouettePreviewArgb != argb) {
			NativeImage image = silhouettePreviewTexture.getPixels();
			image.fillRect(0, 0, 2, 2, argb);
			silhouettePreviewTexture.upload();
			silhouettePreviewArgb = argb;
		}

		return SILHOUETTE_PREVIEW_TEXTURE_ID;
	}

	private void drawLabel(GuiGraphicsExtractor context, String label, int y) {
		this.drawLabel(context, label, y, 0);
	}

	private void drawChildLabel(GuiGraphicsExtractor context, String label, int y) {
		this.drawLabel(context, label, y, CHILD_INDENT);
	}

	private void drawLabel(GuiGraphicsExtractor context, String label, int y, int indent) {
		int x = this.optionsX + indent;
		int reservedForControls = indent == 0 ? 0 : (this.optionsWidth < 280 ? 96 : 128);
		int maxWidth = Math.max(28, this.optionsX + this.optionsWidth - x - reservedForControls);
		context.text(this.font, Component.literal(this.font.plainSubstrByWidth(label, maxWidth)), x, y, indent == 0 ? COLOR_TEXT : COLOR_TEXT_MUTED, false);
		String description = optionDescription(label);
		if (description != null) {
			this.addOptionHover(y - 5, optionHoverHeight(label), description);
		}
	}

	private void addOptionHover(int top, int height, String description) {
		int clippedTop = Math.max(top, this.optionsScrollTop);
		int clippedBottom = Math.min(top + height, this.optionsScrollBottom);
		if (clippedBottom > clippedTop) {
			this.hoverRegions.add(new HoverRegion(this.optionsX - 2, clippedTop, this.optionsWidth + 6, clippedBottom - clippedTop, description));
		}
	}

	private void drawHoverTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		HoverRegion hovered = null;
		for (HoverRegion region : this.hoverRegions) {
			if (region.contains(mouseX, mouseY)) {
				hovered = region;
			}
		}

		long now = System.nanoTime();
		float elapsedMillis = Math.min(50.0F, (now - this.tooltipFrameNanos) / 1_000_000.0F);
		this.tooltipFrameNanos = now;
		if (hovered != null) {
			if (!hovered.description().equals(this.displayedTooltip)) {
				this.displayedTooltip = hovered.description();
				this.tooltipOpacity = 0.0F;
			}
			this.tooltipAnchorX = mouseX;
			this.tooltipAnchorY = mouseY;
			this.tooltipOpacity = Math.min(1.0F, this.tooltipOpacity + elapsedMillis / TOOLTIP_FADE_IN_MILLIS);
		} else {
			this.tooltipOpacity = Math.max(0.0F, this.tooltipOpacity - elapsedMillis / TOOLTIP_FADE_OUT_MILLIS);
		}

		if (this.displayedTooltip == null || this.tooltipOpacity <= 0.0F) {
			return;
		}

		float easedOpacity = this.tooltipOpacity * this.tooltipOpacity * (3.0F - 2.0F * this.tooltipOpacity);
		int wrapWidth = Math.max(80, Math.min(TOOLTIP_MAX_WIDTH, this.width - 28));
		List<FormattedCharSequence> lines = this.font.split(Component.literal(this.displayedTooltip), wrapWidth - 12);
		int textWidth = 0;
		for (FormattedCharSequence line : lines) {
			textWidth = Math.max(textWidth, this.font.width(line));
		}
		int boxWidth = textWidth + 12;
		int boxHeight = lines.size() * 10 + 10;
		int x = this.tooltipAnchorX + 12;
		int y = this.tooltipAnchorY + 12 + Math.round((1.0F - easedOpacity) * 3.0F);
		if (x + boxWidth > this.width - 6) {
			x = this.tooltipAnchorX - boxWidth - 8;
		}
		if (this.currentTab == MainTab.EFFECTS && this.previewWidth > 0 && this.tooltipAnchorX < this.previewX) {
			x = Math.min(x, this.previewX - boxWidth - 6);
		}
		if (y + boxHeight > this.height - 6) {
			y = this.tooltipAnchorY - boxHeight - 8;
		}
		x = Mth.clamp(x, 6, Math.max(6, this.width - boxWidth - 6));
		y = Mth.clamp(y, 6, Math.max(6, this.height - boxHeight - 6));

		context.fill(x, y, x + boxWidth, y + boxHeight, tooltipColor(COLOR_PANEL_DARK, easedOpacity));
		context.fill(x + 2, y + 2, x + boxWidth - 2, y + 4, tooltipColor(COLOR_PURPLE_SOFT, easedOpacity));
		context.outline(x, y, boxWidth, boxHeight, tooltipColor(COLOR_PURPLE, easedOpacity));
		int textY = y + 6;
		for (FormattedCharSequence line : lines) {
			context.text(this.font, line, x + 6, textY, tooltipColor(COLOR_TEXT_MUTED, easedOpacity), true);
			textY += 10;
		}
	}

	private static int tooltipColor(int color, float opacity) {
		int sourceAlpha = color >>> 24;
		int fadedAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * opacity)));
		return fadedAlpha << 24 | color & 0x00FFFFFF;
	}

	private static int optionHoverHeight(String label) {
		return switch (label) {
			case "Color" -> 116;
			case "Animation type", "Display mode", "Train Doll Facing" -> 44;
			case "Defaults" -> 58;
			case "MP3 list" -> 96;
			default -> 24;
		};
	}

	private static String optionDescription(String label) {
		return switch (label) {
			case "Active mode: Silhouette" -> "Silhouette is the selected death effect.";
			case "Color" -> "Select the silhouette color or enter a hexadecimal color value.";
			case "Opacity" -> "Controls how transparent the effect appears.";
			case "Size" -> "Changes the rendered size of the silhouette.";
			case "Duration" -> "Sets how long the effect remains visible.";
			case "Height" -> "Sets how high the effect rises.";
			case "Active mode: Ghost player" -> "Ghost Player is the selected death effect.";
			case "Movement" -> "Choose whether the ghost remains static or rises.";
			case "Armor" -> "Shows the defeated player's armor on the ghost.";
			case "Held items" -> "Shows the defeated player's held items on the ghost.";
			case "Active mode: Faint" -> "Faint is the selected death effect.";
			case "Animation type" -> "Choose Fall or Crawl after the player reaches the ground.";
			case "Speed" -> "Controls how quickly the body crawls.";
			case "Active mode: Kids" -> "Kids is the selected death effect.";
			case "Display mode" -> "Cumulative carries up to 20 dolls; Only Shoulders carries two.";
			case "Timer" -> "Sets each doll's lifetime: 3-300 seconds for shoulders or 20-300 in Cumulative. Infinity prevents timed removal.";
			case "Rope Size" -> "Scales the dragged dolls, rope thickness, and train spacing.";
			case "Maximum carried" -> "Cumulative mode can carry up to 20 dolls.";
			case "Train Doll Facing" -> "Choose whether dragged dolls face the ground or the sky.";
			case "Animation" -> "Enables movement animations for Kids dolls.";
			case "Dragged Hand Movement" -> "Makes dragged dolls move their hands desperately.";
			case "Active mode: Morph" -> "Morph is the selected death effect.";
			case "Morph to" -> "Select the mob used by the Morph death effect.";
			case "Transparency" -> "Controls how transparent the morphed mob appears.";
			case "Elevation" -> "Makes the morphed mob rise while it fades.";
			case "Time" -> "Sets the duration of the elevation animation.";
			case "Mob sound" -> "Plays the selected mob's sound with the Morph effect.";
			case "Volume" -> "Controls the volume of this sound.";
			case "Sound loop" -> "Sets how many times the mob sound repeats.";
			case "Defaults" -> "Reset all settings to their default values.";
			case "Vanilla death animation" -> "Keeps or hides Minecraft's vanilla death animation.";
			case "Custom death sound" -> "Enables a custom sound when a player dies.";
			case "Custom sound file" -> "Open the sound folder or reload available MP3 files.";
			case "MP3 list" -> "Select the MP3 used as the custom death sound.";
			default -> null;
		};
	}

	private void drawValue(GuiGraphicsExtractor context, String value, int y) {
		String trimmed = this.font.plainSubstrByWidth(value, Math.max(30, this.optionsWidth / 3));
		int textWidth = this.font.width(trimmed);
		int right = this.optionsX + this.optionsWidth - 122;
		int x = Math.max(this.optionsX + CHILD_INDENT, right - textWidth);
		context.text(this.font, Component.literal(trimmed), x, y, COLOR_TEXT, false);
	}

	private void drawSwatches(GuiGraphicsExtractor context) {
		for (Swatch swatch : this.swatches) {
			context.fill(swatch.x(), swatch.y(), swatch.x() + swatch.size(), swatch.y() + swatch.size(), 0xFF000000 | swatch.color());
			context.outline(swatch.x() - 1, swatch.y() - 1, swatch.size() + 2, swatch.size() + 2,
				(this.config.silhouetteColor & 0xFFFFFF) == swatch.color() ? COLOR_TEXT : COLOR_BORDER);
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor context) {
		if (this.maxScroll <= 0) {
			return;
		}

		int trackX = Math.min(this.optionsX + this.optionsWidth + 3, this.panelX + this.panelWidth - 8);
		int trackHeight = this.optionsScrollBottom - this.optionsScrollTop;
		int contentHeight = trackHeight + this.maxScroll;
		int thumbHeight = Mth.clamp(trackHeight * trackHeight / contentHeight, 18, trackHeight);
		int thumbTravel = trackHeight - thumbHeight;
		int thumbY = this.optionsScrollTop + (thumbTravel == 0 ? 0 : this.scrollOffset * thumbTravel / this.maxScroll);

		context.fill(trackX, this.optionsScrollTop, trackX + 3, this.optionsScrollBottom, 0x663D2367);
		context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, COLOR_PURPLE);
	}

	private void drawOptionScrollFades(GuiGraphicsExtractor context) {
		drawScrollFades(
			context,
			this.optionsX - 2,
			this.optionsScrollTop,
			this.optionsX + this.optionsWidth + 4,
			this.optionsScrollBottom,
			this.scrollOffset,
			this.maxScroll
		);
	}

	private static void drawScrollFades(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int scrollOffset, int maxScroll) {
		if (maxScroll <= 0 || bottom <= top || right <= left) {
			return;
		}

		int fadeHeight = Mth.clamp((bottom - top) / 5, 10, 24);
		if (scrollOffset > 0) {
			context.fillGradient(left, top, right, top + fadeHeight, COLOR_SCROLL_FADE, COLOR_SCROLL_FADE_CLEAR);
		}

		if (scrollOffset < maxScroll) {
			context.fillGradient(left, bottom - fadeHeight, right, bottom, COLOR_SCROLL_FADE_CLEAR, COLOR_SCROLL_FADE);
		}
	}

	private void computeLayout() {
		int horizontalMargin = this.width < 400 ? 10 : this.width < 560 ? 16 : this.width < 760 ? 32 : 56;
		int verticalMargin = this.height < 240 ? 10 : this.height < 320 ? 16 : this.height < 460 ? 28 : 48;
		int maxPanelWidth = Math.max(1, this.width - horizontalMargin);
		int maxPanelHeight = Math.max(1, this.height - verticalMargin);
		int minPanelWidth = Math.min(340, maxPanelWidth);
		int minPanelHeight = Math.min(204, maxPanelHeight);
		int preferredWidth = Math.min(920, Math.round(this.width * 0.92F));
		int preferredHeight = Math.min(540, Math.round(this.height * 0.90F));
		this.panelWidth = Mth.clamp(preferredWidth, minPanelWidth, maxPanelWidth);
		this.panelHeight = Mth.clamp(preferredHeight, minPanelHeight, maxPanelHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		int contentPadding = this.panelWidth < 520 ? 12 : this.panelWidth < 720 ? 16 : 20;
		this.headerHeight = this.panelHeight < 250 ? 50 : this.panelHeight < 340 ? 56 : 62;
		this.footerHeight = this.panelHeight < 250 ? 32 : 38;
		this.contentX = this.panelX + contentPadding;
		this.contentY = this.panelY + this.headerHeight;
		this.contentWidth = this.panelWidth - contentPadding * 2;

		int footerTop = this.panelY + this.panelHeight - this.footerHeight;
		if (this.currentTab == MainTab.EFFECTS) {
			int contentAvailableHeight = footerTop - this.contentY - 4;
			// The preview has priority. A sidebar is useful only on genuinely wide
			// layouts; enabling it at 4x GUI scale used to consume the width that
			// the preview needed and made the preview disappear completely.
			this.sidebarWidth = this.contentWidth >= 720 && contentAvailableHeight >= 180
				? Mth.clamp((int)(this.contentWidth * 0.13F), 82, 106)
				: 0;
			int sidebarGap = this.sidebarWidth > 0 ? 12 : 0;
			int mainX = this.contentX + this.sidebarWidth + sidebarGap;
			int mainWidth = this.contentWidth - this.sidebarWidth - sidebarGap;
			this.optionsX = mainX;
			this.optionsY = this.contentY;
			int previewGap = mainWidth < 520 ? 8 : 12;
			int previewAvailableHeight = contentAvailableHeight;
			this.previewWidth = 0;
			this.previewHeight = 0;
			this.previewX = 0;
			this.previewY = 0;
			if (mainWidth >= 308 && previewAvailableHeight >= 112) {
				int wantedPreviewWidth = Mth.clamp((int)(mainWidth * 0.36F), 132, 300);
				int minOptionsWidth = mainWidth < 420 ? 168 : mainWidth < 560 ? 220 : 270;
				int maxPreviewWidth = Math.max(0, mainWidth - previewGap - minOptionsWidth);
				this.previewWidth = Math.min(wantedPreviewWidth, maxPreviewWidth);
				if (this.previewWidth >= 128) {
					this.previewHeight = previewAvailableHeight;
					this.previewX = this.contentX + this.contentWidth - this.previewWidth;
					this.previewY = this.contentY;
				} else {
					this.previewWidth = 0;
					this.previewHeight = 0;
				}
			}
			this.optionsWidth = this.previewWidth > 0 ? Math.max(168, this.previewX - this.optionsX - previewGap) : mainWidth;
			this.optionsScrollTop = this.optionsY + (this.sidebarWidth > 0 ? 26 : 24);
		} else {
			this.sidebarWidth = 0;
			this.previewX = 0;
			this.previewY = 0;
			this.previewWidth = 0;
			this.previewHeight = 0;
			this.optionsX = this.contentX;
			this.optionsY = this.contentY;
			this.optionsWidth = this.contentWidth;
			this.optionsScrollTop = this.optionsY + 24;
		}

		this.optionsScrollBottom = footerTop - 4;
	}

	private void updateScrollBounds() {
		this.maxScroll = Math.max(0, this.getLogicalContentBottom() - this.optionsScrollBottom + 8);
		this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
	}

	private int getLogicalContentBottom() {
		int y = this.logicalOptionsTop();
		int bottom = this.optionsScrollTop;

		if (this.currentTab == MainTab.ADVANCED) {
			return y + ROW_STEP * 2 + 20;
		}

		if (this.currentTab == MainTab.SOUND) {
			int rows = 1 + Math.max(1, DeathSoundManager.getSoundFiles().size());
			return y + 20 + ROW_STEP * 3 + rows * 24;
		}

		switch (this.currentEffectTab) {
			case SILHOUETTE -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 20;
				y += 18;
				bottom = y + 24;
				y += 44;
				bottom = y + 20;
				y += 42;
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
				bottom = y + 40;
				if (this.config.faintAnimationType == KohsDeathEffectsConfig.FaintAnimationType.CRAWL) {
					y += ROW_STEP + 20;
					bottom = y + 20;
				}
			}
			case KIDS -> {
				bottom = y + 20;
				y += ROW_STEP;
				bottom = y + 40;
				y += ROW_STEP + 20;
				bottom = y + 20;
				if (this.config.kidsMode == KohsDeathEffectsConfig.KidsMode.CUMULATIVE) {
					y += ROW_STEP;
					bottom = y + 20;
					y += ROW_STEP;
					bottom = y + 20;
					y += ROW_STEP;
					bottom = y + 40;
					y += 20;
				}
				y += ROW_STEP;
				bottom = y + 20;
				if (this.config.kidsAnimationEnabled) {
					y += ROW_STEP;
					bottom = y + 20;
				}
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

	private <T extends AbstractWidget> T addScrolledDrawableChild(T widget) {
		widget.setY(this.scrolledY(widget.getY()));
		if (this.isFullyVisible(widget.getY(), widget.getHeight())) {
			this.addRenderableWidget(widget);
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
				|| this.currentEffectTab == EffectTab.KIDS
				|| this.currentEffectTab == EffectTab.MORPH);
	}

	private float previewProgress() {
		if (this.previewPaused) {
			return this.pausedPreviewProgress;
		}

		int durationSeconds = switch (this.currentEffectTab) {
			case PLAYER -> this.config.playerGhostDurationSeconds;
			case RAGDOLL -> 6;
			case KIDS -> 6;
			case MORPH -> this.config.morphElevationTimeSeconds;
			case SILHOUETTE -> this.config.silhouetteDurationSeconds;
		};
		long durationMs = Math.max(1000L, durationSeconds * 1000L);
		return (Util.getMillis() - this.previewStartedAt) % durationMs / (float)durationMs;
	}

	private void restartPreview() {
		this.pausedPreviewProgress = 0.0F;
		this.previewStartedAt = Util.getMillis();
		this.previewSimulationDirty = true;
		this.previewFrame = null;
	}

	private void updatePreviewSimulation() {
		if (this.currentEffectTab == EffectTab.KIDS) {
			this.previewSimulation.clear();
			this.previewSimulationDirty = false;
			this.previewFrame = null;
			return;
		}

		if (this.previewSimulationDirty) {
			PreviewPlayerEntity entity = this.getPreviewPlayer();
			if (entity == null) {
				this.previewFrame = null;
				return;
			}

			this.preparePreviewPlayer(entity);
			this.previewSimulation.restart(entity, this.config);
			this.previewSimulationDirty = false;
		}

		this.previewFrame = this.previewSimulation.frame(this.previewPaused);
	}

	private void togglePreviewPaused() {
		if (this.previewPaused) {
			long durationMillis = this.previewDurationMillis();
			this.previewStartedAt = Util.getMillis() - (long)(this.pausedPreviewProgress * durationMillis);
			this.previewPaused = false;
		} else {
			this.pausedPreviewProgress = this.previewProgress();
			this.previewPaused = true;
		}
	}

	private long previewDurationMillis() {
		int durationSeconds = switch (this.currentEffectTab) {
			case PLAYER -> this.config.playerGhostDurationSeconds;
			case RAGDOLL, KIDS -> 6;
			case MORPH -> this.config.morphElevationTimeSeconds;
			case SILHOUETTE -> this.config.silhouetteDurationSeconds;
		};
		return Math.max(1000L, durationSeconds * 1000L);
	}

	private void resetPreviewView() {
		this.morphPreviewYawOffset = 0.0F;
		this.morphPreviewPitchOffset = 0.0F;
		this.morphPreviewZoom = 1.0F;
	}

	private void applyColorText(String value) {
		String normalized = value.startsWith("#") ? value.substring(1) : value;
		if (normalized.length() == 6 && normalized.matches("[0-9A-Fa-f]{6}")) {
			this.config.silhouetteColor = Integer.parseInt(normalized, 16);
			this.config.save();
			this.restartPreview();
			this.colorField.setTextColor(COLOR_TEXT);
		} else {
			this.colorField.setTextColor(0xFFFF7777);
		}
	}

	private static float silhouetteFade(float progress) {
		if (progress < 0.62F) {
			return 1.0F;
		}

		return 1.0F - Mth.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F);
	}

	private static float morphFade(float progress) {
		return 1.0F - smoothStep(progress);
	}

	private static float easeOutCubic(float progress) {
		float inverse = 1.0F - progress;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float smoothStep(float value) {
		float clamped = Mth.clamp(value, 0.0F, 1.0F);
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
		return Mth.clamp((elapsed - fadeStart) / fadeWindow, 0.0F, 1.0F);
	}

	private static Component onOff(boolean value) {
		return Component.literal(value ? "ON" : "OFF");
	}

	private static Component movementText(KohsDeathEffectsConfig.GhostMovementMode mode) {
		return Component.literal(mode == KohsDeathEffectsConfig.GhostMovementMode.STATIC ? "Static" : "Rising");
	}

	private static String kidsTimerText(int seconds) {
		return seconds >= 301 ? "Infinity" : seconds + "s";
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
			case KIDS -> EffectTab.KIDS;
			case MORPH -> EffectTab.MORPH;
			case SILHOUETTE -> EffectTab.SILHOUETTE;
		};
	}

	private enum MainTab {
		EFFECTS("Effects", "Configure the visual effect shown when a player dies."),
		SOUND("Sound", "Choose and adjust the custom death sound."),
		ADVANCED("Advanced", "Manage global compatibility and default behavior.");

		private final String label;
		private final String description;

		MainTab(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private enum EffectTab {
		SILHOUETTE("Silhouette", KohsDeathEffectsConfig.DeathEffectMode.SILHOUETTE, "Creates a colored silhouette that rises from the defeated player."),
		PLAYER("Player", KohsDeathEffectsConfig.DeathEffectMode.PLAYER_GHOST, "Creates a fading ghost copy of the defeated player."),
		RAGDOLL("Faint", KohsDeathEffectsConfig.DeathEffectMode.RAGDOLL, "Makes the defeated player fall or crawl along the ground."),
		MORPH("Morph", KohsDeathEffectsConfig.DeathEffectMode.MORPH, "Transforms the defeated player into the selected mob."),
		KIDS("Kids", KohsDeathEffectsConfig.DeathEffectMode.KIDS, "Turns defeated players into small dolls carried by their killer.");

		private final String label;
		private final KohsDeathEffectsConfig.DeathEffectMode mode;
		private final String description;

		EffectTab(String label, KohsDeathEffectsConfig.DeathEffectMode mode, String description) {
			this.label = label;
			this.mode = mode;
			this.description = description;
		}
	}

	private enum ButtonTone {
		NORMAL,
		SELECTED,
		SMALL,
		PRIMARY
	}

	private static void drawCenteredButtonText(GuiGraphicsExtractor context, PurpleButtonWidget button, int color) {
		Minecraft client = Minecraft.getInstance();
		String label = client.font.plainSubstrByWidth(button.getMessage().getString(), Math.max(8, button.getWidth() - 10));
		context.centeredText(
			client.font,
			Component.literal(label),
			button.getX() + button.getWidth() / 2,
			button.getY() + (button.getHeight() - 8) / 2,
			color
		);
	}

	@FunctionalInterface
	private interface PurplePressAction {
		void onPress(PurpleButtonWidget button);
	}

	private static final class PurpleIntSliderWidget extends AbstractSliderButton {
		private final int minimum;
		private final int maximum;
		private final int step;
		private final IntConsumer onValueChanged;
		private final IntFunction<String> formatter;
		private int currentValue;
		private double animatedValue;
		private long lastRenderMillis = Util.getMillis();

		private PurpleIntSliderWidget(
			int x,
			int y,
			int width,
			int height,
			int initialValue,
			int minimum,
			int maximum,
			int step,
			IntConsumer onValueChanged,
			IntFunction<String> formatter
		) {
			super(x, y, width, height, Component.literal(formatter.apply(snap(initialValue, minimum, maximum, step))), normalize(initialValue, minimum, maximum, step));
			this.minimum = minimum;
			this.maximum = maximum;
			this.step = Math.max(1, step);
			this.onValueChanged = onValueChanged;
			this.formatter = formatter;
			this.currentValue = snap(initialValue, minimum, maximum, this.step);
			this.animatedValue = this.value;
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(this.formatter.apply(this.currentValue)));
		}

		@Override
		protected void applyValue() {
			int nextValue = snap(
				(int)Math.round(this.minimum + this.value * (this.maximum - this.minimum)),
				this.minimum,
				this.maximum,
				this.step
			);
			this.value = normalize(nextValue, this.minimum, this.maximum, this.step);
			if (nextValue != this.currentValue) {
				this.currentValue = nextValue;
				this.onValueChanged.accept(nextValue);
			}
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			long now = Util.getMillis();
			float frame = Mth.clamp((now - this.lastRenderMillis) / 90.0F, 0.08F, 1.0F);
			this.lastRenderMillis = now;
			this.animatedValue = Mth.lerp(frame, (float)this.animatedValue, (float)this.value);
			int fill = this.active ? COLOR_PANEL_SOFT : 0x6630203F;
			int border = this.isHovered() || this.isFocused() ? COLOR_TEXT : COLOR_BORDER;
			int trackLeft = this.getX() + 4;
			int trackRight = this.getRight() - 4;
			int trackTop = this.getY() + this.getHeight() / 2 - 2;
			int handleX = trackLeft + (int)Math.round(this.animatedValue * (trackRight - trackLeft));

			context.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), fill);
			context.outline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
			context.fill(trackLeft, trackTop, trackRight, trackTop + 4, COLOR_PURPLE_DARK);
			context.fill(trackLeft, trackTop, handleX, trackTop + 4, COLOR_PURPLE);
			context.fill(handleX - 3, this.getY() + 3, handleX + 3, this.getBottom() - 3, this.isHovered() ? COLOR_TEXT : COLOR_PURPLE);

			Minecraft client = Minecraft.getInstance();
			String label = client.font.plainSubstrByWidth(this.getMessage().getString(), Math.max(8, this.getWidth() - 16));
			context.centeredText(client.font, Component.literal(label), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, COLOR_TEXT);
		}

		private static int snap(int value, int minimum, int maximum, int step) {
			int safeStep = Math.max(1, step);
			int clamped = Mth.clamp(value, minimum, maximum);
			int snapped = minimum + Math.round((clamped - minimum) / (float)safeStep) * safeStep;
			return Mth.clamp(snapped, minimum, maximum);
		}

		private static double normalize(int value, int minimum, int maximum, int step) {
			if (maximum <= minimum) {
				return 0.0;
			}
			return (snap(value, minimum, maximum, step) - minimum) / (double)(maximum - minimum);
		}
	}

	private static final class PurpleButtonWidget extends AbstractWidget {
		private final ButtonTone tone;
		private final PurplePressAction onPress;
		private float hoverAnimation;
		private long lastRenderMillis = Util.getMillis();
		private long pressedAtMillis = Long.MIN_VALUE;

		private PurpleButtonWidget(int x, int y, int width, int height, Component message, PurplePressAction onPress, ButtonTone tone) {
			super(x, y, width, height, message);
			this.tone = tone;
			this.onPress = onPress;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			long now = Util.getMillis();
			float frame = Mth.clamp((now - this.lastRenderMillis) / 90.0F, 0.08F, 1.0F);
			this.lastRenderMillis = now;
			this.hoverAnimation = Mth.lerp(frame, this.hoverAnimation, this.isHovered() && this.active ? 1.0F : 0.0F);
			float pressAnimation = Mth.clamp(1.0F - (now - this.pressedAtMillis) / 90.0F, 0.0F, 1.0F);
			int pressOffset = pressAnimation > 0.35F ? 1 : 0;
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

			if (this.hoverAnimation > 0.02F) {
				int glowAlpha = Mth.clamp(Math.round(this.hoverAnimation * 52.0F), 0, 52);
				context.fill(this.getX() - 1, this.getY() - 1, this.getRight() + 1, this.getBottom() + 1, ARGB.color(glowAlpha, 0xB96BFF));
			}
			context.fill(this.getX(), this.getY() + pressOffset, this.getRight(), this.getBottom() + pressOffset, fill);
			context.outline(this.getX(), this.getY() + pressOffset, this.getWidth(), this.getHeight(), border);
			if (this.tone == ButtonTone.PRIMARY || this.tone == ButtonTone.SELECTED) {
				context.fill(this.getX() + 1, this.getBottom() - 2 + pressOffset, this.getRight() - 1, this.getBottom() + pressOffset, COLOR_PURPLE);
			}
			drawCenteredButtonText(context, this, text);
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubleClick) {
			this.pressedAtMillis = Util.getMillis();
			this.onPress.onPress(this);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {
			builder.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	private static final class MorphToScreen extends Screen {
		private static final int ROW_HEIGHT = 36;
		private final KohsDeathEffectsConfigScreen parent;
		private final List<MorphMobCatalog.MobOption> options = MorphMobCatalog.options();
		private final Map<String, LivingEntity> previewEntities = new HashMap<>();
		private EditBox searchField;
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
			super(Component.literal("Morph to"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			this.computeLayout();
			this.updateSearchScrollBounds();

			this.searchField = new EditBox(this.font, this.panelX + 14, this.panelY + 51, this.panelWidth - 28, 20, Component.literal("Search mob"));
			this.searchField.setMaxLength(48);
			this.searchField.setBordered(false);
			this.searchField.setTextColor(COLOR_TEXT);
			this.searchField.setHint(Component.literal("Search mob"));
			this.searchField.setValue(this.searchQuery);
			this.searchField.setResponder(value -> {
				this.searchQuery = value;
				this.scrollOffset = 0;
				this.updateSearchScrollBounds();
			});
			this.searchField.setFocused(true);
			this.addRenderableWidget(this.searchField);

			this.addRenderableWidget(new PurpleButtonWidget(
				this.panelX + this.panelWidth - 90,
				this.panelY + this.panelHeight - 30,
				76,
				20,
				Component.literal("Back"),
				button -> this.onClose(),
				ButtonTone.PRIMARY
			));
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.text(this.font, this.title, this.panelX + 14, this.panelY + 12, COLOR_TEXT, false);
			context.text(this.font, Component.literal("Current mob: " + this.font.plainSubstrByWidth(MorphMobCatalog.selectedName(this.parent.config.morphEntityTypeId).getString(), Math.max(40, this.panelWidth - 124))), this.panelX + 14, this.panelY + 32, COLOR_TEXT_MUTED, false);
			context.fill(this.searchField.getX() - 2, this.searchField.getY() - 2, this.searchField.getRight() + 2, this.searchField.getBottom() + 2, COLOR_PANEL_SOFT);
			context.outline(this.searchField.getX() - 2, this.searchField.getY() - 2, this.searchField.getWidth() + 4, this.searchField.getHeight() + 4, COLOR_BORDER);
			context.enableScissor(this.panelX + 10, this.listTop, this.panelX + this.panelWidth - 10, this.listBottom);
			this.drawMobPreviews(context);
			this.drawMobRows(context, mouseX, mouseY);
			context.disableScissor();
			super.extractRenderState(context, mouseX, mouseY, deltaTicks);
			this.drawListScrollFades(context);
			this.drawScrollbar(context);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
			if (this.handleMobRowClick(click.x(), click.y())) {
				return true;
			}

			return super.mouseClicked(click, doubleClick);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (mouseY >= this.listTop && mouseY <= this.listBottom && this.maxScroll > 0) {
				this.scrollOffset = Mth.clamp(this.scrollOffset - (int)(verticalAmount * 24.0), 0, this.maxScroll);
				return true;
			}

			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		@Override
		public void onClose() {
			this.parent.config.save();
			if (this.minecraft != null) {
				this.minecraft.setScreen(this.parent);
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		private void computeLayout() {
			int horizontalMargin = this.width < 420 ? 18 : 90;
			int verticalMargin = this.height < 320 ? 18 : 70;
			this.panelWidth = Mth.clamp(this.width - horizontalMargin, Math.min(240, this.width - 12), Math.min(420, this.width - 12));
			this.panelHeight = Mth.clamp(this.height - verticalMargin, Math.min(190, this.height - 12), Math.min(460, this.height - 12));
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

		private void updateSearchScrollBounds() {
			this.maxScroll = Math.max(0, this.filteredOptions().size() * ROW_HEIGHT - (this.listBottom - this.listTop) + 8);
			this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
		}

		private void drawMobPreviews(GuiGraphicsExtractor context) {
			int previewLeft = this.panelX + 15;
			int y = this.listTop + 4 - this.scrollOffset;
			for (MorphMobCatalog.MobOption option : this.filteredOptions()) {
				if (y + 30 >= this.listTop && y <= this.listBottom) {
					context.fill(previewLeft, y, previewLeft + 30, y + 30, COLOR_PANEL_SOFT);
					context.outline(previewLeft, y, 30, 30, option.id().equals(this.parent.config.morphEntityTypeId) ? COLOR_PURPLE : COLOR_BORDER);
					this.drawMobPreview(context, option, previewLeft + 2, y + 2, previewLeft + 28, y + 28);
				}
				y += ROW_HEIGHT;
			}
		}

		private void drawMobRows(GuiGraphicsExtractor context, int mouseX, int mouseY) {
			int rowX = this.panelX + 54;
			int rowWidth = this.panelWidth - 70;
			int y = this.listTop + 4 - this.scrollOffset;
			for (MorphMobCatalog.MobOption option : this.filteredOptions()) {
				if (y + 30 >= this.listTop && y <= this.listBottom) {
					boolean selected = option.id().equals(this.parent.config.morphEntityTypeId);
					boolean hovered = mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= y && mouseY <= y + 30;
					int fill = selected ? COLOR_PURPLE_SOFT : hovered ? 0xAA2B1249 : COLOR_PANEL_SOFT;
					int border = selected ? COLOR_PURPLE : COLOR_BORDER;
					String prefix = selected ? "> " : "";
					String label = this.font.plainSubstrByWidth(prefix + option.name().getString(), Math.max(24, rowWidth - 12));
					context.fill(rowX, y, rowX + rowWidth, y + 30, fill);
					context.outline(rowX, y, rowWidth, 30, border);
					context.text(this.font, Component.literal(label), rowX + 7, y + 11, COLOR_TEXT, false);
				}
				y += ROW_HEIGHT;
			}
		}

		private boolean handleMobRowClick(double mouseX, double mouseY) {
			int rowX = this.panelX + 54;
			int rowWidth = this.panelWidth - 70;
			if (mouseX < rowX || mouseX > rowX + rowWidth || mouseY < this.listTop || mouseY > this.listBottom) {
				return false;
			}

			double relativeY = mouseY - this.listTop - 4 + this.scrollOffset;
			if (relativeY < 0.0 || relativeY % ROW_HEIGHT > 30.0) {
				return false;
			}

			int index = (int)(relativeY / ROW_HEIGHT);
			List<MorphMobCatalog.MobOption> filteredOptions = this.filteredOptions();
			if (index < 0 || index >= filteredOptions.size()) {
				return false;
			}

			MorphMobCatalog.MobOption option = filteredOptions.get(index);
			this.parent.config.morphEntityTypeId = option.id();
			this.parent.previewMorphEntity = null;
			this.parent.previewMorphEntityId = "";
			this.parent.config.save();
			this.parent.restartPreview();
			this.onClose();
			return true;
		}

		private void drawMobPreview(GuiGraphicsExtractor context, MorphMobCatalog.MobOption option, int left, int top, int right, int bottom) {
			if (this.minecraft == null || this.minecraft.level == null) {
				context.centeredText(this.font, Component.literal("?"), (left + right) / 2, top + 9, COLOR_TEXT_MUTED);
				return;
			}

			LivingEntity entity = this.previewEntities.computeIfAbsent(option.id(), id -> MorphMobCatalog.createLivingEntity(this.minecraft.level, id));
			if (entity == null) {
				context.centeredText(this.font, Component.literal("?"), (left + right) / 2, top + 9, COLOR_TEXT_MUTED);
				return;
			}

			entity.tickCount = (int)(Util.getMillis() / 50L);
			entity.setNoGravity(true);
			entity.setInvisible(false);
			entity.setSharedFlagOnFire(false);
			entity.setXRot(0.0F);
			entity.setYRot(180.0F);
			entity.yBodyRot = 180.0F;
			entity.yBodyRotO = 180.0F;
			entity.yHeadRot = 180.0F;
			entity.yHeadRotO = 180.0F;
			float largestSide = Math.max(0.75F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
			int size = Mth.clamp((int)(23.0F / largestSide), 5, 22);
			InventoryScreen.extractEntityInInventoryFollowsMouse(context, left, top, right, bottom, size, 0.0625F, (left + right) / 2.0F, (top + bottom) / 2.0F, entity);
		}

		private void drawScrollbar(GuiGraphicsExtractor context) {
			if (this.maxScroll <= 0) {
				return;
			}

			int trackX = this.panelX + this.panelWidth - 9;
			int trackHeight = this.listBottom - this.listTop;
			int contentHeight = trackHeight + this.maxScroll;
			int thumbHeight = Mth.clamp(trackHeight * trackHeight / contentHeight, 18, trackHeight);
			int thumbTravel = trackHeight - thumbHeight;
			int thumbY = this.listTop + (thumbTravel == 0 ? 0 : this.scrollOffset * thumbTravel / this.maxScroll);
			context.fill(trackX, this.listTop, trackX + 3, this.listBottom, 0x663D2367);
			context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, COLOR_PURPLE);
		}

		private void drawListScrollFades(GuiGraphicsExtractor context) {
			drawScrollFades(
				context,
				this.panelX + 10,
				this.listTop,
				this.panelX + this.panelWidth - 10,
				this.listBottom,
				this.scrollOffset,
				this.maxScroll
			);
		}
	}

	private static final class MorphSoundWarningScreen extends Screen {
		private final KohsDeathEffectsConfigScreen parent;
		private int panelX;
		private int panelY;
		private int panelWidth;
		private int panelHeight;

		private MorphSoundWarningScreen(KohsDeathEffectsConfigScreen parent) {
			super(Component.literal("Mob sound"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			this.computeLayout();
			int buttonY = this.panelY + this.panelHeight - 34;
			int buttonWidth = Mth.clamp((this.panelWidth - 38) / 2, 76, 128);
			this.addRenderableWidget(new PurpleButtonWidget(this.panelX + 14, buttonY, buttonWidth, 20, Component.literal("Back"), button -> this.onClose(), ButtonTone.NORMAL));
			this.addRenderableWidget(new PurpleButtonWidget(this.panelX + this.panelWidth - 14 - buttonWidth, buttonY, buttonWidth, 20, Component.literal("Continue"), button -> {
				this.parent.config.morphMobSoundEnabled = true;
				this.parent.config.customDeathSoundEnabled = false;
				this.parent.config.save();
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}, ButtonTone.PRIMARY));
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.centeredText(this.font, this.title, this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);
			int textWidth = this.panelWidth - 28;
			context.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth("Enabling Mob sound will disable", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 44, COLOR_TEXT_MUTED);
			context.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth("the selected custom death sound.", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 58, COLOR_TEXT_MUTED);
			super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		}

		@Override
		public void onClose() {
			if (this.minecraft != null) {
				this.minecraft.setScreen(this.parent);
			}
		}

		@Override
		public boolean isPauseScreen() {
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
			super(Component.literal("Custom sound"));
			this.parent = parent;
			this.selectedSoundId = selectedSoundId;
		}

		@Override
		protected void init() {
			this.computeLayout();
			int buttonY = this.panelY + this.panelHeight - 34;
			int buttonWidth = Mth.clamp((this.panelWidth - 38) / 2, 76, 128);
			this.addRenderableWidget(new PurpleButtonWidget(this.panelX + 14, buttonY, buttonWidth, 20, Component.literal("Back"), button -> this.onClose(), ButtonTone.NORMAL));
			this.addRenderableWidget(new PurpleButtonWidget(this.panelX + this.panelWidth - 14 - buttonWidth, buttonY, buttonWidth, 20, Component.literal("Continue"), button -> {
				this.parent.applyCustomDeathSound(this.selectedSoundId);
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}, ButtonTone.PRIMARY));
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
			context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
			context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
			context.centeredText(this.font, this.title, this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);
			int textWidth = this.panelWidth - 28;
			context.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth("Enabling Custom sound will disable", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 44, COLOR_TEXT_MUTED);
			context.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth("Morph mob sound.", textWidth)), this.panelX + this.panelWidth / 2, this.panelY + 58, COLOR_TEXT_MUTED);
			super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		}

		@Override
		public void onClose() {
			if (this.minecraft != null) {
				this.minecraft.setScreen(this.parent);
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		private void computeLayout() {
			this.panelWidth = Math.min(Math.max(230, this.width - 36), 340);
			this.panelHeight = Math.min(Math.max(118, this.height - 36), 150);
			this.panelX = (this.width - this.panelWidth) / 2;
			this.panelY = (this.height - this.panelHeight) / 2;
		}
	}

	private static final class PreviewPlayerEntity extends AbstractClientPlayer {
		private PlayerSkin skinTextures = previewSkinTextures;

		private PreviewPlayerEntity(ClientLevel world, GameProfile profile) {
			super(world, profile);
		}

		private void setPreviewSkin(PlayerSkin skinTextures) {
			this.skinTextures = skinTextures;
		}

		@Override
		public PlayerSkin getSkin() {
			return this.skinTextures;
		}

		@Override
		public boolean isModelPartShown(PlayerModelPart part) {
			return true;
		}
	}

	private record Swatch(int x, int y, int size, int color) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.size && mouseY >= this.y && mouseY < this.y + this.size;
		}
	}

	private record HoverRegion(int x, int y, int width, int height, String description) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}
}
