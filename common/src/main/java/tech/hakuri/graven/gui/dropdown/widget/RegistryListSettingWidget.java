package tech.hakuri.graven.gui.dropdown.widget;

import tech.hakuri.graven.gui.dropdown.DropdownScreen;
import tech.hakuri.graven.gui.utils.RegistryListUi;
import tech.hakuri.graven.settings.impl.RegistryListSetting;

public class RegistryListSettingWidget extends AbstractSetSettingWidget<RegistryListSetting<?>> {

    public RegistryListSettingWidget(RegistryListSetting<?> setting) {
        super(setting);
    }

    @Override
    protected int elementCount() {
        return setting.size();
    }

    @Override
    protected String labelText() {
        return RegistryListUi.labelText(setting.getRegistryType());
    }

    @Override
    protected void openPopup() {
        DropdownScreen.INSTANCE.openRegistryListSettingPopup(setting);
    }

}
