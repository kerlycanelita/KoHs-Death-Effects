package com.kohs.deatheffects.client.config;

import java.util.List;

import com.kohs.deatheffects.KohsDeathEffectsConfig;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

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
		super(Text.literal("KoHs Death Effects"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.computeLayout();
		int buttonWidth = MathHelper.clamp((this.panelWidth - 42) / 2, 92, 150);
		int gap = 10;
		int buttonsWidth = buttonWidth * 2 + gap;
		int buttonX = this.panelX + (this.panelWidth - buttonsWidth) / 2;
		int buttonY = this.panelY + this.panelHeight - 34;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("No volver a mostrar"), button -> {
			KohsDeathEffectsConfig config = KohsDeathEffectsConfig.get();
			config.betaWarningDismissed = true;
			config.save();
			this.close();
		}).dimensions(buttonX, buttonY, buttonWidth, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Continuar"), button -> this.close())
			.dimensions(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20)
			.build());
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		context.fill(0, 0, this.width, this.height, COLOR_SCREEN_SHADE);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		this.renderBackground(context, mouseX, mouseY, deltaTicks);
		context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
		context.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelWidth - 1, this.panelY + 38, COLOR_PANEL_DARK);
		context.drawStrokedRectangle(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("LANZAMIENTO BETA"), this.panelX + this.panelWidth / 2, this.panelY + 14, COLOR_TEXT);

		List<OrderedText> lines = this.textRenderer.wrapLines(
			Text.literal("Este mod esta actualmente en beta. Si encuentras errores, comunicalos a zymekoh. Si deseas aportar algo, tambien puedes contactar a zymekoh."),
			this.panelWidth - 32
		);
		int y = this.panelY + 54;
		for (OrderedText line : lines) {
			context.drawText(this.textRenderer, line, this.panelX + 16, y, COLOR_TEXT_MUTED, false);
			y += 12;
		}

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
		int maxWidth = Math.max(220, this.width - 28);
		int maxHeight = Math.max(140, this.height - 28);
		this.panelWidth = MathHelper.clamp(this.width < 520 ? maxWidth : 430, Math.min(220, maxWidth), Math.min(430, maxWidth));
		this.panelHeight = MathHelper.clamp(this.height < 260 ? maxHeight : 170, Math.min(138, maxHeight), Math.min(190, maxHeight));
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
	}
}
