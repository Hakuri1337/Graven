package tech.hakuri.graven.gui.dropdown.widget;

import tech.hakuri.graven.gui.dropdown.component.Component;
import tech.hakuri.graven.settings.Setting;

public abstract class SettingWidget<S extends Setting<?>> extends Component {

    protected final S setting;

    protected SettingWidget(S setting) {
        this.setting = setting;
    }

    public S getSetting() {
        return setting;
    }

    public boolean isVisible() {
        return setting.isAvailable();
    }

}
