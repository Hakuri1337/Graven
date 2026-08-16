package tech.hakuri.graven.settings.impl;

import tech.hakuri.graven.settings.Setting;

import java.util.function.Consumer;

public class StringSetting extends Setting<String> {

    public StringSetting(String name, String defaultValue, Dependency dependency) {
        this(name, defaultValue, dependency, null);
    }

    public StringSetting(String name, String defaultValue, Dependency dependency, Consumer<String> onChanged) {
        super(name, dependency, onChanged);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

}
