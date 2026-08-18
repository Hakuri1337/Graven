package tech.hakuri.graven.modules.impl.player;

import net.minecraft.world.item.Items;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.utils.player.InvHelper;

/** 在没有图腾保护时，生命值过低自动离开当前服务器。 */
public class AutoRunAway extends Module {

    public static final AutoRunAway INSTANCE = new AutoRunAway();

    private enum Command {
        Hub,
        Again
    }

    private final DoubleSetting health = doubleSetting("Health", 6.0, 0.5, 36.0, 0.5);
    private final EnumSetting<Command> command = enumSetting("Command", Command.Hub);
    private boolean triggered;

    private AutoRunAway() {
        super("Auto Run Away", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        triggered = false;
    }

    @Override
    protected void onDisable() {
        triggered = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.player.connection == null) return;

        // 图腾出现时取消当前触发状态，移除图腾后仍可再次触发。
        if (InvHelper.hasItem(Items.TOTEM_OF_UNDYING)) {
            triggered = false;
            return;
        }

        if (mc.player.getHealth() >= health.getValue().floatValue()) {
            triggered = false;
            return;
        }

        if (triggered) return;
        triggered = true;
        mc.player.connection.sendCommand(command.getValue() == Command.Hub ? "hub" : "again");
    }
}
