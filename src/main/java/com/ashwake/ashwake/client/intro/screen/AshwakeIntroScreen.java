package com.ashwake.ashwake.client.intro.screen;

import com.ashwake.ashwake.network.OpenIntroGuiPayload;
import com.ashwake.ashwake.network.WeatherCoreNetwork;
import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class AshwakeIntroScreen extends Screen {
    private static final ResourceLocation LOGO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ashwake", "textures/gui/ashwake_logo.png");
    private static final ResourceLocation LOGO_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ashwake", "textures/gui/ashwake_logo_glow.png");

    private static final int FADE_IN_TICKS = 12;
    private static final int CLOSE_FADE_TICKS = 10;
    private static final int BUTTONS_APPEAR_TICKS = 24;
    private static final int MAX_PARTICLES = 24;

    private static final int OUTER_PAD = 16;
    private static final int INNER_GAP = 10;
    private static final int FOOTER_H_LARGE = 54;
    private static final int FOOTER_H_COMPACT = 72;
    private static final int STORY_LINE_SPACING = 2;

    private static final String DEBUG_SHARP_TEXT = "DEBUG_SHARP_TEXT_123";
    private static final boolean SHOW_DEBUG_SHARP_TEXT = false;

    private final OpenIntroGuiPayload payload;
    private final List<OverlayParticle> particles = new ArrayList<>();
    private final Random rng = new Random();

    private Button beginButton;
    private Button learnMoreButton;

    private boolean dontShowAgainChecked = true;
    private boolean disableTutorialPopupsChecked;
    private boolean closing;
    private int closeTicks;
    private int ageTicks;
    private int nextAshSpawnTick;
    private boolean sentCloseAck;
    private boolean playedHintChime;
    private boolean finalizedClose;
    private int storyScrollOffset;

    public AshwakeIntroScreen(OpenIntroGuiPayload payload) {
        super(Component.translatable("intro.ashwake.title"));
        this.payload = payload;
    }

    public boolean allowDisableTutorialPopups() {
        return payload.allowDisableTutorialPopups();
    }

    public boolean isDisableTutorialPopupsChecked() {
        return disableTutorialPopupsChecked;
    }

    public void setDisableTutorialPopupsChecked(boolean checked) {
        disableTutorialPopupsChecked = checked;
    }

    public void beginCloseFromLearnMore() {
        if (this.minecraft != null && this.minecraft.screen != this) {
            this.minecraft.setScreen(this);
        }
        beginClose();
    }

    @Override
    protected void init() {
        beginButton = this.addRenderableWidget(Button.builder(Component.translatable("intro.ashwake.button.begin"), b -> beginClose())
                .bounds(0, 0, 112, 20)
                .build());

        learnMoreButton = this.addRenderableWidget(Button.builder(Component.translatable("intro.ashwake.button.learn_more"), b -> {
                    if (this.minecraft == null || !payload.allowLearnMore()) {
                        return;
                    }
                    this.minecraft.setScreen(new AshwakeIntroLearnMoreScreen(this));
                })
                .bounds(0, 0, 112, 20)
                .build());

        Layout layout = computeLayout();
        updateButtons(layout);

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.BEACON_ACTIVATE, 0.25F, 0.84F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;

        if (closing) {
            closeTicks++;
            if (closeTicks >= CLOSE_FADE_TICKS && !finalizedClose) {
                finalizedClose = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
            }
            updateParticles();
            return;
        }

        if (ageTicks >= nextAshSpawnTick) {
            spawnAshMote();
            nextAshSpawnTick = ageTicks + (7 + this.rng.nextInt(5));
        }

        if (ageTicks <= BUTTONS_APPEAR_TICKS && (ageTicks % 3) == 0) {
            spawnLogoEmber();
        }

        if (!playedHintChime && ageTicks >= 26) {
            playedHintChime = true;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.BEACON_AMBIENT, 0.05F, 1.32F);
            }
        }

        updateParticles();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = computeLayout();
        if (layout.showCheckbox && insideBox(mouseX, mouseY, layout.checkboxX, layout.checkboxY, 12, 12)) {
            dontShowAgainChecked = !dontShowAgainChecked;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = computeLayout();
        if (!insideBox(mouseX, mouseY, layout.storyX, layout.storyY, layout.storyW, layout.storyH)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int maxScroll = maxStoryScroll(layout);
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (scrollY > 0.0D) {
            storyScrollOffset = Math.max(0, storyScrollOffset - 1);
            return true;
        }

        if (scrollY < 0.0D) {
            storyScrollOffset = Math.min(maxScroll, storyScrollOffset + 1);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        beginClose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
        // This screen draws its own backdrop and must not call vanilla menu blur.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.gameRenderer != null) {
            this.minecraft.gameRenderer.shutdownEffect();
        }

        Layout layout = computeLayout();
        updateButtons(layout);

        float alpha = currentAlpha(partialTick);
        drawBackdrop(guiGraphics, alpha);
        drawParticles(guiGraphics, alpha, layout);
        drawPanel(guiGraphics, alpha, layout);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawDebugSharpText(guiGraphics);
    }

    private void drawBackdrop(GuiGraphics guiGraphics, float alpha) {
        int overlayAlpha = (int) (Mth.clamp(alpha * 0.76F, 0.0F, 1.0F) * 255.0F);
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        for (int i = 0; i < 4; i++) {
            float ring = (4 - i) / 4.0F;
            int bloomAlpha = (int) (26.0F * alpha * ring);
            int radiusX = (int) (this.width * (0.24F + (i * 0.10F)));
            int radiusY = (int) (this.height * (0.17F + (i * 0.08F)));
            guiGraphics.fill(
                    centerX - radiusX,
                    centerY - radiusY,
                    centerX + radiusX,
                    centerY + radiusY,
                    ((bloomAlpha & 0xFF) << 24) | 0x6B2600);
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, float alpha, Layout layout) {
        int outer = withAlpha(0x1B1A19, alpha * 0.90F);
        int inner = withAlpha(0x25211E, alpha * 0.93F);
        int border = withAlpha(0xA55F24, alpha * 0.88F);
        int cornerGlow = withAlpha(0xD97B27, alpha * 0.40F);

        guiGraphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + layout.panelH, outer);
        guiGraphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + layout.panelW - 2, layout.panelY + layout.panelH - 2, inner);

        guiGraphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + 2, border);
        guiGraphics.fill(layout.panelX, layout.panelY + layout.panelH - 2, layout.panelX + layout.panelW, layout.panelY + layout.panelH, border);
        guiGraphics.fill(layout.panelX, layout.panelY, layout.panelX + 2, layout.panelY + layout.panelH, border);
        guiGraphics.fill(layout.panelX + layout.panelW - 2, layout.panelY, layout.panelX + layout.panelW, layout.panelY + layout.panelH, border);

        guiGraphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + 18, layout.panelY + 18, cornerGlow);
        guiGraphics.fill(layout.panelX + layout.panelW - 18, layout.panelY + 2, layout.panelX + layout.panelW - 2, layout.panelY + 18, cornerGlow);
        guiGraphics.fill(layout.panelX + 2, layout.panelY + layout.panelH - 18, layout.panelX + 18, layout.panelY + layout.panelH - 2, cornerGlow);
        guiGraphics.fill(layout.panelX + layout.panelW - 18, layout.panelY + layout.panelH - 18, layout.panelX + layout.panelW - 2, layout.panelY + layout.panelH - 2, cornerGlow);

        drawHeader(guiGraphics, layout, alpha);
        drawStoryPlate(guiGraphics, layout, alpha);
        drawSummaryCard(guiGraphics, layout, alpha);
        drawFooter(guiGraphics, layout, alpha);
    }

    private void drawHeader(GuiGraphics guiGraphics, Layout layout, float alpha) {
        int logoW = Math.min(layout.contentW, (int) (layout.logoH / 0.38F));
        int logoX = layout.panelX + (layout.panelW - logoW) / 2;

        float ignite = Mth.clamp((ageTicks - 8) / 22.0F, 0.0F, 1.0F);
        float brightness = 0.46F + (ignite * 0.54F);
        float glowAlpha = alpha * (0.12F + (ignite * 0.38F));

        guiGraphics.setColor(brightness, brightness, brightness, alpha);
        guiGraphics.blit(LOGO_TEXTURE, logoX, layout.logoY, 0, 0, logoW, layout.logoH, logoW, layout.logoH);

        guiGraphics.setColor(1.0F, 0.63F, 0.28F, glowAlpha);
        guiGraphics.blit(LOGO_GLOW_TEXTURE, logoX - 4, layout.logoY - 3, 0, 0, logoW + 8, layout.logoH + 6, logoW + 8, layout.logoH + 6);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.drawCenteredString(this.font, this.title, layout.panelX + (layout.panelW / 2), layout.titleY, 0xFFF1DEC2);
    }

    private void drawStoryPlate(GuiGraphics guiGraphics, Layout layout, float alpha) {
        int plateColor = withAlpha(0x100F0E, Math.min(0.66F, 0.50F + (alpha * 0.12F)));
        int plateBorder = withAlpha(0x8E4F22, Math.min(0.70F, 0.44F + (alpha * 0.18F)));

        guiGraphics.fill(layout.storyX, layout.storyY, layout.storyX + layout.storyW, layout.storyY + layout.storyH, plateColor);
        guiGraphics.fill(layout.storyX, layout.storyY, layout.storyX + layout.storyW, layout.storyY + 1, plateBorder);

        int innerX = layout.storyX + 8;
        int innerY = layout.storyY + 8;
        int innerW = Math.max(40, layout.storyW - 16);
        int innerH = Math.max(24, layout.storyH - 16);

        List<StoryLine> lines = buildStoryLines(innerW, layout.tiny);
        int lineStep = this.font.lineHeight + STORY_LINE_SPACING;
        int visibleLines = Math.max(1, innerH / lineStep);
        int maxScroll = Math.max(0, lines.size() - visibleLines);

        storyScrollOffset = Mth.clamp(storyScrollOffset, 0, maxScroll);

        int drawY = innerY;
        for (int idx = storyScrollOffset; idx < Math.min(lines.size(), storyScrollOffset + visibleLines); idx++) {
            StoryLine line = lines.get(idx);
            if (!line.spacer) {
                guiGraphics.drawString(this.font, line.text, innerX, drawY, line.color, true);
            }
            drawY += lineStep;
        }

        if (maxScroll > 0) {
            int barX = layout.storyX + layout.storyW - 6;
            int barTop = innerY;
            int barBottom = innerY + innerH;
            guiGraphics.fill(barX, barTop, barX + 2, barBottom, 0xFF3B2A20);

            float marker = (barBottom - barTop - 18) * (storyScrollOffset / (float) maxScroll);
            guiGraphics.fill(barX - 1, barTop + (int) marker, barX + 3, barTop + (int) marker + 18, 0xFFC2752A);
        }
    }

    private List<StoryLine> buildStoryLines(int textWidth, boolean tinyMode) {
        List<StoryLine> lines = new ArrayList<>();
        int body = 0xFFF4ECE2;

        addWrapped(lines, Component.translatable("intro.ashwake.story.line1"), textWidth, body);
        if (tinyMode) {
            return lines;
        }

        addWrapped(lines, Component.translatable("intro.ashwake.story.line2"), textWidth, body);
        addWrapped(lines, Component.translatable("intro.ashwake.story.line3"), textWidth, body);
        lines.add(StoryLine.spacer());
        addWrapped(lines, Component.translatable("intro.ashwake.story.line4"), textWidth, body);
        addWrapped(lines, Component.translatable("intro.ashwake.story.line5"), textWidth, body);
        lines.add(StoryLine.spacer());
        addWrapped(lines, Component.translatable("intro.ashwake.story.line6"), textWidth, 0xFFE7D1B5);
        lines.add(StoryLine.spacer());
        addWrapped(lines, Component.translatable("intro.ashwake.story.final"), textWidth, 0xFFFFC58D);
        addWrapped(lines, Component.translatable("intro.ashwake.hint.guidance"), textWidth, 0xFFD2B48A);
        return lines;
    }

    private void addWrapped(List<StoryLine> target, Component text, int width, int color) {
        List<FormattedCharSequence> wrapped = this.font.split(text, Math.max(40, width));
        for (FormattedCharSequence line : wrapped) {
            target.add(StoryLine.text(line, color));
        }
    }

    private void drawSummaryCard(GuiGraphics guiGraphics, Layout layout, float alpha) {
        int plateColor = withAlpha(0x141210, Math.min(0.74F, 0.60F + (alpha * 0.12F)));
        int plateBorder = withAlpha(0x8E4F22, Math.min(0.70F, 0.44F + (alpha * 0.18F)));

        guiGraphics.fill(layout.summaryX, layout.summaryY, layout.summaryX + layout.summaryW, layout.summaryY + layout.summaryH, plateColor);
        guiGraphics.fill(layout.summaryX, layout.summaryY, layout.summaryX + layout.summaryW, layout.summaryY + 1, plateBorder);

        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        Component stateName = Component.translatable(snapshot.state().translationKey());
        String timer = formatTicks(snapshot.ticksRemaining());

        int textX = layout.summaryX + 10;
        int textY = layout.summaryY + 10;
        int textW = Math.max(40, layout.summaryW - 20);

        if (layout.summaryCollapsed) {
            String merged = Component.translatable("intro.ashwake.summary.state", stateName).getString()
                    + " | "
                    + Component.translatable("intro.ashwake.summary.next", timer).getString();
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(merged), textW);
            if (!wrapped.isEmpty()) {
                guiGraphics.drawString(this.font, wrapped.get(0), textX, textY, 0xFFE7DED2, true);
            }
            return;
        }

        guiGraphics.drawString(this.font, Component.translatable("intro.ashwake.summary.title"), textX, textY, 0xFFFFC58D, true);
        textY += this.font.lineHeight + 8;

        guiGraphics.drawString(
                this.font,
                Component.translatable("intro.ashwake.summary.state", stateName),
                textX,
                textY,
                0xFFE7DED2,
                true);

        textY += this.font.lineHeight + 4;
        guiGraphics.drawString(
                this.font,
                Component.translatable("intro.ashwake.summary.next", timer),
                textX,
                textY,
                0xFFE7DED2,
                true);

        textY += this.font.lineHeight + 8;
        List<FormattedCharSequence> safeLines = this.font.split(Component.translatable("intro.ashwake.summary.safe"), textW);
        int maxSafeLines = Math.max(1, (layout.summaryY + layout.summaryH - 8 - textY) / (this.font.lineHeight + 1));
        for (int i = 0; i < Math.min(maxSafeLines, safeLines.size()); i++) {
            guiGraphics.drawString(this.font, safeLines.get(i), textX, textY + (i * (this.font.lineHeight + 1)), 0xFFCBB184, true);
        }
    }

    private void drawFooter(GuiGraphics guiGraphics, Layout layout, float alpha) {
        int separator = withAlpha(0x7B4721, alpha * 0.95F);
        guiGraphics.fill(layout.contentLeft, layout.footerY - 1, layout.contentRight, layout.footerY, separator);

        if (layout.showCheckbox) {
            drawCheckboxLine(
                    guiGraphics,
                    layout.checkboxX,
                    layout.checkboxY,
                    dontShowAgainChecked,
                    Component.translatable("intro.ashwake.toggle.dont_show"));
        }
    }

    private void drawCheckboxLine(
            GuiGraphics guiGraphics,
            int x,
            int y,
            boolean checked,
            Component label) {
        int border = 0xFFB57032;
        int fill = checked ? 0xFFC47122 : 0xFF2C2621;
        guiGraphics.fill(x, y, x + 12, y + 12, border);
        guiGraphics.fill(x + 1, y + 1, x + 11, y + 11, fill);
        if (checked) {
            guiGraphics.fill(x + 3, y + 5, x + 9, y + 7, 0xFFF9EAD2);
        }
        guiGraphics.drawString(this.font, label, x + 16, y + 2, 0xFFE5D5C2, true);
    }

    private void drawParticles(GuiGraphics guiGraphics, float alphaScale, Layout layout) {
        int panelX0 = layout.panelX;
        int panelY0 = layout.panelY;
        int panelX1 = layout.panelX + layout.panelW;
        int panelY1 = layout.panelY + layout.panelH;

        for (OverlayParticle particle : particles) {
            float life = 1.0F - (particle.age / (float) particle.maxAge);
            float alpha = Mth.clamp(life * particle.baseAlpha * alphaScale, 0.0F, 1.0F);
            if (alpha <= 0.0F) {
                continue;
            }

            int px = (int) particle.x;
            int py = (int) particle.y;
            if (px >= panelX0 && px <= panelX1 && py >= panelY0 && py <= panelY1) {
                continue;
            }

            int color = withAlpha(particle.rgb, alpha);
            int size = Math.max(1, (int) particle.size);
            guiGraphics.fill(px, py, px + size, py + size, color);
        }
    }

    private void updateParticles() {
        Iterator<OverlayParticle> it = particles.iterator();
        while (it.hasNext()) {
            OverlayParticle particle = it.next();
            particle.age++;
            particle.x += particle.vx;
            particle.y += particle.vy;
            if (particle.age >= particle.maxAge) {
                it.remove();
            }
        }
    }

    private void spawnAshMote() {
        if (particles.size() >= MAX_PARTICLES) {
            return;
        }

        Layout layout = computeLayout();
        int x0 = layout.panelX + 6;
        int y0 = layout.panelY + 6;
        int x1 = layout.panelX + layout.panelW - 6;
        int y1 = layout.panelY + layout.panelH - 6;

        float px = this.rng.nextFloat() * this.width;
        float py = this.rng.nextFloat() * this.height;
        for (int i = 0; i < 12; i++) {
            float testX = this.rng.nextFloat() * this.width;
            float testY = this.rng.nextFloat() * this.height;
            if (!(testX >= x0 && testX <= x1 && testY >= y0 && testY <= y1)) {
                px = testX;
                py = testY;
                break;
            }
        }

        particles.add(new OverlayParticle(
                px,
                py,
                (this.rng.nextFloat() - 0.5F) * 0.12F,
                -(0.16F + this.rng.nextFloat() * 0.10F),
                70 + this.rng.nextInt(45),
                1.0F + this.rng.nextFloat() * 1.3F,
                0.38F,
                0xFF9D876B));
    }

    private void spawnLogoEmber() {
        if (particles.size() >= MAX_PARTICLES) {
            return;
        }

        Layout layout = computeLayout();
        int logoW = Math.min(layout.contentW, (int) (layout.logoH / 0.38F));
        int logoX = layout.panelX + (layout.panelW - logoW) / 2;
        int logoY = layout.logoY;

        float edge = this.rng.nextFloat();
        float px = edge < 0.5F
                ? logoX + (this.rng.nextFloat() * logoW)
                : (edge < 0.75F ? logoX + 2 : logoX + logoW - 2);
        float py = edge < 0.5F
                ? (edge < 0.25F ? logoY + 2 : logoY + layout.logoH - 2)
                : logoY + (this.rng.nextFloat() * layout.logoH);

        particles.add(new OverlayParticle(
                px,
                py,
                (this.rng.nextFloat() - 0.5F) * 0.16F,
                -(0.20F + this.rng.nextFloat() * 0.12F),
                22 + this.rng.nextInt(14),
                1.4F + this.rng.nextFloat() * 1.4F,
                0.70F,
                0xFFF08A2E));
    }

    private void updateButtons(Layout layout) {
        boolean visible = !closing && ageTicks >= BUTTONS_APPEAR_TICKS;

        if (beginButton != null) {
            beginButton.setX(layout.beginX);
            beginButton.setY(layout.beginY);
            beginButton.setWidth(112);
            beginButton.setHeight(20);
            beginButton.visible = visible;
            beginButton.active = visible;
        }

        if (learnMoreButton != null) {
            learnMoreButton.setX(layout.learnX);
            learnMoreButton.setY(layout.learnY);
            learnMoreButton.setWidth(112);
            learnMoreButton.setHeight(20);

            boolean allow = visible && layout.showLearn;
            learnMoreButton.visible = allow;
            learnMoreButton.active = allow;
        }
    }

    private void beginClose() {
        if (finalizedClose) {
            return;
        }

        if (!sentCloseAck) {
            sentCloseAck = true;
            boolean dontShow = !payload.allowDontShowAgain() || dontShowAgainChecked;
            boolean disableTutorialPopups = payload.allowDisableTutorialPopups() && disableTutorialPopupsChecked;
            WeatherCoreNetwork.sendIntroGuiClosedFromClient(payload.introVersion(), dontShow, disableTutorialPopups);
        }

        if (!closing) {
            closing = true;
            closeTicks = 0;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.BEACON_AMBIENT, 0.05F, 0.72F);
            }
        }
    }

    private float currentAlpha(float partialTick) {
        if (!closing) {
            return Mth.clamp((ageTicks + partialTick) / FADE_IN_TICKS, 0.0F, 1.0F);
        }
        float close = Mth.clamp((closeTicks + partialTick) / CLOSE_FADE_TICKS, 0.0F, 1.0F);
        return 1.0F - close;
    }

    private Layout computeLayout() {
        boolean smallWindow = this.width < 640 || this.height < 420;

        int panelW;
        int panelH;
        if (smallWindow) {
            panelW = Math.max(280, this.width - 24);
            panelH = Math.max(240, this.height - 24);
        } else {
            panelW = Mth.clamp(this.width - 80, 520, 980);
            panelH = Mth.clamp(this.height - 60, 360, 560);
        }

        panelW = Math.min(panelW, Math.max(220, this.width - 12));
        panelH = Math.min(panelH, Math.max(220, this.height - 12));

        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        boolean compact = smallWindow || panelW < 720 || panelH < 440;
        boolean tiny = panelH < 330;

        Layout layout = buildLayout(panelX, panelY, panelW, panelH, compact, tiny);
        if (layout.bodyH < 120 && !compact) {
            layout = buildLayout(panelX, panelY, panelW, panelH, true, tiny);
        }
        if (layout.bodyH < 90 && !tiny) {
            layout = buildLayout(panelX, panelY, panelW, panelH, true, true);
        }

        return layout;
    }

    private Layout buildLayout(int panelX, int panelY, int panelW, int panelH, boolean compact, boolean tiny) {
        Layout layout = new Layout();

        layout.panelX = panelX;
        layout.panelY = panelY;
        layout.panelW = panelW;
        layout.panelH = panelH;
        layout.compact = compact;
        layout.tiny = tiny;
        layout.footerH = compact ? FOOTER_H_COMPACT : FOOTER_H_LARGE;

        layout.contentLeft = panelX + OUTER_PAD;
        layout.contentRight = panelX + panelW - OUTER_PAD;
        layout.contentW = Math.max(120, layout.contentRight - layout.contentLeft);

        int logoMin = tiny ? 44 : 58;
        int logoMax = tiny ? 72 : (compact ? 90 : 140);
        layout.logoH = Mth.clamp((int) (panelH * 0.26F), logoMin, logoMax);
        layout.logoY = panelY + OUTER_PAD;
        layout.titleY = layout.logoY + layout.logoH + 6;

        layout.bodyTop = layout.titleY + 22;
        layout.footerY = panelY + panelH - OUTER_PAD - layout.footerH;
        int bodyBottom = layout.footerY - 6;
        layout.bodyH = Math.max(46, bodyBottom - layout.bodyTop);

        if (!compact) {
            layout.storyX = layout.contentLeft;
            layout.storyY = layout.bodyTop;
            layout.storyW = Math.max(120, (int) Math.floor((layout.contentW - INNER_GAP) * 0.60F));
            layout.storyW = Math.min(layout.storyW, layout.contentW - INNER_GAP - 90);
            layout.storyH = layout.bodyH;

            layout.summaryX = layout.storyX + layout.storyW + INNER_GAP;
            layout.summaryY = layout.bodyTop;
            layout.summaryW = Math.max(84, layout.contentRight - layout.summaryX);
            layout.summaryCollapsed = layout.bodyH < 120;
            layout.summaryH = layout.summaryCollapsed ? Math.min(layout.bodyH, 54) : Math.min(layout.bodyH, 140);
        } else {
            layout.storyX = layout.contentLeft;
            layout.storyY = layout.bodyTop;
            layout.storyW = layout.contentW;

            int desiredSummary;
            if (tiny) {
                desiredSummary = Math.max(28, layout.bodyH / 4);
            } else {
                desiredSummary = layout.bodyH < 180 ? 58 : Mth.clamp(layout.bodyH / 3, 72, 126);
            }

            layout.storyH = layout.bodyH - desiredSummary - INNER_GAP;
            if (layout.storyH < (tiny ? 28 : 72)) {
                layout.storyH = tiny ? 28 : Math.max(48, layout.bodyH - 52 - INNER_GAP);
            }

            layout.summaryX = layout.contentLeft;
            layout.summaryY = layout.storyY + layout.storyH + INNER_GAP;
            layout.summaryW = layout.contentW;
            layout.summaryH = Math.max(24, (layout.bodyTop + layout.bodyH) - layout.summaryY);
            layout.summaryCollapsed = tiny || layout.summaryH < 92 || layout.bodyH < 170;
            if (layout.summaryCollapsed) {
                layout.summaryH = Math.min(layout.summaryH, tiny ? 36 : 58);
            }
        }

        int buttonW = 112;
        int buttonH = 20;
        int right = layout.contentRight;

        layout.showLearn = payload.allowLearnMore() && !tiny;
        layout.showCheckbox = payload.allowDontShowAgain() && !tiny;

        if (!compact) {
            int rowY = layout.footerY + ((layout.footerH - buttonH) / 2);
            layout.learnX = right - buttonW;
            layout.learnY = rowY;
            layout.beginX = layout.showLearn ? layout.learnX - buttonW - 8 : right - buttonW;
            layout.beginY = rowY;
            layout.checkboxX = layout.contentLeft;
            layout.checkboxY = layout.footerY + ((layout.footerH - 12) / 2);
        } else {
            int stackY = layout.footerY + 6;
            layout.beginX = right - buttonW;
            layout.beginY = stackY;
            layout.learnX = right - buttonW;
            layout.learnY = stackY + buttonH + 6;
            layout.checkboxX = layout.contentLeft;
            layout.checkboxY = layout.footerY + 8;
        }

        return layout;
    }

    private int maxStoryScroll(Layout layout) {
        int innerW = Math.max(40, layout.storyW - 16);
        int innerH = Math.max(24, layout.storyH - 16);
        List<StoryLine> lines = buildStoryLines(innerW, layout.tiny);
        int lineStep = this.font.lineHeight + STORY_LINE_SPACING;
        int visibleLines = Math.max(1, innerH / lineStep);
        return Math.max(0, lines.size() - visibleLines);
    }

    private void drawDebugSharpText(GuiGraphics guiGraphics) {
        if (!SHOW_DEBUG_SHARP_TEXT) {
            return;
        }
        int x = 10;
        int y = 10;
        int plateRight = x + this.font.width(DEBUG_SHARP_TEXT) + 4;
        guiGraphics.fill(x - 2, y - 2, plateRight, y + this.font.lineHeight + 2, 0xB0000000);
        guiGraphics.drawString(this.font, DEBUG_SHARP_TEXT, x, y, 0xFFFFFFFF, false);
    }

    private static boolean insideBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%02d:%02d", minutes, rem);
    }

    private static final class Layout {
        private int panelX;
        private int panelY;
        private int panelW;
        private int panelH;
        private int contentLeft;
        private int contentRight;
        private int contentW;
        private int footerY;
        private int footerH;
        private int logoY;
        private int logoH;
        private int titleY;
        private int bodyTop;
        private int bodyH;

        private int storyX;
        private int storyY;
        private int storyW;
        private int storyH;

        private int summaryX;
        private int summaryY;
        private int summaryW;
        private int summaryH;
        private boolean summaryCollapsed;

        private int checkboxX;
        private int checkboxY;
        private int beginX;
        private int beginY;
        private int learnX;
        private int learnY;

        private boolean compact;
        private boolean tiny;
        private boolean showLearn;
        private boolean showCheckbox;
    }

    private static final class StoryLine {
        private final FormattedCharSequence text;
        private final int color;
        private final boolean spacer;

        private StoryLine(FormattedCharSequence text, int color, boolean spacer) {
            this.text = text;
            this.color = color;
            this.spacer = spacer;
        }

        private static StoryLine text(FormattedCharSequence text, int color) {
            return new StoryLine(text, color, false);
        }

        private static StoryLine spacer() {
            return new StoryLine(null, 0, true);
        }
    }

    private static final class OverlayParticle {
        private float x;
        private float y;
        private final float vx;
        private final float vy;
        private int age;
        private final int maxAge;
        private final float size;
        private final float baseAlpha;
        private final int rgb;

        private OverlayParticle(float x, float y, float vx, float vy, int maxAge, float size, float baseAlpha, int rgb) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.maxAge = maxAge;
            this.size = size;
            this.baseAlpha = baseAlpha;
            this.rgb = rgb;
        }
    }
}
