package com.ashwake.ashwake.client.intro.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class AshwakeIntroLearnMoreScreen extends Screen {
    private static final int OUTER_PADDING = 18;
    private static final int INNER_GAP = 12;
    private static final int FOOTER_HEIGHT = 56;
    private static final int TAB_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 44;

    private final AshwakeIntroScreen parent;
    private LearnTab selectedTab = LearnTab.GOOD;
    private int scrollOffset;

    private Button goodTabButton;
    private Button badTabButton;
    private Button rareTabButton;
    private Button backButton;
    private Button beginButton;

    private int listX0;
    private int listY0;
    private int listX1;
    private int listY1;

    public AshwakeIntroLearnMoreScreen(AshwakeIntroScreen parent) {
        super(Component.translatable("intro.ashwake.learn_more.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        goodTabButton = this.addRenderableWidget(Button.builder(Component.translatable(LearnTab.GOOD.tabKey), b -> selectTab(LearnTab.GOOD))
                .bounds(0, 0, 96, TAB_HEIGHT)
                .build());
        badTabButton = this.addRenderableWidget(Button.builder(Component.translatable(LearnTab.BAD.tabKey), b -> selectTab(LearnTab.BAD))
                .bounds(0, 0, 96, TAB_HEIGHT)
                .build());
        rareTabButton = this.addRenderableWidget(Button.builder(Component.translatable(LearnTab.RARE.tabKey), b -> selectTab(LearnTab.RARE))
                .bounds(0, 0, 96, TAB_HEIGHT)
                .build());

        backButton = this.addRenderableWidget(Button.builder(Component.translatable("intro.ashwake.learn_more.back"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                    }
                })
                .bounds(0, 0, 106, 20)
                .build());

        beginButton = this.addRenderableWidget(Button.builder(Component.translatable("intro.ashwake.button.begin"), b -> parent.beginCloseFromLearnMore())
                .bounds(0, 0, 106, 20)
                .build());

        updateWidgetBounds();
        updateTabButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (parent.allowDisableTutorialPopups()) {
            int boxX = disableCheckboxX();
            int boxY = disableCheckboxY();
            if (insideBox(mouseX, mouseY, boxX, boxY, 12, 12)) {
                parent.setDisableTutorialPopupsChecked(!parent.isDisableTutorialPopupsChecked());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideBox(mouseX, mouseY, listX0, listY0, Math.max(1, listX1 - listX0), Math.max(1, listY1 - listY0))) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int maxOffset = Math.max(0, entriesForSelectedTab().size() - visibleItems());
        if (scrollY > 0.0D) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (scrollY < 0.0D) {
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
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

        updateWidgetBounds();
        updateTabButtons();

        drawBackdrop(guiGraphics);
        drawPanel(guiGraphics);
        drawHeader(guiGraphics);
        drawList(guiGraphics);
        drawFooter(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xBE000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        for (int i = 0; i < 3; i++) {
            int alpha = 18 - (i * 4);
            int radiusX = (int) (this.width * (0.26F + (i * 0.12F)));
            int radiusY = (int) (this.height * (0.18F + (i * 0.10F)));
            guiGraphics.fill(
                    centerX - radiusX,
                    centerY - radiusY,
                    centerX + radiusX,
                    centerY + radiusY,
                    ((alpha & 0xFF) << 24) | 0x682500);
        }
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();

        guiGraphics.fill(x, y, x + w, y + h, 0xEA171514);
        guiGraphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xF224201D);

        guiGraphics.fill(x, y, x + w, y + 2, 0xFFA45E22);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, 0xFFA45E22);
        guiGraphics.fill(x, y, x + 2, y + h, 0xFFA45E22);
        guiGraphics.fill(x + w - 2, y, x + w, y + h, 0xFFA45E22);
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int textX = x + OUTER_PADDING;

        int headerY = y + OUTER_PADDING;
        guiGraphics.drawCenteredString(this.font, this.title, x + (w / 2), headerY, 0xFFEEDCC7);

        int safeY = headerY + this.font.lineHeight + 6;
        guiGraphics.drawString(
                this.font,
                Component.translatable("intro.ashwake.learn_more.safe_zone"),
                textX,
                safeY,
                0xFFD6B58C,
                true);

        int introY = safeY + this.font.lineHeight + 4;
        List<FormattedCharSequence> introLines = this.font.split(
                Component.translatable("intro.ashwake.learn_more.intro"),
                panelWidth() - (OUTER_PADDING * 2));
        for (int i = 0; i < introLines.size(); i++) {
            guiGraphics.drawString(this.font, introLines.get(i), textX, introY + (i * (this.font.lineHeight + 1)), 0xFFE6DED2, true);
        }
    }

    private void drawList(GuiGraphics guiGraphics) {
        int x = panelX() + OUTER_PADDING;
        int y = bodyTop() + TAB_HEIGHT + INNER_GAP;
        int w = panelWidth() - (OUTER_PADDING * 2);
        int h = Math.max(80, footerTop() - INNER_GAP - y);

        this.listX0 = x;
        this.listY0 = y;
        this.listX1 = x + w;
        this.listY1 = y + h;

        guiGraphics.fill(x, y, x + w, y + h, 0xA313120F);
        guiGraphics.fill(x, y, x + w, y + 1, 0xAA8A4F22);

        List<Component> entries = entriesForSelectedTab();
        int visibleItems = visibleItems();
        int first = Mth.clamp(scrollOffset, 0, Math.max(0, entries.size() - visibleItems));

        LearnTab tab = selectedTab;
        int chipColor = tab.chipColor;
        int cardBorder = tab.borderColor;

        int textWidth = Math.max(60, w - 76);
        int baseY = y + 6;

        for (int i = 0; i < visibleItems && (first + i) < entries.size(); i++) {
            int itemY = baseY + (i * ITEM_HEIGHT);
            int itemBottom = Math.min(itemY + ITEM_HEIGHT - 4, y + h - 4);
            guiGraphics.fill(x + 6, itemY, x + w - 16, itemBottom, 0xA51A1815);
            guiGraphics.fill(x + 6, itemY, x + w - 16, itemY + 1, cardBorder);

            int chipX = x + 12;
            int chipY = itemY + 7;
            guiGraphics.fill(chipX, chipY, chipX + 70, chipY + 12, chipColor);
            guiGraphics.drawCenteredString(this.font, Component.translatable(tab.tabKey), chipX + 35, chipY + 2, 0xFF17130F);

            Component entry = entries.get(first + i);
            List<FormattedCharSequence> wrapped = this.font.split(entry, textWidth);
            int lineY = itemY + 6;
            int lineX = x + 86;
            for (int line = 0; line < Math.min(2, wrapped.size()); line++) {
                guiGraphics.drawString(this.font, wrapped.get(line), lineX, lineY + (line * (this.font.lineHeight + 2)), tab.textColor, true);
            }
        }

        if (entries.size() > visibleItems) {
            int barX = x + w - 8;
            int barTop = y + 6;
            int barBottom = y + h - 6;
            guiGraphics.fill(barX, barTop, barX + 2, barBottom, 0xFF3B2A20);

            float span = Math.max(1.0F, entries.size() - visibleItems);
            float marker = (barBottom - barTop - 18) * (first / span);
            guiGraphics.fill(barX - 1, barTop + (int) marker, barX + 3, barTop + (int) marker + 18, 0xFFC2752A);
        }
    }

    private void drawFooter(GuiGraphics guiGraphics) {
        int x = panelX();
        int w = panelWidth();
        int footerY = footerTop();

        guiGraphics.fill(x + OUTER_PADDING, footerY - 1, x + w - OUTER_PADDING, footerY, 0xCC7B4721);

        if (parent.allowDisableTutorialPopups()) {
            drawCheckboxLine(
                    guiGraphics,
                    disableCheckboxX(),
                    disableCheckboxY(),
                    parent.isDisableTutorialPopupsChecked(),
                    Component.translatable("intro.ashwake.toggle.disable_tutorial"));
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

    private void updateWidgetBounds() {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();

        int tabY = bodyTop();
        int tabX = panelX + OUTER_PADDING;
        int tabW = 96;
        int tabGap = 6;

        if (goodTabButton != null) {
            goodTabButton.setX(tabX);
            goodTabButton.setY(tabY);
            goodTabButton.setWidth(tabW);
            goodTabButton.setHeight(TAB_HEIGHT);
        }
        if (badTabButton != null) {
            badTabButton.setX(tabX + tabW + tabGap);
            badTabButton.setY(tabY);
            badTabButton.setWidth(tabW);
            badTabButton.setHeight(TAB_HEIGHT);
        }
        if (rareTabButton != null) {
            rareTabButton.setX(tabX + (tabW + tabGap) * 2);
            rareTabButton.setY(tabY);
            rareTabButton.setWidth(tabW);
            rareTabButton.setHeight(TAB_HEIGHT);
        }

        int buttonW = 106;
        int buttonH = 20;
        int buttonGap = 8;
        int footerY = panelY + panelH - OUTER_PADDING - FOOTER_HEIGHT;
        int buttonY = footerY + ((FOOTER_HEIGHT - buttonH) / 2);
        int right = panelX + panelW - OUTER_PADDING;

        if (beginButton != null) {
            beginButton.setX(right - buttonW);
            beginButton.setY(buttonY);
            beginButton.setWidth(buttonW);
            beginButton.setHeight(buttonH);
        }
        if (backButton != null) {
            backButton.setX(right - (buttonW * 2) - buttonGap);
            backButton.setY(buttonY);
            backButton.setWidth(buttonW);
            backButton.setHeight(buttonH);
        }
    }

    private void updateTabButtons() {
        if (goodTabButton != null) {
            goodTabButton.active = selectedTab != LearnTab.GOOD;
        }
        if (badTabButton != null) {
            badTabButton.active = selectedTab != LearnTab.BAD;
        }
        if (rareTabButton != null) {
            rareTabButton.active = selectedTab != LearnTab.RARE;
        }
    }

    private void selectTab(LearnTab tab) {
        if (this.selectedTab == tab) {
            return;
        }
        this.selectedTab = tab;
        this.scrollOffset = 0;
        updateTabButtons();
    }

    private List<Component> entriesForSelectedTab() {
        return switch (selectedTab) {
            case GOOD -> List.of(
                    Component.translatable("intro.ashwake.learn_more.good.dawn"),
                    Component.translatable("intro.ashwake.learn_more.good.clear"),
                    Component.translatable("intro.ashwake.learn_more.good.ember"),
                    Component.translatable("intro.ashwake.learn_more.good.drizzle"),
                    Component.translatable("intro.ashwake.learn_more.good.tailwind"));
            case BAD -> List.of(
                    Component.translatable("intro.ashwake.learn_more.bad.storm"),
                    Component.translatable("intro.ashwake.learn_more.bad.nightfall"),
                    Component.translatable("intro.ashwake.learn_more.bad.gravity"),
                    Component.translatable("intro.ashwake.learn_more.bad.dread"));
            case RARE -> List.of(
                    Component.translatable("intro.ashwake.learn_more.rare.eclipse"),
                    Component.translatable("intro.ashwake.learn_more.rare.skyfracture"));
        };
    }

    private int visibleItems() {
        int listHeight = Math.max(1, listY1 - listY0 - 8);
        return Math.max(1, listHeight / ITEM_HEIGHT);
    }

    private int panelWidth() {
        int maxAllowed = Math.max(280, this.width - 20);
        int minAllowed = Math.min(420, maxAllowed);
        int target = Math.min(900, this.width - 80);
        return Mth.clamp(target, minAllowed, maxAllowed);
    }

    private int panelHeight() {
        int maxAllowed = Math.max(240, this.height - 20);
        int minAllowed = Math.min(320, maxAllowed);
        int target = Math.min(520, this.height - 60);
        return Mth.clamp(target, minAllowed, maxAllowed);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return (this.height - panelHeight()) / 2;
    }

    private int bodyTop() {
        int introWidth = panelWidth() - (OUTER_PADDING * 2);
        int introLines = this.font.split(Component.translatable("intro.ashwake.learn_more.intro"), introWidth).size();
        int headerHeight = this.font.lineHeight
                + 6
                + this.font.lineHeight
                + 4
                + (introLines * (this.font.lineHeight + 1))
                + 8;
        return panelY() + OUTER_PADDING + headerHeight;
    }

    private int footerTop() {
        return panelY() + panelHeight() - OUTER_PADDING - FOOTER_HEIGHT;
    }

    private int disableCheckboxX() {
        return panelX() + OUTER_PADDING;
    }

    private int disableCheckboxY() {
        return footerTop() + ((FOOTER_HEIGHT - 12) / 2);
    }

    private static boolean insideBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private enum LearnTab {
        GOOD("intro.ashwake.tab.good", 0xFF79D496, 0xFF8EDAA5, 0xFFE8F4EB),
        BAD("intro.ashwake.tab.bad", 0xFFD47C7C, 0xFFB55A5A, 0xFFF2E4E4),
        RARE("intro.ashwake.tab.rare", 0xFFE1CD7E, 0xFFB99C3B, 0xFFF3EEDA);

        private final String tabKey;
        private final int chipColor;
        private final int borderColor;
        private final int textColor;

        LearnTab(String tabKey, int chipColor, int borderColor, int textColor) {
            this.tabKey = tabKey;
            this.chipColor = chipColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
        }
    }
}
