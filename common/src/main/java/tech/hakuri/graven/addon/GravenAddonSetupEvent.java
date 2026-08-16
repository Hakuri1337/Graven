package tech.hakuri.graven.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GravenAddonSetupEvent {

    private final ArrayList<GravenAddon> addons = new ArrayList<>();

    public void registerAddon(GravenAddon addon) {
        if (addon != null) {
            addons.add(addon);
        }
    }

    public List<GravenAddon> getAddons() {
        return Collections.unmodifiableList(addons);
    }

}
