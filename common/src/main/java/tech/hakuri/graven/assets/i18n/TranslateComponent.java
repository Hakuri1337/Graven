package tech.hakuri.graven.assets.i18n;

public interface TranslateComponent {

    String getFullKey();

    String getTranslatedName();

    void refresh();

    TranslateComponent createChild(String suffix);

}