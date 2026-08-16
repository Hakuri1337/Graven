package tech.hakuri.graven.gui.dropdown.component;

import tech.hakuri.graven.assets.i18n.TranslateComponent;
import tech.hakuri.graven.gui.dropdown.DropdownTheme;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import tech.hakuri.graven.settings.Setting;

import java.util.List;

public class SettingsListPanel extends AbstractDropdownPanel {

    private final SettingsContent settingsContent;

    public SettingsListPanel(String id, String title, String icon, int panelIndex, List<Setting<?>> settings) {
        super(id, title, icon, panelIndex);
        this.settingsContent = new SettingsContent("dropdown-panel:" + id, settings);
    }

    public SettingsListPanel(String id, TranslateComponent titleComponent, String icon, int panelIndex, List<Setting<?>> settings) {
        super(id, titleComponent, icon, panelIndex);
        this.settingsContent = new SettingsContent("dropdown-panel:" + id, settings);
    }

    @Override
    protected float computeContentHeight() {
        return settingsContent.computeContentHeight(getRenderFrameId());
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        settingsContent.draw(scope, textMetrics, mouseX, mouseY, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width, getRenderFrameId());
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return settingsContent.mouseClicked(mouseX, mouseY, button, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width);
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return settingsContent.mouseReleased(mouseX, mouseY, button, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return settingsContent.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(String typedText) {
        return settingsContent.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return settingsContent.hasActiveInput();
    }

    public static tech.hakuri.graven.gui.dropdown.widget.SettingWidget<?> createWidget(Setting<?> setting) {
        return SettingsContent.createWidget(setting);
    }

}
