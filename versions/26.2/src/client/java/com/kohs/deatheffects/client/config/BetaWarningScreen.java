package com.kohs.deatheffects.client.config;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.kohs.deatheffects.KohsDeathEffectsConfig;

public final class BetaWarningScreen extends Screen {
	private static final int COLOR_SCREEN_SHADE = 0xE40A0613;
	private static final int COLOR_PANEL = 0xF0140A24;
	private static final int COLOR_PANEL_DARK = 0xF00C0616;
	private static final int COLOR_BORDER = 0xFF9D63FF;
	private static final int COLOR_TEXT = 0xFFF4ECFF;
	private static final int COLOR_TEXT_MUTED = 0xFFCDBAE8;

	private final Screen parent;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public BetaWarningScreen(Screen parent) {
		super(Component.literal("KoHs Death Effects"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.computeLayout();
		int buttonWidth = Mth.clamp((this.panelWidth - 42) / 2, 92, 150);
		int gap = 10;
		int buttonsWidth = buttonWidth * 2 + gap;
		int buttonX = this.panelX + (this.panelWidth - buttonsWidth) / 2;
		int buttonY = this.panelY + this.panelHeight - 34;

		this.addRenderableWidget(Button.builder(Component.literal("No volver a mostrar"), button -> {
			KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
			config.betaWarningDismissed = true;
			config.save();
			this.onClose();
		}).bounds(buttonX, buttonY, buttonWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Continuar"), button -> this.onClose())
			.bounds(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20)
			.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		this.extractBackground(context, mouseX, mouseY, deltaTicks);
		context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
		context.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelWidth - 1, this.panelY + 38, COLOR_PANEL_DARK);
		context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
		context.centeredText(this.font, Component.literal("LANZAMIENTO BETA"), this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);

		List<FormattedCharSequence> lines = this.font.split(
			Component.literal("Este mod esta actualmente en beta. Si encuentras errores, comunicalos a zymekoh. Si deseas aportar algo, tambien puedes contactar a zymekoh."),
			this.panelWidth - 32
		);
		int y = this.panelY + 54;
		for (FormattedCharSequence line : lines) {
			context.text(this.font, line, this.panelX + 16, y, COLOR_TEXT_MUTED, false);
			y += 12;
		}

		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void computeLayout() {
		int maxWidth = Math.max(220, this.width - 28);
		int maxHeight = Math.max(140, this.height - 28);
		this.panelWidth = Mth.clamp(this.width < 520 ? maxWidth : 430, Math.min(220, maxWidth), Math.min(430, maxWidth));
		this.panelHeight = Mth.clamp(this.height < 260 ? maxHeight : 170, Math.min(138, maxHeight), Math.min(190, maxHeight));
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
	}
}

