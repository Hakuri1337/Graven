package tech.hakuri.graven.events.impl;

import tech.hakuri.graven.events.bus.Cancellable;
import net.minecraft.client.gui.screens.Screen;

public class OpenScreenEvent extends Cancellable {

    private final Screen screen;

    public OpenScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

}
