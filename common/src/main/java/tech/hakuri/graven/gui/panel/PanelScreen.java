package tech.hakuri.graven.gui.panel;

import tech.hakuri.graven.gui.utils.UiCoordinateMapper;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import tech.hakuri.graven.gui.panel.input.PanelInputRouter;
import tech.hakuri.graven.gui.panel.popup.PanelPopupHost;
import tech.hakuri.graven.gui.panel.utils.IMEFocusHelper;
import tech.hakuri.graven.gui.panel.view.CategoryRailPanel;
import tech.hakuri.graven.gui.panel.view.ClientSettingPanel;
import tech.hakuri.graven.gui.panel.view.ModuleDetailPanel;
import tech.hakuri.graven.gui.panel.view.ModuleListPanel;
import tech.hakuri.graven.gui.theme.GravenUiTheme;
import tech.hakuri.graven.gui.theme.MD3Theme;
import tech.hakuri.graven.holders.TranslateHolder;
import tech.hakuri.graven.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

/**
 * 面板 UI 的主屏幕宿主。
 * <p>
 * 它负责维护全局状态，并在 Minecraft UI runtime 的统一 scene 帧中调度各子面板，
 * 并将输入事件路由到 rail、模块列表、详情面板、客户端设置面板和弹窗宿主。
 */
public class PanelScreen extends Screen {

    public static final PanelScreen INSTANCE = new PanelScreen();

    private final PanelState state = new PanelState();
    private final PanelDirtyState dirtyState = new PanelDirtyState();
    private UiTextMetrics textMetrics;
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final PanelInputRouter inputRouter = new PanelInputRouter();
    private CategoryRailPanel categoryRailPanel;
    private ModuleListPanel moduleListPanel;
    private ModuleDetailPanel moduleDetailPanel;
    private ClientSettingPanel clientSettingPanel;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private String lastSelectedCategory = "";
    private String lastSelectedModule = "";
    private String lastSearchQuery = "";
    private ClientSetting.ModuleSort lastModuleSort;
    private boolean lastSidebarExpanded;
    private boolean lastClientSettingMode;
    private long lastI18nRevision = Long.MIN_VALUE;

    private IMEPreeditOverlay preeditOverlay;

    private PanelScreen() {
        super(Component.literal("PanelGui"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 提取面板当前帧的渲染状态。
     * <p>
     * 该方法会计算布局、推动动画、让各个子面板把 UI 编译进共享批次，
     * 最后由 runtime 统一提交 scene。
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {

        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        int gravenMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        int gravenMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        if (scene == null || sceneRuntime != runtime) {
            releaseScene();
            scene = runtime.createScene(GravenUiTheme.lumin());
            sceneRuntime = runtime;
            textMetrics = runtime.textMetrics();
            categoryRailPanel = new CategoryRailPanel(state, textMetrics);
            moduleListPanel = new ModuleListPanel(state, textMetrics);
            moduleDetailPanel = new ModuleDetailPanel(state, textMetrics, popupHost);
            clientSettingPanel = new ClientSettingPanel(state, textMetrics, popupHost);
        }

        runtime.render(scene, activeScene -> extractPanelFrame(guiGraphics, activeScene,
                gravenMouseX, gravenMouseY, partialTick));

        if (preeditOverlay != null) {
            this.preeditOverlay.updateInputPosition(
                    (int) UiCoordinateMapper.toMinecraftX(IMEFocusHelper.activeCursorX),
                    (int) UiCoordinateMapper.toMinecraftY(IMEFocusHelper.activeCursorY));
            guiGraphics.setPreeditOverlay(this.preeditOverlay);
        }
        popupHost.extractOverlay(guiGraphics, gravenMouseX, gravenMouseY, partialTick);
    }

    private void extractPanelFrame(GuiGraphicsExtractor guiGraphics, UiScene scene, int mouseX, int mouseY, float partialTick) {

        String currentCategory = state.getSelectedCategory().name();
        String currentModule = state.getSelectedModule() == null ? "" : state.getSelectedModule().getName();
        String currentQuery = state.getSearchQuery();
        ClientSetting.ModuleSort currentModuleSort = ClientSetting.INSTANCE.moduleSort.getValue();
        boolean sidebarExpanded = state.isSidebarExpanded();
        boolean clientSettingMode = state.isClientSettingMode();
        long currentI18nRevision = TranslateHolder.INSTANCE.getRevision();
        if (!lastSelectedCategory.equals(currentCategory)
                || !lastSelectedModule.equals(currentModule)
                || !lastSearchQuery.equals(currentQuery)
                || lastModuleSort != currentModuleSort
                || lastSidebarExpanded != sidebarExpanded
                || lastClientSettingMode != clientSettingMode
                || lastI18nRevision != currentI18nRevision) {
            dirtyState.markAllDirty();
            lastSelectedCategory = currentCategory;
            lastSelectedModule = currentModule;
            lastSearchQuery = currentQuery;
            lastModuleSort = currentModuleSort;
            lastSidebarExpanded = sidebarExpanded;
            lastClientSettingMode = clientSettingMode;
            lastI18nRevision = currentI18nRevision;
        }

        if (categoryRailPanel.hasActiveAnimations()
                || moduleListPanel.hasActiveAnimations()
                || moduleDetailPanel.hasActiveAnimations()
                || clientSettingPanel.hasActiveAnimations()) {
            dirtyState.markAllDirty();
        }

        int uiWidth = UiCoordinateMapper.getProjectionWidthInt();
        int uiHeight = UiCoordinateMapper.getProjectionHeightInt();
        if (uiWidth != lastWidth || uiHeight != lastHeight) {
            dirtyState.markLayoutDirty();
            lastWidth = uiWidth;
            lastHeight = uiHeight;
        }

        if (dirtyState.consumeModuleListDirty()) {
            moduleListPanel.markDirty();
        }
        if (dirtyState.consumeDetailDirty()) {
            moduleDetailPanel.markDirty();
        }
        if (dirtyState.consumeClientSettingDirty()) {
            clientSettingPanel.markDirty();
        }

        float railWidth = categoryRailPanel.getAnimatedWidth();
        PanelLayout.Layout layout = PanelLayout.compute(uiWidth, uiHeight, railWidth);
        popupHost.setOverlayBounds(layout.panel());

        drawChrome(layout);
        int gravenMouseX = mouseX;
        int gravenMouseY = mouseY;
        boolean popupActive = popupHost.getActivePopup() != null;
        int panelMouseX = popupActive ? Integer.MIN_VALUE : gravenMouseX;
        int panelMouseY = popupActive ? Integer.MIN_VALUE : gravenMouseY;
        categoryRailPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, -20), layout.rail(), panelMouseX, panelMouseY, partialTick);
        if (state.isClientSettingMode()) {
            UiRect clientSettingsBounds = new UiRect(
                    layout.modules().x(), layout.modules().y(),
                    layout.detail().right() - layout.modules().x(),
                    layout.modules().height()
            );
            clientSettingPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 10), clientSettingsBounds, panelMouseX, panelMouseY, partialTick);
        } else {
            moduleListPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 0), layout.modules(), panelMouseX, panelMouseY, partialTick);
            moduleDetailPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 20), layout.detail(), panelMouseX, panelMouseY, partialTick);
        }

        renderPopup(guiGraphics, gravenMouseX, gravenMouseY, partialTick);
    }

    private void drawChrome(PanelLayout.Layout layout) {
        UiTree tree = UiTree.build(scope -> {
            scope.pushAbsolute(layout.panel(), panel -> {
                panel.shadow(0.0f, 0.0f, layout.panel().width(), layout.panel().height(),
                        MD3Theme.PANEL_RADIUS, MD3Theme.PANEL_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA));
                panel.roundRect(0.0f, 0.0f, layout.panel().width(), layout.panel().height(),
                        MD3Theme.PANEL_RADIUS, MD3Theme.SURFACE);
            });
            scope.pushAbsolute(layout.rail(), rail -> rail.roundRect(0.0f, 0.0f, layout.rail().width(), layout.rail().height(),
                    MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            if (state.isClientSettingMode()) {
                float csW = layout.detail().right() - layout.modules().x();
                float csH = layout.modules().height();
                scope.pushAbsolute(layout.modules().x(), layout.modules().y(), clientSettings ->
                        clientSettings.roundRect(0.0f, 0.0f, csW, csH, MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            } else {
                scope.pushAbsolute(layout.modules(), modules -> modules.roundRect(0.0f, 0.0f, layout.modules().width(), layout.modules().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
                scope.pushAbsolute(layout.detail(), detail -> detail.roundRect(0.0f, 0.0f, layout.detail().width(), layout.detail().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            }
        });
        scene.submit(UiLayer.CHROME, -20, tree);
    }

    private void renderPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (popupHost.getActivePopup() == null) {
            return;
        }
        popupHost.render(guiGraphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick);
    }

    /**
     * Minecraft 可以在首个渲染状态提取前派发输入事件。子面板在该提取阶段才依赖
     * runtime 的文字度量创建，因此就绪前不得将事件分发给它们。
     */
    private boolean panelsReady() {
        return categoryRailPanel != null
                && moduleListPanel != null
                && moduleDetailPanel != null
                && clientSettingPanel != null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        if (!panelsReady()) {
            return super.mouseClicked(gravenEvent, isDoubleClick);
        }
        double mouseX = gravenEvent.x();
        double mouseY = gravenEvent.y();
        if (event.button() != 0) {
            if (state.getListeningKeyBindModule() != null && moduleDetailPanel.mouseClicked(gravenEvent, isDoubleClick)) {
                dirtyState.markAllDirty();
                return true;
            }
            if (state.getListeningKeybindSetting() != null) {
                boolean handledListening = state.isClientSettingMode() ? clientSettingPanel.mouseClicked(gravenEvent, isDoubleClick) : moduleDetailPanel.mouseClicked(gravenEvent, isDoubleClick);
                if (handledListening) {
                    dirtyState.markAllDirty();
                    return true;
                }
            }
            return super.mouseClicked(gravenEvent, isDoubleClick);
        }

        if (popupHost.getActivePopup() != null) {
            return inputRouter.routeMouseClicked(gravenEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel, categoryRailPanel, clientSettingPanel, state.isClientSettingMode())
                    || super.mouseClicked(gravenEvent, isDoubleClick);
        }

        PanelLayout.Layout layout = PanelLayout.compute(
                UiCoordinateMapper.getProjectionWidthInt(),
                UiCoordinateMapper.getProjectionHeightInt(),
                categoryRailPanel.getAnimatedWidth());
        if (!layout.panel().contains(mouseX, mouseY)) {
            if (ClientSetting.INSTANCE.closeOnOutside.getValue()) minecraft.setScreen(null);
            return true;
        }
        if (!state.isClientSettingMode()) {
            moduleListPanel.handleGlobalClick(mouseX, mouseY);
        }
        boolean handled = inputRouter.routeMouseClicked(gravenEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel, categoryRailPanel, clientSettingPanel, state.isClientSettingMode());
        if (handled) {
            dirtyState.markAllDirty();
        }
        return handled || super.mouseClicked(gravenEvent, isDoubleClick);
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        textMetrics = null;
        if (previous != null) previous.close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double gravenMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        double gravenMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        if (!panelsReady()) {
            return super.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY);
        }
        if (popupHost.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY)) {
            dirtyState.markAllDirty();
            return true;
        }
        if (state.isClientSettingMode()) {
            if (clientSettingPanel.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY)) {
                dirtyState.markClientSettingDirty();
                return true;
            }
        } else {
            if (moduleListPanel.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY)) {
                dirtyState.markModuleListDirty();
                return true;
            }
            if (moduleDetailPanel.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY)) {
                dirtyState.markDetailDirty();
                return true;
            }
        }
        return super.mouseScrolled(gravenMouseX, gravenMouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        if (!panelsReady()) {
            return super.mouseReleased(gravenEvent);
        }
        if (inputRouter.routeMouseReleased(gravenEvent, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseReleased(gravenEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent gravenEvent = UiCoordinateMapper.toProjectionEvent(event);
        double gravenDeltaX = UiCoordinateMapper.toProjectionX(deltaX);
        double gravenDeltaY = UiCoordinateMapper.toProjectionY(deltaY);
        if (!panelsReady()) {
            return super.mouseDragged(gravenEvent, gravenDeltaX, gravenDeltaY);
        }
        if (inputRouter.routeMouseDragged(gravenEvent, gravenDeltaX, gravenDeltaY,
                popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseDragged(gravenEvent, gravenDeltaX, gravenDeltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!panelsReady()) {
            return super.keyPressed(event);
        }
        if (inputRouter.routeKeyPressed(event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!panelsReady()) {
            return super.charTyped(event);
        }
        if (inputRouter.routeCharTyped(event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void onClose() {
        IMEFocusHelper.forceDeactivate();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        popupHost.close();
        releaseScene();
        if (moduleListPanel != null) moduleListPanel.resetTransientState();
        if (moduleDetailPanel != null) moduleDetailPanel.resetTransientState();
        if (clientSettingPanel != null) clientSettingPanel.resetTransientState();
        state.setListeningKeyBindModule(null);
        state.setListeningKeybindSetting(null);
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
    }

}
