package tech.hakuri.graven.gui.dropdown;

import tech.hakuri.graven.gui.theme.MD3Theme;

import java.awt.*;

public class DropdownTheme {

    public static float PANEL_WIDTH = 130.0f;
    public static float PANEL_HEADER_HEIGHT = 28.0f;
    public static float PANEL_RADIUS = 10.0f;
    public static float PANEL_GAP = 14.0f;
    public static float PANEL_MARGIN_X = 20.0f;
    public static float PANEL_MARGIN_Y = 20.0f;
    public static float PANEL_SHADOW_BLUR = 20.0f;
    public static int PANEL_SHADOW_ALPHA = 96;

    public static float GROUP_HEADER_HEIGHT = 18.0f;
    public static float GROUP_INSET = 4.0f;
    public static float GROUP_HEADER_TEXT_SCALE = 0.52f;
    public static float GROUP_COUNT_CHIP_HEIGHT = 11.0f;
    public static float GROUP_COUNT_CHIP_PADDING = 6.0f;
    public static float GROUP_COUNT_TEXT_SCALE = 0.42f;

    public static float MODULE_HEIGHT = 19.0f;
    public static float MODULE_PADDING_X = 7.0f;
    public static float MODULE_TEXT_SCALE = 0.62f;
    public static float MODULE_ADDON_GAP = 4.0f;
    public static float MODULE_ADDON_INFO_HEIGHT = 15.0f;
    public static float MODULE_ADDON_INFO_TEXT_SCALE = 0.50f;

    public static float SETTING_PADDING_X = 6.0f;
    public static float SETTING_HEIGHT = 16.0f;
    public static float SETTING_TEXT_SCALE = 0.54f;
    public static float SETTING_GAP = 3.0f;
    public static float SETTING_INDENT = 5.0f;

    public static float SLIDER_HEIGHT = 3.0f;
    public static float SLIDER_RADIUS = 1.5f;
    public static float SLIDER_KNOB_RADIUS = 3.5f;

    public static float COLOR_PREVIEW_SIZE = 12.0f;
    public static float COLOR_PICKER_HEIGHT = 60.0f;
    public static float COLOR_HUE_HEIGHT = 7.0f;
    public static float COLOR_ALPHA_HEIGHT = 7.0f;
    public static float COLOR_RADIUS = 5.0f;

    public static float KEYBIND_WIDTH = 34.0f;
    public static float KEYBIND_HEIGHT = 14.0f;
    public static float KEYBIND_RADIUS = 5.0f;

    public static float INPUT_HEIGHT = 16.0f;
    public static float INPUT_RADIUS = 5.0f;

    public static float BUTTON_HEIGHT = 16.0f;
    public static float BUTTON_RADIUS = 5.0f;

    public static float SCROLL_SPEED = 28.0f;
    public static float PANEL_BOTTOM_PADDING = 8.0f;

    public static long ANIM_OPEN = 200L;
    public static long ANIM_TOGGLE = 180L;
    public static long ANIM_HOVER = 120L;
    public static long ANIM_EXPAND = 220L;
    public static long ANIM_GROUP = 180L;

    public static float HEADER_TEXT_SCALE = 0.82f;
    public static float HEADER_ICON_SCALE = 0.86f;

    private static boolean opal;

    private DropdownTheme() {
    }

    public static void syncFromSettings() {
        opal = MD3Theme.isOpalTheme();
        if (opal) {
            applyOpalMetrics();
        } else {
            applyGravenMetrics();
        }
    }

    public static boolean isOpal() {
        return opal;
    }

    public static String bodyFontId() {
        return opal ? "graven-opal-medium" : null;
    }

    public static String headerFontId() {
        return opal ? "graven-opal-bold" : null;
    }

    public static float blurStrength() {
        return opal ? 8.0f : 0.0f;
    }

    public static float searchRadius(float height) {
        return opal ? height * 0.5f : INPUT_RADIUS;
    }

    private static void applyGravenMetrics() {
        PANEL_WIDTH = 130.0f;
        PANEL_HEADER_HEIGHT = 28.0f;
        PANEL_RADIUS = 10.0f;
        PANEL_GAP = 14.0f;
        PANEL_MARGIN_X = 20.0f;
        PANEL_MARGIN_Y = 20.0f;
        PANEL_SHADOW_BLUR = 20.0f;
        PANEL_SHADOW_ALPHA = 96;
        GROUP_HEADER_HEIGHT = 18.0f;
        GROUP_INSET = 4.0f;
        GROUP_HEADER_TEXT_SCALE = 0.52f;
        GROUP_COUNT_CHIP_HEIGHT = 11.0f;
        GROUP_COUNT_CHIP_PADDING = 6.0f;
        GROUP_COUNT_TEXT_SCALE = 0.42f;
        MODULE_HEIGHT = 19.0f;
        MODULE_PADDING_X = 7.0f;
        MODULE_TEXT_SCALE = 0.62f;
        MODULE_ADDON_GAP = 4.0f;
        MODULE_ADDON_INFO_HEIGHT = 15.0f;
        MODULE_ADDON_INFO_TEXT_SCALE = 0.50f;
        SETTING_PADDING_X = 6.0f;
        SETTING_HEIGHT = 16.0f;
        SETTING_TEXT_SCALE = 0.54f;
        SETTING_GAP = 3.0f;
        SETTING_INDENT = 5.0f;
        SLIDER_HEIGHT = 3.0f;
        SLIDER_RADIUS = 1.5f;
        SLIDER_KNOB_RADIUS = 3.5f;
        KEYBIND_WIDTH = 34.0f;
        KEYBIND_HEIGHT = 14.0f;
        KEYBIND_RADIUS = 5.0f;
        INPUT_HEIGHT = 16.0f;
        INPUT_RADIUS = 5.0f;
        BUTTON_HEIGHT = 16.0f;
        BUTTON_RADIUS = 5.0f;
        SCROLL_SPEED = 28.0f;
        PANEL_BOTTOM_PADDING = 8.0f;
        ANIM_OPEN = 200L;
        ANIM_TOGGLE = 180L;
        ANIM_HOVER = 120L;
        ANIM_EXPAND = 220L;
        ANIM_GROUP = 180L;
        HEADER_TEXT_SCALE = 0.82f;
        HEADER_ICON_SCALE = 0.86f;
    }

    private static void applyOpalMetrics() {
        PANEL_WIDTH = 122.0f;
        PANEL_HEADER_HEIGHT = 23.0f;
        PANEL_RADIUS = 5.0f;
        PANEL_GAP = 8.0f;
        PANEL_MARGIN_X = 12.0f;
        PANEL_MARGIN_Y = 46.0f;
        PANEL_SHADOW_BLUR = 12.0f;
        PANEL_SHADOW_ALPHA = 118;
        GROUP_HEADER_HEIGHT = 17.0f;
        GROUP_INSET = 3.0f;
        GROUP_HEADER_TEXT_SCALE = 0.50f;
        GROUP_COUNT_CHIP_HEIGHT = 10.0f;
        GROUP_COUNT_CHIP_PADDING = 5.0f;
        GROUP_COUNT_TEXT_SCALE = 0.40f;
        MODULE_HEIGHT = 18.0f;
        MODULE_PADDING_X = 5.0f;
        MODULE_TEXT_SCALE = 0.56f;
        MODULE_ADDON_GAP = 3.0f;
        MODULE_ADDON_INFO_HEIGHT = 13.0f;
        MODULE_ADDON_INFO_TEXT_SCALE = 0.45f;
        SETTING_PADDING_X = 5.0f;
        SETTING_HEIGHT = 15.0f;
        SETTING_TEXT_SCALE = 0.50f;
        SETTING_GAP = 2.0f;
        SETTING_INDENT = 3.0f;
        SLIDER_HEIGHT = 2.5f;
        SLIDER_RADIUS = 1.25f;
        SLIDER_KNOB_RADIUS = 2.5f;
        KEYBIND_WIDTH = 29.0f;
        KEYBIND_HEIGHT = 12.0f;
        KEYBIND_RADIUS = 3.0f;
        INPUT_HEIGHT = 14.0f;
        INPUT_RADIUS = 3.0f;
        BUTTON_HEIGHT = 14.0f;
        BUTTON_RADIUS = 3.0f;
        SCROLL_SPEED = 26.0f;
        PANEL_BOTTOM_PADDING = 5.0f;
        ANIM_OPEN = 150L;
        ANIM_TOGGLE = 150L;
        ANIM_HOVER = 120L;
        ANIM_EXPAND = 125L;
        ANIM_GROUP = 150L;
        HEADER_TEXT_SCALE = 0.68f;
        HEADER_ICON_SCALE = 0.72f;
    }

    public static Color panelBackground() {
        return opal ? new Color(15, 15, 15, 217) : MD3Theme.SURFACE_CONTAINER;
    }

    public static Color panelShadow() {
        return MD3Theme.withAlpha(MD3Theme.SHADOW, PANEL_SHADOW_ALPHA);
    }

    public static Color moduleDivider() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 24);
    }

    public static Color moduleEnabled(float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(45, 191, 254, 220), new Color(74, 202, 255, 235), hoverProgress)
                : MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.15f), hoverProgress);
    }

    public static Color moduleEnabledAlternate(float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(36, 153, 203, 220), new Color(62, 177, 224, 235), hoverProgress)
                : moduleEnabled(hoverProgress);
    }

    public static Color moduleDisabled(float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(30, 30, 45, 178), new Color(43, 43, 58, 205), hoverProgress)
                : MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hoverProgress);
    }

    public static Color moduleTextEnabled() {
        return opal ? Color.WHITE : MD3Theme.ON_PRIMARY_CONTAINER;
    }

    public static Color moduleTextDisabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_SECONDARY, MD3Theme.TEXT_PRIMARY, hoverProgress);
    }

    public static Color moduleAddonInfoText() {
        return MD3Theme.TEXT_MUTED;
    }

    public static Color settingLabel() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color settingLabelMuted() {
        return MD3Theme.TEXT_MUTED;
    }

    public static Color settingSurface() {
        return opal ? new Color(0, 0, 0, 64) : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 160);
    }

    public static Color sliderTrack() {
        return opal ? new Color(55, 55, 55, 255) : MD3Theme.SURFACE_CONTAINER_HIGHEST;
    }

    public static Color sliderActive() {
        return MD3Theme.PRIMARY;
    }

    public static Color sliderKnob() {
        return opal ? Color.WHITE : MD3Theme.PRIMARY;
    }

    public static Color chipSelected() {
        return MD3Theme.SECONDARY_CONTAINER;
    }

    public static Color chipSelectedText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color chipUnselected() {
        return MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color chipUnselectedText() {
        return MD3Theme.TEXT_SECONDARY;
    }

    public static Color keybindSurface(boolean listening) {
        return listening ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGHEST;
    }

    public static Color keybindText(boolean listening) {
        return listening ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
    }

    public static Color inputSurface(boolean focused) {
        if (opal) return focused ? new Color(30, 53, 65, 235) : new Color(25, 25, 31, 225);
        return focused ? MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.PRIMARY_CONTAINER, 0.3f) : MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color inputText() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color inputIndicator(boolean focused) {
        return focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 96);
    }

    public static Color fieldSurface(boolean focused, float hoverProgress) {
        return opal ? MD3Theme.lerp(inputSurface(focused), new Color(42, 42, 52, 235), hoverProgress * 0.45f)
                : MD3Theme.filledFieldSurface(focused, hoverProgress);
    }

    public static Color fieldOutline(boolean focused, float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(104, 104, 115, 115), MD3Theme.PRIMARY,
                focused ? 1.0f : hoverProgress * 0.65f) : MD3Theme.filledFieldIndicator(focused, hoverProgress);
    }

    public static Color fieldText() {
        return opal ? Color.WHITE : MD3Theme.TEXT_PRIMARY;
    }

    public static Color optionHover() {
        return opal ? new Color(45, 191, 254, 54) : MD3Theme.rowSurface(1.0f);
    }

    public static Color controlOutline(float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(120, 120, 130, 90), new Color(45, 191, 254, 190), hoverProgress)
                : MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 96), MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 136), hoverProgress * 0.55f);
    }

    public static Color buttonSurface(float hoverProgress) {
        return opal ? MD3Theme.lerp(new Color(30, 30, 45, 220), new Color(45, 191, 254, 220), hoverProgress)
                : MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, 0.12f), hoverProgress);
    }

    public static Color buttonText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color expandArrow(float toggleProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ON_PRIMARY_CONTAINER, toggleProgress);
    }

    public static Color scrollbar() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 64);
    }

    public static Color scrollbar(float hoverProgress) {
        return MD3Theme.lerp(scrollbar(), MD3Theme.withAlpha(MD3Theme.PRIMARY, 190), hoverProgress);
    }

    public static Color scrim() {
        return new Color(0, 0, 0, opal ? 92 : 50);
    }

    public static Color groupBackground() {
        return opal ? new Color(0, 0, 0, 54) : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 160);
    }

    public static Color groupBackgroundHover() {
        return opal ? new Color(36, 36, 45, 205) : MD3Theme.SURFACE_CONTAINER;
    }

    public static Color groupText() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color groupCountChip() {
        return MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210);
    }

    public static Color groupCountText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color groupChevron(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress);
    }

    public static Color groupDivider() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 48);
    }

}
