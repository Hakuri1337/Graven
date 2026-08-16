package tech.hakuri.graven.assets.i18n;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.holders.TranslateHolder;
import tech.hakuri.graven.modules.impl.ClientSetting;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class GravenLanguageManager {

    public static final GravenLanguageManager INSTANCE = new GravenLanguageManager();

    private static final Gson GSON = new Gson();
    private static final Pattern UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    private static final String DEFAULT_LANGUAGE_CODE = "en_us";
    private static final String LANGUAGE_DIRECTORY = "i18n";

    private volatile Map<String, String> translations = Map.of();
    private volatile GravenLanguage selectedLanguage = GravenLanguage.English;
    private volatile long revision;

    private GravenLanguageManager() {
    }

    public void selectLanguage(GravenLanguage language) {
        if (language == null) {
            language = GravenLanguage.English;
        }
        selectedLanguage = language;
        reload(Constants.mc.getResourceManager());
    }

    public void refreshCustomLanguage() {
        if (selectedLanguage.isCustom()) {
            reload(Constants.mc.getResourceManager());
        }
    }

    public GravenLanguage getSelectedLanguage() {
        return selectedLanguage;
    }

    public String getSelectedLanguageCode() {
        String code = resolveSelectedLanguageCode();
        return code.isBlank() ? DEFAULT_LANGUAGE_CODE : code;
    }

    public synchronized void reload(ResourceManager resourceManager) {
        Map<String, String> loadedTranslations = new HashMap<>();
        appendLanguage(resourceManager, DEFAULT_LANGUAGE_CODE, loadedTranslations);

        String selectedCode = resolveSelectedLanguageCode();
        if (!selectedCode.isBlank() && !DEFAULT_LANGUAGE_CODE.equals(selectedCode)) {
            appendLanguage(resourceManager, selectedCode, loadedTranslations);
        }

        // 首次初始化可能早于 Minecraft 的资源包重载。此时 ResourceManager 尚未暴露
        // mod 资源，但内置语言表已经位于当前类路径中，直接读取可避免组件永久缓存原始 key。
        appendClasspathLanguage(DEFAULT_LANGUAGE_CODE, loadedTranslations);
        if (!selectedCode.isBlank() && !DEFAULT_LANGUAGE_CODE.equals(selectedCode)) {
            appendClasspathLanguage(selectedCode, loadedTranslations);
        }

        translations = Map.copyOf(loadedTranslations);
        revision++;
        Constants.LOGGER.info("Loaded Graven translations: language={}, entries={}, revision={}",
                selectedCode.isBlank() ? DEFAULT_LANGUAGE_CODE : selectedCode, translations.size(), revision);
        refreshUi();
    }

    public long getRevision() {
        return revision;
    }

    public String getOrDefault(String key) {
        return translations.getOrDefault(key, key);
    }

    public boolean has(String key) {
        return translations.containsKey(key);
    }

    private void appendLanguage(ResourceManager resourceManager, String languageCode, Map<String, String> output) {
        if (resourceManager == null) {
            return;
        }

        String path = String.format(Locale.ROOT, "%s/%s.json", LANGUAGE_DIRECTORY, languageCode);
        for (String namespace : resourceManager.getNamespaces()) {
            try {
                Identifier location = Identifier.fromNamespaceAndPath(namespace, path);
                appendResources(location, resourceManager.getResourceStack(location), output);
            } catch (Exception exception) {
                Constants.LOGGER.warn("跳过 Graven 语言文件: {}:{} ({})", namespace, path, exception.toString());
            }
        }
    }

    private void appendResources(Identifier location, List<Resource> resources, Map<String, String> output) {
        for (Resource resource : resources) {
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject entries = GSON.fromJson(reader, JsonObject.class);
                if (entries == null) {
                    continue;
                }
                I18NJson.read(entries, (key, value) -> {
                    String text = GsonHelper.convertToString(value, key);
                    output.put(key, UNSUPPORTED_FORMAT_PATTERN.matcher(text).replaceAll("%$1s"));
                });
            } catch (IOException | JsonParseException exception) {
                Constants.LOGGER.warn("读取 Graven 语言文件失败: {} from pack {} ({})", location, resource.sourcePackId(), exception.toString());
            }
        }
    }

    private void appendClasspathLanguage(String languageCode, Map<String, String> output) {
        String path = "/assets/graven/" + LANGUAGE_DIRECTORY + "/" + languageCode + ".json";
        try (var stream = GravenLanguageManager.class.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject entries = GSON.fromJson(reader, JsonObject.class);
                if (entries == null) {
                    return;
                }
                I18NJson.read(entries, (key, value) -> {
                    String text = GsonHelper.convertToString(value, key);
                    output.putIfAbsent(key, UNSUPPORTED_FORMAT_PATTERN.matcher(text).replaceAll("%$1s"));
                });
            }
        } catch (IOException | JsonParseException exception) {
            Constants.LOGGER.warn("读取内置 Graven 语言文件失败: {} ({})", path, exception.toString());
        }
    }

    private void refreshUi() {
        TranslateHolder.INSTANCE.refresh();
    }

    private String resolveSelectedLanguageCode() {
        if (!selectedLanguage.isCustom()) {
            return selectedLanguage.getCode();
        }

        try {
            return ClientSetting.INSTANCE.customLanguage.getValue()
                    .trim()
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
