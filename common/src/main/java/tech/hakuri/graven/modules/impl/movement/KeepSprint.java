package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.AttackEntityEvent;
import tech.hakuri.graven.events.impl.AttackSlowDownEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.network.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    private final Random random = new Random();

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        Prediction
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);

    public final IntSetting slowdown = intSetting("Slowdown", 0, 0, 100, 1);
    private final BoolSetting groundOnly = boolSetting("Ground Only", false);
    private final BoolSetting prediction = boolSetting("Prediction", false);
    private final BoolSetting reachOnly = boolSetting("Reach Only", false);

    private boolean can;

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventHandler
    private void onAttackSlowDown(AttackSlowDownEvent event) {
        switch (mode.getValue()) {
            case Vanilla -> event.cancel();
            case Prediction -> mc.player.setSprinting(true);
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!mode.is(Mode.Vanilla) || nullCheck() || mc.gameMode == null) return;

        int attackSlot = mc.player.getInventory().getSelectedSlot();
        int otherSlot;
        do {
            otherSlot = random.nextInt(9);
        } while (otherSlot == attackSlot);

        mc.player.getInventory().setSelectedSlot(otherSlot);
        PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(otherSlot));

        mc.player.getInventory().setSelectedSlot(attackSlot);
        PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(attackSlot));
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        can = false;
    }

    @EventHandler
    private void onPostTick(PlayerTickEvent.Post event) {
        can = true;
    }

    public boolean shouldKeepSprint() {
        if (prediction.getValue() && !can) return false;
        if (groundOnly.getValue() && !mc.player.onGround()) return false;
        if (reachOnly.getValue()) {
            HitResult hitResult = mc.hitResult;
            if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) return false;
            return hitResult.getLocation().distanceTo(mc.player.getEyePosition()) > 3.0;
        }
        return true;
    }

}
