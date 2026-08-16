package tech.hakuri.graven.gui.dropdown;

import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.gui.dropdown.component.*;
import tech.hakuri.graven.gui.dropdown.widget.DropdownTextField;
import tech.hakuri.graven.gui.utils.UiCoordinateMapper;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.text.icon.IconChars;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import tech.hakuri.graven.gui.panel.popup.PanelPopupHost;
import tech.hakuri.graven.gui.panel.popup.RegistryListSelectPopup;
import tech.hakuri.graven.gui.panel.popup.StringListSelectPopup;
import tech.hakuri.graven.gui.panel.utils.IMEFocusHelper;
import tech.hakuri.graven.gui.theme.GravenUiTheme;
import tech.hakuri.graven.gui.theme.MD3Theme;
import tech.hakuri.graven.gui.theme.OpalIslandStyle;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.impl.ClientSetting;
import tech.hakuri.graven.settings.impl.RegistryListSetting;
import tech.hakuri.graven.settings.impl.StringListSetting;
import tech.hakuri.graven.utils.render.animation.Animation;
import tech.hakuri.graven.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();

    private final List<DropdownPanel> panels = new ArrayList<>();
    private UiTextMetrics uiTextMetrics;
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final Animation scrimAnim = new Animation(Easing.EASE_OUT_SINE, 200L);
    private final Animation islandSearchAnimation = new Animation(Easing.DYNAMIC_ISLAND, OpalIslandStyle.ANIMATION_DURATION);
    private final Animation searchWidthAnimation = new Animation(Easing.DYNAMIC_ISLAND, OpalIslandStyle.ANIMATION_DURATION);
    private final DropdownTextField searchField = new DropdownTextField(64);
    private final Set<String> visiblePanelIds = new HashSet<>();

    private IMEPreeditOverlay preeditOverlay;
    private boolean initialized;
    private boolean initializedOpalStyle;
    private int sessionId;
    private int renderFrameId;
    private UiRenderBatch dropdownBatch;
    private UiTree.Scope dropdownScope;
    private int dropdownLayer;
    private float animatedSearchWidth = OpalIslandStyle.DEFAULT_WIDTH;

    private DropdownScreen() {
        super(Component.literal("DropdownGui"));
    }

    @Override
    protected void init() {
        super.init();
        MD3Theme.syncFromSettings();
        DropdownTheme.syncFromSettings();
        sessionId++;
        scrimAnim.setStartValue(0.0f);
        scrimAnim.run(0.0f);
        scrimAnim.run(1.0f);
        resetSearchAnimation();

        if (!initialized || initializedOpalStyle != DropdownTheme.isOpal()) {
            buildPanels();
            initialized = true;
            initializedOpalStyle = DropdownTheme.isOpal();
        }

        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel));
            panel.startIntro();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        syncVisualStyle();
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        int gravenMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        int gravenMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        prepareScene(runtime);
        if (DropdownTheme.isOpal()) {
            runtime.useDefaultFont(DropdownTheme.bodyFontId());
        }
        try {
            runtime.render(scene, activeScene -> drawGui(graphics, activeScene,
                    gravenMouseX, gravenMouseY, partialTick));
        } finally {
            if (DropdownTheme.isOpal()) {
                ClientSetting.INSTANCE.restoreMinecraftDefaultFont(runtime);
            }
        }
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition(
                    (int) UiCoordinateMapper.toMinecraftX(IMEFocusHelper.activeCursorX),
                    (int) UiCoordinateMapper.toMinecraftY(IMEFocusHelper.activeCursorY));
            graphics.setPreeditOverlay(preeditOverlay);
        }
        popupHost.extractOverlay(graphics, gravenMouseX, gravenMouseY, partialTick);
    }

    private void prepareScene(MinecraftUiRuntime2612 runtime) {
        if (scene != null && sceneRuntime == runtime) return;
        releaseScene();
        scene = runtime.createScene(GravenUiTheme.lumin());
        sceneRuntime = runtime;
        uiTextMetrics = runtime.textMetrics();
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        uiTextMetrics = null;
        dropdownBatch = null;
        dropdownScope = null;
        if (previous != null) previous.close();
    }

    private void drawGui(GuiGraphicsExtractor graphics, UiScene activeScene, int mouseX, int mouseY, float partialTick) {
        float uiWidth = UiCoordinateMapper.getProjectionWidth();
        float uiHeight = UiCoordinateMapper.getProjectionHeight();
        scrimAnim.run(1.0f);
        dropdownBatch = activeScene.batch(UiLayer.CONTENT);
        dropdownLayer = -10;
        popupHost.setOverlayBounds(new UiRect(0.0f, 0.0f, uiWidth, uiHeight));
        updatePanelHeightLimits();
        updateVisiblePanelIds();
        beginPanelFrames();

        beginDropdownLayer();
        Color scrim = DropdownTheme.scrim();
        float scrimAlpha = scrimAnim.getValue();
        dropdownScope.rect(0, 0, uiWidth, uiHeight, new Color(scrim.getRed(), scrim.getGreen(), scrim.getBlue(), (int) (scrim.getAlpha() * scrimAlpha)));
        flushDropdownLayer();

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;
        boolean popupHovered = popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
        int backgroundMouseX = popupHovered ? Integer.MIN_VALUE : mouseX;
        int backgroundMouseY = popupHovered ? Integer.MIN_VALUE : mouseY;

        DropdownPanel topmostHovered = null;
        if (!popupHovered) {
            for (int i = panels.size() - 1; i >= 0; i--) {
                DropdownPanel p = panels.get(i);
                if (!p.isVisible()) continue;
                float ph = p.getPanelHeight();
                if (mouseX >= p.getX() && mouseX <= p.getX() + p.getWidth()
                        && mouseY >= p.getY() && mouseY <= p.getY() + ph) {
                    topmostHovered = p;
                    break;
                }
            }
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            float intro = panel.getIntroValue();
            if (intro < 0.001f) continue;

            float slideOffset = (1.0f - intro) * 10.0f;
            float origY = panel.getY();
            panel.setPosition(panel.getX(), origY - slideOffset);

            float panelH = panel.getPanelHeight();
            float revealedH = panelH * intro;

            if (DropdownTheme.isOpal() && revealedH > 0.5f) {
                MinecraftUiRuntime2612.current().applyBlur(MinecraftBlurRegion2612.rounded(
                        new UiRect(panel.getX(), panel.getY(), panel.getWidth(), revealedH),
                        DropdownTheme.PANEL_RADIUS,
                        DropdownTheme.blurStrength()
                ));
            }

            beginDropdownLayer();
            withDropdownScissor(intro < 1.0f,
                    panel.getX() - shadowPad,
                    panel.getY() - shadowPad,
                    panel.getWidth() + shadowPad * 2,
                    revealedH + shadowPad * 2,
                    scope -> panel.drawBackground(scope, uiTextMetrics));
            flushDropdownLayer();

            float clipY = panel.getContentClipY();
            float clipH = panel.getContentClipHeight();
            float revealedBottom = panel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                beginDropdownLayer();
                int hoverMouseX = panel == topmostHovered ? backgroundMouseX : -1;
                int hoverMouseY = panel == topmostHovered ? backgroundMouseY : -1;
                boolean requiresContentScissor = intro < 1.0f || panel.requiresContentScissor();
                withDropdownScissor(requiresContentScissor, panel.getX(), clipY, panel.getWidth(), actualClipH,
                        scope -> panel.drawContent(scope, uiTextMetrics, hoverMouseX, hoverMouseY));
                flushDropdownLayer();
            }

            panel.setPosition(panel.getX(), origY);
        }

        drawSearch(backgroundMouseX, backgroundMouseY);
        popupHost.render(graphics, activeScene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick);
    }

    private void drawSearch(int mouseX, int mouseY) {
        beginDropdownLayer();
        if (DropdownTheme.isOpal()) {
            islandSearchAnimation.run(1.0f);
            searchWidthAnimation.run(resolveSearchTargetWidth());
            float available = Math.max(1.0f,
                    UiCoordinateMapper.getProjectionWidth() - OpalIslandStyle.SEARCH_MARGIN * 2.0f);
            animatedSearchWidth = Mth.clamp(searchWidthAnimation.getValue(),
                    minimumSearchWidth(), available);
        }
        float searchX = getSearchX();
        float searchY = getSearchY();
        if (DropdownTheme.isOpal()) {
            OpalIslandStyle.applyBlur(searchX, searchY, getSearchWidth(), getSearchHeight());
            OpalIslandStyle.drawSurface(dropdownScope, searchX, searchY, getSearchWidth(), getSearchHeight());
            searchField.drawIslandContent(dropdownScope, uiTextMetrics, searchX, searchY,
                    getSearchWidth(), getSearchHeight(), GravenTranslations.Gui.SEARCH.getTranslatedName(),
                    0.58f, IconChars.SEARCH, Mth.clamp(islandSearchAnimation.getValue(), 0.0f, 1.0f));
        } else {
            searchField.draw(dropdownScope, uiTextMetrics, searchX, searchY, getSearchWidth(), getSearchHeight(),
                    mouseX, mouseY, GravenTranslations.Gui.SEARCH.getTranslatedName(), 0.58f,
                    DropdownTheme.searchRadius(getSearchHeight()));
        }
        drawHints();
        flushDropdownLayer();
    }

    private void drawHints() {
        if (!DropdownTheme.isOpal() && ClientSetting.INSTANCE.dropdownHints.getValue()) {
            float scale = 0.62f;
            float lineGap = 5.0f;
            float lineHeight = uiTextMetrics.textHeight(scale, null);
            String[] hints = {
                    GravenTranslations.Gui.DROPDOWN_HINT_SEARCH.getTranslatedName(),
                    GravenTranslations.Gui.DROPDOWN_HINT_PANELS.getTranslatedName(),
                    GravenTranslations.Gui.DROPDOWN_HINT_DRAG.getTranslatedName()
            };
            float screenWidth = UiCoordinateMapper.getProjectionWidth();
            float xRight = screenWidth - DropdownTheme.PANEL_MARGIN_X;
            xRight = Math.max(getSearchX() + getSearchWidth(), xRight);
            float y = UiCoordinateMapper.getProjectionHeight() - DropdownTheme.PANEL_MARGIN_Y
                    - hints.length * lineHeight - (hints.length - 1) * lineGap;
            int alpha = (int) (255 * scrimAnim.getValue());
            if (alpha <= 0) {
                return;
            }
            Color color = MD3Theme.withAlpha(Color.WHITE, alpha);
            for (String hint : hints) {
                float x = xRight - uiTextMetrics.textWidth(hint, scale, null);
                dropdownScope.text(hint, x, y, scale, color);
                y += lineHeight + lineGap;
            }
        }
    }


    private void beginDropdownLayer() {
        dropdownLayer += 10;
        dropdownScope = new UiTree.Scope();
    }

    private void flushDropdownLayer() {
        // 每个可遮挡 pass 使用独立 layer；不相交的批次仍可由 scheduler 跨 layer 合并。
        dropdownBatch.render(UiTree.from(dropdownScope), dropdownLayer);
    }

    private void withDropdownScissor(boolean required, float guiX, float guiY, float guiW, float guiH,
                                     Consumer<UiTree.Scope> content) {
        dropdownScope.scissorIf(required, new UiRect(guiX, guiY, guiW, guiH), content);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        double mx = gravenEvent.x();
        double my = gravenEvent.y();
        int button = gravenEvent.button();

        if (popupHost.mouseClicked(gravenEvent, isDoubleClick)) {
            return true;
        }

        if (button == 0 && searchField.focusIfContains(mx, my, getSearchX(), getSearchY(), getSearchWidth(), getSearchHeight())) {
            return true;
        } else if (button == 0 && searchField.isFocused()) {
            searchField.blur();
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseClicked(mx, my, button)) {
                if (i < panels.size() - 1) {
                    panels.remove(i);
                    panels.add(panel);
                }
                DropdownLayoutState.save(panels);
                return true;
            }
        }
        return super.mouseClicked(gravenEvent, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        double mx = gravenEvent.x();
        double my = gravenEvent.y();
        int button = gravenEvent.button();

        if (popupHost.mouseReleased(gravenEvent)) {
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseReleased(mx, my, button)) {
                DropdownLayoutState.save(panels);
                return true;
            }
        }
        return super.mouseReleased(gravenEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        double gravenDeltaX = UiCoordinateMapper.toProjectionX(mouseX);
        double gravenDeltaY = UiCoordinateMapper.toProjectionY(mouseY);
        if (popupHost.mouseDragged(gravenEvent, gravenDeltaX, gravenDeltaY)) {
            return true;
        }
        boolean handled = false;
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseDragged(gravenEvent.x(), gravenEvent.y())) {
                handled = true;
            }
        }
        if (handled) {
            DropdownLayoutState.save(panels);
            return true;
        }
        return super.mouseDragged(gravenEvent, gravenDeltaX, gravenDeltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double gravenMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        double gravenMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        if (popupHost.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY)) {
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseScrolled(gravenMouseX, gravenMouseY, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupHost.keyPressed(event)) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            searchField.focus();
            return true;
        }
        if (searchField.isFocused()) {
            if (event.isEscape()) {
                searchField.blur();
                return true;
            }
            if (searchField.keyPressed(event)) {
                syncSearchQuery();
                return true;
            }
        }

        boolean hasActiveInput = panels.stream().filter(DropdownPanel::isVisible).anyMatch(DropdownPanel::hasActiveInput);

        if (hasActiveInput) {
            for (DropdownPanel panel : panels) {
                if (!panel.isVisible()) continue;
                if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                    return true;
                }
            }
        }

        if (event.isEscape()) {
            onClose();
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.charTyped(event)) {
            return true;
        }
        if (searchField.charTyped(event)) {
            syncSearchQuery();
            return true;
        }
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && panel.charTyped(typed)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        IMEFocusHelper.forceDeactivate();
        DropdownLayoutState.save(panels);
        super.onClose();
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        popupHost.close();
        searchField.blur();
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
        releaseScene();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildPanels() {
        panels.clear();
        int index = 0;
        MainDropdownPanel mainPanel = new MainDropdownPanel(index++, this::handleMainPanelAction, this::anySubPanelVisible, this::isPanelVisible);
        mainPanel.setPosition(DropdownTheme.PANEL_MARGIN_X, DropdownTheme.PANEL_MARGIN_Y);
        panels.add(mainPanel);

        float x = DropdownTheme.PANEL_MARGIN_X + mainPanel.getWidth() + DropdownTheme.PANEL_GAP;
        float y = DropdownTheme.PANEL_MARGIN_Y;
        for (Category category : Category.values()) {
            panels.add(createSubPanel(new CategoryPanel(category, index++), x, y));
            y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        }

        panels.add(createSubPanel(new FriendDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new ConfigDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new AddonDropdownPanel(index), x, y));

        DropdownLayoutState.load(panels);
    }

    private DropdownPanel createSubPanel(DropdownPanel panel, float x, float y) {
        panel.setPosition(x, y);
        panel.setVisible(false);
        panel.setOpened(false);
        return panel;
    }

    private void handleMainPanelAction(String panelId) {
        if ("__collapse_all__".equals(panelId)) {
            for (DropdownPanel panel : panels) {
                if (!"main".equals(panel.getId())) {
                    panel.setVisible(false);
                    panel.setOpened(false);
                }
            }
            DropdownLayoutState.save(panels);
            return;
        }

        for (DropdownPanel panel : panels) {
            if (panel.getId().equals(panelId)) {
                panel.setVisible(!panel.isVisible());
                panel.setOpened(false);
                DropdownLayoutState.save(panels);
                return;
            }
        }
    }

    private boolean anySubPanelVisible() {
        return panels.stream().anyMatch(panel -> !"main".equals(panel.getId()) && panel.isVisible());
    }

    private boolean isPanelVisible(String panelId) {
        return visiblePanelIds.contains(panelId);
    }

    private void updateVisiblePanelIds() {
        visiblePanelIds.clear();
        for (DropdownPanel panel : panels) {
            if (panel.isVisible()) {
                visiblePanelIds.add(panel.getId());
            }
        }
    }

    private float resolveMaxPanelHeight(DropdownPanel panel) {
        return resolveMaxPanelHeight(panel, UiCoordinateMapper.getProjectionHeight() * 0.72f);
    }

    private float resolveMaxPanelHeight(DropdownPanel panel, float screenLimited) {
        return switch (panel.getId()) {
            case "main", "addon" -> Math.min(screenLimited, 260.0f);
            case "friend", "config" -> Math.min(screenLimited, 220.0f);
            default -> Math.min(screenLimited, 350.0f);
        };
    }

    private void updatePanelHeightLimits() {
        float screenLimited = UiCoordinateMapper.getProjectionHeight() * 0.72f;
        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel, screenLimited));
        }
    }

    private void beginPanelFrames() {
        int frameId = ++renderFrameId;
        for (DropdownPanel panel : panels) {
            panel.beginRenderFrame(frameId);
        }
    }

    private void syncSearchQuery() {
        String query = searchField.getText();
        for (DropdownPanel panel : panels) {
            if (panel instanceof CategoryPanel categoryPanel) {
                categoryPanel.setSearchQuery(query);
            }
        }
    }

    private float getSearchX() {
        if (DropdownTheme.isOpal()) {
            return (UiCoordinateMapper.getProjectionWidth() - getSearchWidth()) * 0.5f;
        }
        return DropdownTheme.PANEL_MARGIN_X;
    }

    private float getSearchY() {
        if (DropdownTheme.isOpal()) {
            return OpalIslandStyle.TOP;
        }
        return UiCoordinateMapper.getProjectionHeight() - DropdownTheme.PANEL_MARGIN_Y - getSearchHeight();
    }

    private float getSearchWidth() {
        if (DropdownTheme.isOpal()) {
            return animatedSearchWidth;
        }
        return Mth.clamp(UiCoordinateMapper.getProjectionWidth() - DropdownTheme.PANEL_MARGIN_X * 2.0f, 140.0f, 200.0f);
    }

    private float getSearchHeight() {
        return DropdownTheme.isOpal() ? OpalIslandStyle.HEIGHT : 20.0f;
    }

    private float resolveSearchTargetWidth() {
        float available = Math.max(1.0f,
                UiCoordinateMapper.getProjectionWidth() - OpalIslandStyle.SEARCH_MARGIN * 2.0f);
        if (!searchField.isFocused() && searchField.getText().isEmpty()) {
            return Math.min(OpalIslandStyle.SEARCH_WIDTH, available);
        }
        float iconWidth = uiTextMetrics.textWidth(IconChars.SEARCH,
                OpalIslandStyle.SEARCH_ICON_SIZE, OpalIslandStyle.ICON_FONT);
        float textWidth = uiTextMetrics.textWidth(searchField.getText(),
                0.58f, OpalIslandStyle.BODY_FONT);
        float contentWidth = OpalIslandStyle.SEARCH_GAP * 3.0f + iconWidth + textWidth;
        return Mth.clamp(contentWidth, minimumSearchWidth(), available);
    }

    private float minimumSearchWidth() {
        if (uiTextMetrics == null) return 48.0f;
        return OpalIslandStyle.SEARCH_GAP * 3.0f
                + uiTextMetrics.textWidth(IconChars.SEARCH,
                OpalIslandStyle.SEARCH_ICON_SIZE, OpalIslandStyle.ICON_FONT);
    }

    private void resetSearchAnimation() {
        islandSearchAnimation.setStartValue(0.0f);
        islandSearchAnimation.setFinished(false);
        searchWidthAnimation.setStartValue(OpalIslandStyle.DEFAULT_WIDTH);
        searchWidthAnimation.setFinished(false);
        animatedSearchWidth = DropdownTheme.isOpal()
                ? Math.min(OpalIslandStyle.DEFAULT_WIDTH,
                Math.max(1.0f, UiCoordinateMapper.getProjectionWidth() - OpalIslandStyle.SEARCH_MARGIN * 2.0f))
                : 0.0f;
    }

    public int getSessionId() {
        return sessionId;
    }

    private void syncVisualStyle() {
        MD3Theme.syncFromSettings();
        DropdownTheme.syncFromSettings();
        if (!initialized || initializedOpalStyle == DropdownTheme.isOpal()) return;

        buildPanels();
        initializedOpalStyle = DropdownTheme.isOpal();
        resetSearchAnimation();
        sessionId++;
        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel));
            panel.startIntro();
        }
    }

    public void openRegistryListSettingPopup(RegistryListSetting<?> setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(360.0f, UiCoordinateMapper.getProjectionWidth() - 28.0f),
                Math.min(300.0f, UiCoordinateMapper.getProjectionHeight() - 28.0f)
        );
        popupHost.open(RegistryListSelectPopup.create(bounds, setting));
    }

    public void openStringListSettingPopup(StringListSetting setting) {
        UiRect bounds = popupHost.getCenteredBounds(
                Math.min(300.0f, UiCoordinateMapper.getProjectionWidth() - 28.0f),
                Math.min(260.0f, UiCoordinateMapper.getProjectionHeight() - 28.0f)
        );
        popupHost.open(new StringListSelectPopup(bounds, setting, setting::add, setting::remove));
    }

}
