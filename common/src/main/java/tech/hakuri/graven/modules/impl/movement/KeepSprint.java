package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;

public class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    private enum Mode {
        CubeCraft
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.CubeCraft);
    public final DoubleSetting motion = doubleSetting("Motion", 1.0, 0.0, 1.0, 0.1);

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

}
