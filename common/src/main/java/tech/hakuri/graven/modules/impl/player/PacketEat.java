package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

public class PacketEat extends Module {

    public static final PacketEat INSTANCE = new PacketEat();

    private PacketEat() {
        super("Packet Eat", Category.PLAYER);
    }

    private ItemStack item;

    @EventHandler
    private void onPostTick(PlayerTickEvent.Post event) {
        if (mc.player.isUsingItem()) item = mc.player.getUseItem();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundPlayerActionPacket packet && packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            FoodProperties food = item.get(DataComponents.FOOD);
            if (food != null && food.canAlwaysEat()) {
                event.cancel();
            }
        }
    }

}
