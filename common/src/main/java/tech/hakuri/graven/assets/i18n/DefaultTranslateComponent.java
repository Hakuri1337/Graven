package tech.hakuri.graven.assets.i18n;

import tech.hakuri.graven.holders.TranslateHolder;
import tech.hakuri.graven.modules.impl.ClientSetting;

public class DefaultTranslateComponent implements TranslateComponent {

    private final String fullKey;
    private String cachedName;
    private long cachedRevision = -1L;

    private DefaultTranslateComponent(String fullKey) {
        this.fullKey = fullKey;
    }

    public static DefaultTranslateComponent create(String fullKey) {
        DefaultTranslateComponent component = new DefaultTranslateComponent(fullKey);
        TranslateHolder.INSTANCE.registerTranslateComponent(component);
        return component;
    }

    @Override
    public String getFullKey() {
        return fullKey;
    }

    @Override
    public String getTranslatedName() {
        long currentRevision = GravenLanguageManager.INSTANCE.getRevision();
        if (cachedName == null || cachedRevision != currentRevision) {
            cachedName = resolveTranslation(fullKey);
            cachedRevision = currentRevision;
        }
        return cachedName;
    }

    @Override
    public void refresh() {
        cachedName = resolveTranslation(fullKey);
        cachedRevision = GravenLanguageManager.INSTANCE.getRevision();
    }

    private static String resolveTranslation(String key) {
        if (GravenLanguageManager.INSTANCE.has(key)) {
            return GravenLanguageManager.INSTANCE.getOrDefault(key);
        }
        return ClientSetting.INSTANCE.i18nFallback.getValue() ? formatKey(key) : key;
    }

    private static String formatKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String lastPart = key;
        int lastDotIndex = key.lastIndexOf('.');
        if (lastDotIndex != -1) {
            lastPart = key.substring(lastDotIndex + 1);
        }

        StringBuilder result = new StringBuilder();
        String[] words = lastPart.split(" ");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));

                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString();
    }

    @Override
    public TranslateComponent createChild(String suffix) {
        return DefaultTranslateComponent.create(fullKey + "." + suffix);
    }

}
