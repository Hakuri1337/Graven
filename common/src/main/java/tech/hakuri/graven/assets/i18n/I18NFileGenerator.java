package tech.hakuri.graven.assets.i18n;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.addon.GravenAddon;
import tech.hakuri.graven.holders.AddonHolder;
import tech.hakuri.graven.holders.HudElementHolder;
import tech.hakuri.graven.holders.ModuleHolder;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.Setting;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.impl.EnumSetting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class I18NFileGenerator {

    private static final String EPSILON_OWNER = "graven";
    private static final String PREFIX = EPSILON_OWNER + ".";

    public static void generate(String filePath) {
        generate(filePath, null);
    }

    /**
     * 生成指定 owner 的空翻译模板。
     *
     * @param filePath 输出文件路径
     * @param ownerId  {@code graven}、Addon ID，或使用 {@code null} / 空字符串生成全部
     */
    public static void generate(String filePath, String ownerId) {
        String selectedOwner = normalizeOwner(ownerId);
        JsonObject root = new JsonObject();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        boolean matched = false;

        if (matchesOwner(EPSILON_OWNER, selectedOwner)) {
            addGravenKeys(root);
            matched = true;
        }

        for (GravenAddon addon : AddonHolder.INSTANCE.getAddons()) {
            if (matchesOwner(addon.getAddonId(), selectedOwner)) {
                addAddonKeys(root, addon);
                matched = true;
            }
        }

        for (Module module : ModuleHolder.INSTANCE.getModules()) {
            if (matchesOwner(module.getAddonId(), selectedOwner)) {
                addModuleKeys(root, module);
                matched = true;
            }
        }

        for (Module module : HudElementHolder.INSTANCE.getElements()) {
            if (matchesOwner(module.getAddonId(), selectedOwner)) {
                addModuleKeys(root, module);
                matched = true;
            }
        }

        if (selectedOwner != null && !matched) {
            throw new IllegalArgumentException("Unknown i18n owner: " + selectedOwner);
        }

        final var file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Constants.LOGGER.warn("Failed to create i18n file: {}", filePath, e);
            }
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(root, writer);
            Constants.LOGGER.info("I18N file generated successfully at: {}", filePath);
        } catch (IOException e) {
            Constants.LOGGER.warn("Failed to write i18n file: {}", filePath, e);
        }
    }

    private static void addGravenKeys(JsonObject root) {
        I18NJson.addTranslation(root, EPSILON_OWNER, "");
        for (Category category : Category.values()) {
            String catKey = PREFIX + "categories." + category.toString().toLowerCase();
            I18NJson.addTranslation(root, catKey, "");
        }

        for (TranslateComponent component : GravenTranslations.all()) {
            I18NJson.addTranslation(root, component.getFullKey(), "");
        }
    }

    private static void addAddonKeys(JsonObject root, GravenAddon addon) {
        I18NJson.addTranslation(root, addon.getAddonId(), "");
        for (SettingGroup group : addon.getSettingGroups()) {
            addSettingGroupKey(root, group);
        }
        for (Setting<?> setting : addon.getSettings()) {
            addSettingKey(root, setting);
        }
    }

    private static void addModuleKeys(JsonObject root, Module module) {
        if (module.translateComponent == null) return;
        String moduleKey = module.translateComponent.getFullKey();
        I18NJson.addTranslation(root, moduleKey, "");

        for (SettingGroup group : module.getSettingGroups()) {
            addSettingGroupKey(root, group);
        }

        for (Setting<?> setting : module.getSettings()) {
            addSettingKey(root, setting);
        }
    }

    private static void addSettingGroupKey(JsonObject root, SettingGroup group) {
        TranslateComponent component = group.getTranslateComponent();
        if (component != null) {
            I18NJson.addTranslation(root, component.getFullKey(), "");
        }
    }

    private static void addSettingKey(JsonObject root, Setting<?> setting) {
        TranslateComponent component = setting.getTranslateComponent();
        if (component == null) return;
        String settingKey = component.getFullKey();
        I18NJson.addTranslation(root, settingKey, "");

        if (setting instanceof EnumSetting<?> enumSetting) {
            for (final var mode : enumSetting.getModes()) {
                I18NJson.addTranslation(root, settingKey + "." + mode.toString().toLowerCase(), "");
            }
        }
    }

    private static boolean matchesOwner(String ownerId, String selectedOwner) {
        return selectedOwner == null || selectedOwner.equals(ownerId);
    }

    private static String normalizeOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }
        return ownerId.trim();
    }

}
