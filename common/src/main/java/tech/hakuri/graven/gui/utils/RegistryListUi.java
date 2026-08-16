package tech.hakuri.graven.gui.utils;

import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.settings.impl.RegistryListSetting;

public final class RegistryListUi {

    private RegistryListUi() {
    }

    public static String labelText(RegistryListSetting.Type type) {
        return switch (type) {
            case BLOCK -> GravenTranslations.Gui.LIST_BLOCKS.getTranslatedName();
            case ITEM -> GravenTranslations.Gui.LIST_ITEMS.getTranslatedName();
            case ENTITY_TYPE -> GravenTranslations.Gui.LIST_ENTITIES.getTranslatedName();
            case SOUND_EVENT -> GravenTranslations.Gui.LIST_SOUNDS.getTranslatedName();
            case ENCHANTMENT -> GravenTranslations.Gui.LIST_ENCHANTMENTS.getTranslatedName();
        };
    }
}
