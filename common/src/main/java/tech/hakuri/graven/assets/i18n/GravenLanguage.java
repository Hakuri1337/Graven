package tech.hakuri.graven.assets.i18n;

public enum GravenLanguage {
    English("en_us", "English"),
    ChineseSimplified("zh_cn", "Chinese Simplified"),
    Custom("", "Custom");

    private final String code;
    private final String settingName;

    GravenLanguage(String code, String settingName) {
        this.code = code;
        this.settingName = settingName;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return settingName;
    }

    public boolean isCustom() {
        return this == Custom;
    }
}
