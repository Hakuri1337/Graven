package tech.hakuri.graven.assets.i18n;

/**
 * Static factory that creates {@link TranslateComponent} instances
 * with the "graven" prefix. Used for Graven's own i18n keys.
 */
public class GravenTranslateComponent {

    private static final String PREFIX = "graven";

    public static TranslateComponent create(String prefix, String suffix) {
        return DefaultTranslateComponent.create(PREFIX + "." + prefix + "." + suffix);
    }

}

