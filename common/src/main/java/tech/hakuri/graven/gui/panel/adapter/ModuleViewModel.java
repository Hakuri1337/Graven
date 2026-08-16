package tech.hakuri.graven.gui.panel.adapter;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public record ModuleViewModel(Module module, String displayName, String realName, boolean enabled, Category category,
                              String searchText) {
    public static ModuleViewModel from(Module module) {
        String displayName = module.getTranslatedName();
        String description = module.getName();
        String categoryName = module.getCategory().getName();
        String searchText = (displayName + " " + description + " " + categoryName).toLowerCase();
        return new ModuleViewModel(module, displayName, description, module.isEnabled(), module.getCategory(), searchText);
    }
}
