package tech.hakuri.graven.modules.impl;

import tech.hakuri.graven.assets.i18n.GravenLanguage;
import tech.hakuri.graven.assets.i18n.GravenLanguageManager;
import tech.hakuri.graven.gui.dropdown.DropdownScreen;
import tech.hakuri.graven.gui.dropdown.DropdownTheme;
import tech.hakuri.graven.gui.hudeditor.HudEditorScreen;
import tech.hakuri.graven.gui.panel.PanelScreen;
import tech.hakuri.graven.gui.screen.MainMenuScreen;
import tech.hakuri.graven.gui.theme.MD3Theme;
import tech.hakuri.graven.holders.TranslateHolder;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.rotations.RotationManager;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.scripting.lua.LuaScriptManager;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.impl.*;
import tech.hakuri.graven.utils.client.FontPathResolver;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;

public class ClientSetting extends Module {

    public static final ClientSetting INSTANCE = new ClientSetting();

    private ClientSetting() {
        super("Client Setting", null);
    }

    public enum GuiMode {
        Dropdown,
        Panel
    }

    public enum ModuleSort {
        Name,
        EnabledFirst,
        Addon
    }

    public enum ThemePreset {
        TonalSpot,
        Neutral,
        Vibrant,
        Expressive,
        Fidelity,
        Content,
        Rainbow,
        FruitSalad,
        Monochrome,
        Opal
    }

    public enum ThemeMode {
        Dark,
        Light
    }

    public enum IconMode {
        Vanilla,
        Minecraft_1_8_9,
        Graven
    }

    public enum TitleMode {
        Vanilla,
        Minecraft_1_8_9,
        Graven
    }

    public enum HideMode {
        None,
        Hide,
        Vanilla
    }

    public enum FontMode {
        Default,
        Custom
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgAntiCheat = settingGroup("Anti Cheat");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgNotification = settingGroup("Notification");
    private final SettingGroup sgLua = settingGroup("Lua Scripts");
    private MinecraftUiRuntime2612 fontRuntime;
    private FontMode appliedFontMode;
    private String appliedCustomFont;
    private String configuredDefaultFontId = "graven-default";

    @SuppressWarnings("unused")
    private final ButtonSetting openHUDEditor = buttonSetting("Open HUD Editor", () -> mc.setScreen(HudEditorScreen.INSTANCE));

    // General
    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT).group(sgGeneral);

    public final EnumSetting<GuiMode> guiMode = enumSetting("Gui Mode", GuiMode.Dropdown, _ -> mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
        case Panel -> PanelScreen.INSTANCE;
        case Dropdown -> DropdownScreen.INSTANCE;
    })).group(sgGeneral);

    public final EnumSetting<ModuleSort> moduleSort = enumSetting("Module Sort", ModuleSort.Name).group(sgGeneral);

    public final EnumSetting<GravenLanguage> language = enumSetting("Language", GravenLanguage.English, GravenLanguageManager.INSTANCE::selectLanguage).group(sgGeneral);

    public final StringSetting customLanguage = stringSetting("Custom Language", "", () -> language.is(GravenLanguage.Custom), _ -> GravenLanguageManager.INSTANCE.refreshCustomLanguage())
            .group(sgGeneral)
            .applyWhenRelease();

    private final DoubleSetting renderScale = doubleSetting("Render Scale", 2.0, 1.0, 6.0, 0.5)
            .group(sgGeneral)
            .applyWhenRelease();

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true, _ -> {
        TranslateHolder.INSTANCE.refresh();
    }).group(sgGeneral);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true).group(sgGeneral);

    public final EnumSetting<FontMode> font = enumSetting("Font", FontMode.Default).group(sgGeneral);

    public final StringSetting customFont = stringSetting("Custom Font", "", () -> font.is(FontMode.Custom))
            .group(sgGeneral)
            .applyWhenRelease();

    public final DoubleSetting fontScale = doubleSetting("Font Scale", 1.0, 0.5, 2.0, 0.05,
            this::applyUiTextScaleMultiplier).group(sgGeneral).applyWhenRelease();

    public final IntSetting fontGlyphsPerFrame = intSetting("Font Glyphs Per Frame", 8, 1, 64, 1, this::applyFontGlyphUploadBudget).group(sgGeneral);

    public final BoolSetting replaceMinecraftFont = boolSetting("Replace Minecraft Font", true).group(sgGeneral);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false, () -> guiMode.is(GuiMode.Panel)).group(sgGeneral);

    public final BoolSetting dropdownHints = boolSetting("Dropdown Hints", true, () -> guiMode.is(GuiMode.Dropdown)).group(sgGeneral);

    // Anti Cheat
    public final EnumSetting<RotationManager.RotationMode> rotationMode =
            enumSetting("Rotation Mode", RotationManager.RotationMode.SILENT, mode -> {
                if (Managers.ROTATION != null) {
                    Managers.switchRotationManager(mode);
                }
            }).group(sgAntiCheat);

    public final BoolSetting modifyCrosshair = boolSetting("Modify Crosshair", false).group(sgAntiCheat);

    public final EnumSetting<HideMode> hideMode = enumSetting("Hide Mode", HideMode.None).group(sgAntiCheat);

    // Appearance
    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark, _ -> syncTheme()).group(sgAppearance);

    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.TonalSpot, _ -> syncTheme()).group(sgAppearance);

    public final EnumSetting<IconMode> customIcon = enumSetting("Custom Icon", IconMode.Graven, _ -> {
        try {
            mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ignored) {
        }
    }).group(sgAppearance);

    public final EnumSetting<TitleMode> customTitle = enumSetting("Custom Title", TitleMode.Graven, _ -> mc.updateTitle()).group(sgAppearance);

    public final BoolSetting useMainMenu = boolSetting("Use MainMenu", true).group(sgAppearance);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting("MainMenu Background", MainMenuScreen.Background.PLANET, useMainMenu::getValue).group(sgAppearance);

    public final BoolSetting showWelcomeScreen = boolSetting("Show Welcome Screen", true).rootSetting().group(sgAppearance);

    // Lua Scripts
    public final BoolSetting luaScriptsEnabled = boolSetting("Enable Lua Scripts", false,
            LuaScriptManager.INSTANCE::setEnabled).rootSetting().group(sgLua);

    // Notification
    public final BoolSetting soundNotify = boolSetting("Sound Notify", true).group(sgNotification);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true).group(sgNotification);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true).group(sgNotification);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue).group(sgNotification);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue).group(sgNotification);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue).group(sgNotification);

    public double getScale() {
        return renderScale.getValue();
    }

    public int getFontGlyphsPerFrame() {
        return fontGlyphsPerFrame.getValue();
    }

    public double getFontScale() {
        return fontScale.getValue();
    }

    public void syncFontGlyphUploadBudget() {
        applyFontGlyphUploadBudget(fontGlyphsPerFrame.getValue());
    }

    /** 向 MC-owned UI runtime 注册 Graven 字体，并应用当前业务字体选择。 */
    public synchronized void configureMinecraftFonts(MinecraftUiRuntime2612 runtime) {
        runtime.setProjectionScale(getScale());
        runtime.setUiTextScaleMultiplier((float) getFontScale());
        runtime.setFontGlyphsPerFrame(getFontGlyphsPerFrame());
        if (fontRuntime != runtime) {
            runtime.registerFont("graven-default", Identifier.fromNamespaceAndPath("graven", "fonts/font.ttf"));
            runtime.registerFont("graven-icons", Identifier.fromNamespaceAndPath("graven", "fonts/icons.ttf"));
            runtime.registerFont("graven-jura-light", Identifier.fromNamespaceAndPath("graven", "fonts/jura-light.ttf"));
            runtime.registerFont("graven-osakachips", Identifier.fromNamespaceAndPath("graven", "fonts/osakachips.ttf"));
            runtime.registerFont("graven-opal-medium", Identifier.fromNamespaceAndPath("graven", "fonts/opal-medium.ttf"));
            runtime.registerFont("graven-opal-bold", Identifier.fromNamespaceAndPath("graven", "fonts/opal-bold.ttf"));
            fontRuntime = runtime;
            appliedFontMode = null;
            appliedCustomFont = null;
            configuredDefaultFontId = "graven-default";
        }

        FontMode nextMode = font.getValue();
        String nextCustom = customFont.getValue();
        if (nextMode == appliedFontMode && (nextMode != FontMode.Custom
                || java.util.Objects.equals(nextCustom, appliedCustomFont))) {
            runtime.useDefaultFont(configuredDefaultFontId);
            return;
        }
        appliedFontMode = nextMode;
        appliedCustomFont = nextCustom;
        if (nextMode == FontMode.Custom) {
            try {
                runtime.useCustomDefaultFont("graven-custom", FontPathResolver.resolve(nextCustom));
                // Lumin 延迟创建字体 loader；在这里强制读取和解析，确保失败仍处于回退边界内。
                runtime.font("graven-custom");
                configuredDefaultFontId = "graven-custom";
                return;
            } catch (RuntimeException failure) {
                tech.hakuri.graven.Constants.LOGGER.error(
                        "Failed to load custom Lumin font '{}'; using Graven default", nextCustom, failure);
            }
        }
        configuredDefaultFontId = "graven-default";
        runtime.useDefaultFont("graven-default");
    }

    /** 恢复用户配置的默认字体，供临时使用专用字体的界面在帧末调用。 */
    public synchronized void restoreMinecraftDefaultFont(MinecraftUiRuntime2612 runtime) {
        if (fontRuntime == runtime) {
            runtime.useDefaultFont(configuredDefaultFontId);
        }
    }

    /** 资源重载会销毁 Lumin 的字形 loader，下一帧必须重新注册字体资源。 */
    public synchronized void invalidateMinecraftFonts(MinecraftUiRuntime2612 runtime) {
        if (fontRuntime != runtime) return;
        fontRuntime = null;
        appliedFontMode = null;
        appliedCustomFont = null;
        configuredDefaultFontId = "graven-default";
    }

    private void syncTheme() {
        MD3Theme.syncFromSettings();
        DropdownTheme.syncFromSettings();
    }

    private synchronized void applyFontGlyphUploadBudget(int maxGlyphsPerFrame) {
        if (fontRuntime != null) {
            fontRuntime.setFontGlyphsPerFrame(Math.max(1, maxGlyphsPerFrame));
        }
    }

    private synchronized void applyUiTextScaleMultiplier(double multiplier) {
        if (fontRuntime != null) {
            fontRuntime.setUiTextScaleMultiplier((float) multiplier);
        }
    }

    public boolean snapRotation() {
        return rotationMode.is(RotationManager.RotationMode.SNAP);
    }

    public boolean silentRotation() {
        return rotationMode.is(RotationManager.RotationMode.SILENT);
    }

}
