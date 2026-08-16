package tech.hakuri.graven.modules;

import tech.hakuri.graven.assets.i18n.GravenTranslateComponent;
import tech.hakuri.graven.assets.i18n.TranslateComponent;
import com.github.slmpc.lumingraphics.text.icon.IconChars;

public enum Category {

    COMBAT(IconChars.SWORDS, "combat"),
    PLAYER(IconChars.PERSON, "player"),
    MOVEMENT(IconChars.DIRECTIONS_RUN, "movement"),
    RENDER(IconChars.BRUSH, "render");

    public final String icon;
    private final String name;
    private final TranslateComponent translateComponent;

    Category(String icon, String name) {
        this.icon = icon;
        this.name = name;
        translateComponent = GravenTranslateComponent.create("categories", name);
    }

    public String getName() {
        return translateComponent.getTranslatedName();
    }

    @Override
    public String toString() {
        return name;
    }

}
