package tech.hakuri.graven.gui.dropdown.widget;

import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.gui.dropdown.DropdownScreen;
import tech.hakuri.graven.settings.impl.StringListSetting;

public class StringListSettingWidget extends AbstractSetSettingWidget<StringListSetting> {

    public StringListSettingWidget(StringListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() {
        return setting.size();
    }

    @Override
    protected String labelText() {
        return GravenTranslations.Gui.LIST_ENTRIES.getTranslatedName();
    }

    @Override
    protected void openPopup() {
        DropdownScreen.INSTANCE.openStringListSettingPopup(setting);
    }

}
