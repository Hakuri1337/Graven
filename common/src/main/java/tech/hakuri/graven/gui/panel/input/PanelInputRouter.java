package tech.hakuri.graven.gui.panel.input;

import tech.hakuri.graven.gui.panel.popup.PanelPopupHost;
import tech.hakuri.graven.gui.panel.view.CategoryRailPanel;
import tech.hakuri.graven.gui.panel.view.ClientSettingPanel;
import tech.hakuri.graven.gui.panel.view.ModuleDetailPanel;
import tech.hakuri.graven.gui.panel.view.ModuleListPanel;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class PanelInputRouter {

    public boolean routeMouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, CategoryRailPanel categoryRailPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.mouseClicked(event, isDoubleClick)) {
            return true;
        }
        if (clientSettingMode) {
            if (clientSettingPanel != null && clientSettingPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
        } else {
            if (detailPanel != null && detailPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
            if (moduleListPanel != null && moduleListPanel.mouseClicked(event, isDoubleClick)) {
                return true;
            }
        }
        return categoryRailPanel != null && categoryRailPanel.mouseClicked(event, isDoubleClick);
    }

    public boolean routeKeyPressed(KeyEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.keyPressed(event)) {
            return true;
        }
        if (clientSettingMode) {
            return clientSettingPanel != null && clientSettingPanel.keyPressed(event);
        }
        if (moduleListPanel != null && moduleListPanel.keyPressed(event)) {
            return true;
        }
        return detailPanel != null && detailPanel.keyPressed(event);
    }

    public boolean routeMouseReleased(MouseButtonEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.mouseReleased(event);
        }
        if (clientSettingMode) {
            return clientSettingPanel != null && clientSettingPanel.mouseReleased(event);
        }
        if (detailPanel != null && detailPanel.mouseReleased(event)) {
            return true;
        }
        return moduleListPanel != null && moduleListPanel.mouseReleased(event);
    }

    public boolean routeMouseDragged(MouseButtonEvent event, double mouseX, double mouseY, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.mouseDragged(event, mouseX, mouseY);
        }
        if (clientSettingMode) {
            return clientSettingPanel != null && clientSettingPanel.mouseDragged(event, mouseX, mouseY);
        }
        if (detailPanel != null && detailPanel.mouseDragged(event, mouseX, mouseY)) {
            return true;
        }
        return moduleListPanel != null && moduleListPanel.mouseDragged(event, mouseX, mouseY);
    }

    public boolean routeCharTyped(CharacterEvent event, PanelPopupHost popupHost, ModuleDetailPanel detailPanel, ModuleListPanel moduleListPanel, ClientSettingPanel clientSettingPanel, boolean clientSettingMode) {
        if (popupHost.getActivePopup() != null) {
            return popupHost.charTyped(event);
        }
        if (clientSettingMode) {
            return clientSettingPanel != null && clientSettingPanel.charTyped(event);
        }
        if (moduleListPanel != null && moduleListPanel.charTyped(event)) {
            return true;
        }
        return detailPanel != null && detailPanel.charTyped(event);
    }

}
