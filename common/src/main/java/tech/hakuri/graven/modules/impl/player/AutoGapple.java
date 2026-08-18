package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.SlowdownEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoGapple extends Module {

    public static final AutoGapple INSTANCE = new AutoGapple();

    private final DoubleSetting health = doubleSetting("Health", 10, 1, 20, 0.5);
    public boolean eating;
    public int eatTick;

    private AutoGapple() {
        super("AutoGapple", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetEating();
    }

    @Override
    protected void onDisable() {
        resetEating();
    }

    @EventHandler
    private void onSlow(SlowdownEvent event) {
        if (nullCheck()) return;
        if (eating && mc.player.isUsingItem()) {
            event.setSlowdown(false);
            mc.player.setSprinting(true);
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.gameMode == null) return;
        if (eating) eatTick++;
        if (eatTick >= 32) resetEating();
        if (mc.player.getOffhandItem().is(Items.GOLDEN_APPLE)
                && mc.player.getHealth() <= health.getValue() && !eating) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (nullCheck() || mc.gameMode == null || !mc.player.getOffhandItem().is(Items.GOLDEN_APPLE)) return;
        if (event.getPacket() instanceof ServerboundUseItemPacket use
                && use.getHand() == InteractionHand.OFF_HAND) eating = true;
        if (event.getPacket() instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                && mc.player.getOffhandItem().is(Items.GOLDEN_APPLE)) event.cancel();
    }

    private void resetEating() {
        eatTick = 0;
        eating = false;
    }
}
