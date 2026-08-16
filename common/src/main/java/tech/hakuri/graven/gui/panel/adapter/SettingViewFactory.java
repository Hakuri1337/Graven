package tech.hakuri.graven.gui.panel.adapter;

import tech.hakuri.graven.gui.panel.component.SettingRow;
import tech.hakuri.graven.gui.panel.component.setting.*;
import tech.hakuri.graven.settings.Setting;
import tech.hakuri.graven.settings.impl.*;

public class SettingViewFactory {

    private SettingViewFactory() {
    }

    public static SettingRow<?> create(Setting<?> setting) {
        return switch (setting) {
            case KeybindSetting keybindSetting -> new KeybindSettingRow(keybindSetting);
            case BoolSetting boolSetting -> new BoolSettingRow(boolSetting);
            case EnumSetting<?> enumSetting -> new EnumSettingRow(enumSetting);
            case ChoiceSetting choiceSetting -> new ChoiceSettingRow(choiceSetting);
            case IntSetting intSetting -> new IntSettingRow(intSetting);
            case DoubleSetting doubleSetting -> new DoubleSettingRow(doubleSetting);
            case ColorSetting colorSetting -> new ColorSettingRow(colorSetting);
            case RegistryListSetting<?> r -> new RegistryListSettingRow(r);
            case StringSetting stringSetting -> new StringSettingRow(stringSetting);
            case ButtonSetting buttonSetting -> new ButtonSettingRow(buttonSetting);
            case StringListSetting stringListSetting -> new StringListSettingRow(stringListSetting);
            case null, default -> null;
        };
    }

}
