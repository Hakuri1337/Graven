package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.BlockShapeEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.SendPositionEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class AntiVoid extends Module {

    public static final AntiVoid INSTANCE = new AntiVoid();

    private enum Mode { Blink, Flag, GhostBlock }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.GhostBlock);
    private final IntSetting voidLevel = intSetting("Void Level", 0, -256, 0, 1);
    private final DoubleSetting triggerFallDistance = doubleSetting("Fall Distance", 0.5, 0.0, 6.0, 0.1);
    private final DoubleSetting flagHeight = doubleSetting("Flag Height", 0.42, 0.01, 10.0, 0.01, () -> mode.is(Mode.Flag));
    private final BoolSetting silent = boolSetting("Silent", false, () -> mode.is(Mode.Flag));

    private Vec3 rescuePosition;
    private boolean likelyFalling;
    private boolean blinking;
    private boolean silentFlag;
    private boolean checkingVoid;

    private AntiVoid() {
        super("AntiVoid", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        stopBlink(true);
        resetState();
    }

    @EventHandler
    private void onUpdate(PlayerTickEvent.Post event) {
        if (nullCheck()) return;
        if (isExempt()) {
            likelyFalling = false;
            rescuePosition = mc.player.position();
            stopBlink(true);
            return;
        }
        boolean overVoid = isOverVoid(mc.player.blockPosition());
        likelyFalling = overVoid && !mc.player.onGround() && mc.player.getDeltaMovement().y < 0.0;
        if (!overVoid) {
            rescuePosition = mc.player.position();
            stopBlink(true);
            return;
        }
        if (!likelyFalling || rescuePosition == null) return;
        if (mode.is(Mode.Blink)) {
            if (!blinking) {
                Managers.REMIX_BLINK.start(this);
                blinking = true;
            }
            if (mc.player.fallDistance >= triggerFallDistance.getValue()) {
                mc.player.setPos(rescuePosition);
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.fallDistance = 0.0F;
                discardBlink();
            }
        } else {
            stopBlink(true);
            if (mode.is(Mode.Flag) && mc.player.fallDistance >= triggerFallDistance.getValue()) {
                if (silent.getValue()) silentFlag = true;
                else mc.player.setPos(mc.player.getX(), mc.player.getY() + flagHeight.getValue(), mc.player.getZ());
                mc.player.fallDistance = 0.0F;
            }
        }
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (!silentFlag || !mode.is(Mode.Flag)) return;
        event.setY(event.getY() + flagHeight.getValue());
        silentFlag = false;
    }

    @EventHandler
    private void onBlockShape(BlockShapeEvent event) {
        if (checkingVoid || !mode.is(Mode.GhostBlock) || !likelyFalling
                || rescuePosition == null || !event.getShape().isEmpty()) return;
        if (event.getPos().getY() < Math.floor(rescuePosition.y)) event.setShape(Shapes.block());
    }

    private boolean isOverVoid(BlockPos origin) {
        int bottom = Math.max(mc.level.getMinY(), voidLevel.getValue());
        BlockPos.MutableBlockPos pos = origin.mutable();
        checkingVoid = true;
        try {
            for (int y = origin.getY(); y >= bottom; y--) {
                pos.setY(y);
                if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return false;
            }
            return true;
        } finally {
            checkingVoid = false;
        }
    }

    private boolean isExempt() {
        return mc.player.isDeadOrDying() || mc.player.isCreative()
                || mc.player.getAbilities().flying || mc.player.isFallFlying()
                || Flight.INSTANCE.isEnabled();
    }

    private void stopBlink(boolean releasePackets) {
        if (!blinking) return;
        Managers.REMIX_BLINK.dispatch(this, releasePackets);
        blinking = false;
    }

    private void discardBlink() {
        if (!blinking) return;
        Managers.REMIX_BLINK.discardPackets();
        Managers.REMIX_BLINK.dispatch(this, false);
        blinking = false;
    }

    private void resetState() {
        rescuePosition = mc.player == null ? null : mc.player.position();
        likelyFalling = false;
        blinking = false;
        silentFlag = false;
        checkingVoid = false;
    }
}
