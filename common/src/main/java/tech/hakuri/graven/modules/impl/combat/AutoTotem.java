package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.player.ClickSlotUtils;
import tech.hakuri.graven.utils.player.InvHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {

    public static final AutoTotem INSTANCE = new AutoTotem();

    private final BoolSetting strict = boolSetting("Strict", true);
    private final DoubleSetting health = doubleSetting("Health", 16.0, 0.0, 36.0, 0.5);
    private final BoolSetting checkGapple = boolSetting("Check Gapple", true);

    private AutoTotem() {
        super("Auto Totem", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        if (nullCheck()) return null;
        return String.valueOf(InvHelper.getItemCount(Items.TOTEM_OF_UNDYING));
    }

    @EventHandler
    public void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.gameMode == null) return;

        if (!shouldHoldTotem()) {
            return;
        }

        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int slot = InvHelper.getItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }

        moveItemToOffhand(slot);
    }

    private boolean shouldHoldTotem() {
        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (totalHealth <= health.getValue().floatValue()) {
            return true;
        }

        // Keep an empty offhand safe by filling it with a totem.
        if (mc.player.getOffhandItem().isEmpty()) {
            return true;
        }

        // Simple void safety check for modern overworld min Y.
        if (mc.player.getY() < -64.0) {
            return true;
        }

        if (checkGapple.getValue()) {
            Item mainHandItem = mc.player.getMainHandItem().getItem();
            if (mainHandItem == Items.GOLDEN_APPLE || mainHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                return true;
            }
        }

        return false;
    }

    private void moveItemToOffhand(int slot) {
        if (slot < 9) {
            slot += 36;
        }

        if (!strict.getValue()) {
            ClickSlotUtils.swap(slot, 40);
            return;
        }

        ClickSlotUtils.click(slot);
        ClickSlotUtils.click(45);

        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            ClickSlotUtils.click(slot);
        }
    }

}
