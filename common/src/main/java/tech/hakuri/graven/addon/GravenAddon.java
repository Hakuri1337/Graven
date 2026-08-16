package tech.hakuri.graven.addon;

import tech.hakuri.graven.assets.i18n.DefaultTranslateComponent;
import tech.hakuri.graven.holders.ModuleHolder;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.Setting;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.SettingHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for Graven addons.
 */
public abstract class GravenAddon implements SettingHost {

    public final String addonId;
    private final ArrayList<Setting<?>> settings = new ArrayList<>();
    private final ArrayList<SettingGroup> settingGroups = new ArrayList<>();
    private final ArrayList<Module> registeredModules = new ArrayList<>();

    protected GravenAddon(String addonId) {
        this.addonId = addonId;
    }

    /**
     * Called after this addon is registered.
     */
    public abstract void onSetup();

    public String getAddonId() {
        return addonId;
    }

    public String getDisplayName() {
        return addonId;
    }

    public String getDescription() {
        return "";
    }

    public String getVersion() {
        return "";
    }

    public List<String> getAuthors() {
        return List.of();
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public List<SettingGroup> getSettingGroups() {
        return Collections.unmodifiableList(settingGroups);
    }

    @Override
    public List<Setting<?>> mutableSettings() {
        return settings;
    }

    @Override
    public List<SettingGroup> mutableSettingGroups() {
        return settingGroups;
    }

    public List<Module> getRegisteredModules() {
        return Collections.unmodifiableList(registeredModules);
    }

    public void resetSettings() {
        for (Setting<?> setting : settings) {
            if (setting != null) {
                setting.reset();
            }
        }
    }

    public void initAddonI18n() {
        for (SettingGroup group : settingGroups) {
            if (group != null) {
                group.initTranslateComponent(DefaultTranslateComponent.create(addonId + ".settings." + group.getName().toLowerCase()));
            }
        }
        for (Setting<?> setting : settings) {
            if (setting != null) {
                setting.initTranslateComponent(DefaultTranslateComponent.create(addonId + ".settings." + setting.getName().toLowerCase()));
            }
        }
    }

    protected void registerModule(Module module) {
        if (module == null) {
            return;
        }
        ModuleHolder.INSTANCE.registerAddonModule(
                addonId,
                module,
                DefaultTranslateComponent.create(addonId + ".modules." + module.getName().toLowerCase())
        );
        if (!registeredModules.contains(module)) {
            registeredModules.add(module);
        }
    }

}
