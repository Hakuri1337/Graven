package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;

/** Remix 的隐藏全局目标过滤模块。 */
public final class Targets extends Module {

    public static final Targets INSTANCE = new Targets();

    public final BoolSetting player = boolSetting("Player", true);
    public final BoolSetting dead = boolSetting("Dead", false);
    public final BoolSetting villager = boolSetting("Villager", false);
    public final BoolSetting invisible = boolSetting("Invisible", false);
    public final BoolSetting mob = boolSetting("Mob", false);
    public final BoolSetting animal = boolSetting("Animal", false);

    private Targets() {
        super("Targets", Category.PLAYER);
        setDefaultHidden(true);
        setDefaultEnabledValue(true);
    }

    @Override
    protected void onDisable() {
        setEnabled(true);
    }
}
