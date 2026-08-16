package tech.hakuri.graven.events.impl;

import tech.hakuri.graven.events.bus.Cancellable;
import net.minecraft.network.chat.Component;

public final class ChatReceivedEvent extends Cancellable {

    private final Component text;
    private boolean overlay;

    public ChatReceivedEvent(Component text, boolean overlay) {
        this.text = text;
        this.overlay = overlay;
    }

    public Component getText() {
        return text;
    }

    public boolean isOverlay() {
        return overlay;
    }

    public void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }
}
