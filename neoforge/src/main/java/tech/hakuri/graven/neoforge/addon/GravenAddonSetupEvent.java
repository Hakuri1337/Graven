package tech.hakuri.graven.neoforge.addon;

import tech.hakuri.graven.addon.GravenAddon;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge EVENT_BUS event for collecting Graven addons.
 */
public class GravenAddonSetupEvent extends Event {

    private final ArrayList<GravenAddon> addons = new ArrayList<>();

    public void registerAddon(GravenAddon addon) {
        if (addon != null) {
            addons.add(addon);
        }
    }

    public List<GravenAddon> getAddons() {
        return addons;
    }

}
