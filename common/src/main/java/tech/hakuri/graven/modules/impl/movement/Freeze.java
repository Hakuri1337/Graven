package tech.hakuri.graven.modules.impl.movement;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.QueuePacketEvent;
import tech.hakuri.graven.events.impl.Render3DEvent;
import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.network.PacketQueueManager;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.StringListSetting;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.network.TransferOrigin;
import tech.hakuri.graven.utils.player.FreezeMovementPredictor;
import tech.hakuri.graven.utils.rotation.Rot2f;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** BMW Freeze 的 Graven 适配实现。 */
public final class Freeze extends Module {

    public static final Freeze INSTANCE = new Freeze();

    private static final int PREDICTION_COLOR = new Color(0, 128, 255, 255).getRGB();
    private static final double MIN_OFFSET = 0.002D;
    private static final double MAX_OFFSET = 0.01D;
    private static final float MIN_OFFSET_DIFFERENCE = 1.0E-6F;

    public final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Stationary);
    public final BoolSetting disableOnFlag = boolSetting("DisableOnFlag", true);
    public final BoolSetting notification = boolSetting("Notification", false);
    public final BoolSetting balanceWarp = boolSetting("BalanceWarp", false);
    public final BoolSetting bypassNegativeTimer = boolSetting("BypassNegativeTimer", true);
    public final StringListSetting queueOrigin = stringListSetting("Queue Origin", List.of("Outgoing"),
            () -> mode.is(Mode.Queue));
    public final StringListSetting cancelOrigin = stringListSetting("Cancel Origin", List.of("Outgoing"),
            () -> mode.is(Mode.Cancel));
    public final BoolSetting cancelC0B = boolSetting("CancelC0B", false,
            () -> mode.is(Mode.Stationary));

    private final FloatOffsetGenerator yawOffset = new FloatOffsetGenerator();
    private final FloatOffsetGenerator pitchOffset = new FloatOffsetGenerator();
    private int missedOutTick;
    private boolean warpInProgress;

    private Freeze() {
        super("Freeze", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        missedOutTick = 0;
        warpInProgress = false;
    }

    @Override
    protected void onDisable() {
        if (balanceWarp.getValue() && mc.player != null) {
            warpInProgress = true;
            try {
                while (missedOutTick > 0) {
                    mc.player.tick();
                    missedOutTick--;
                }
            } finally {
                warpInProgress = false;
            }
        }

        missedOutTick = 0;
        if (bypassNegativeTimer.getValue() && !nullCheck()) interact();
    }

    @Override
    protected void resetCustomState() {
        missedOutTick = 0;
        warpInProgress = false;
        yawOffset.reset();
        pitchOffset.reset();
    }

    public void interact() {
        if (nullCheck() || mc.getConnection() == null) return;

        InteractionHand hand = InteractionHand.OFF_HAND;
        int restoreSlot = -1;
        if (!isInteractable(mc.player.getOffhandItem())) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (!isInteractable(stack)) continue;

                hand = InteractionHand.MAIN_HAND;
                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                if (slot != selectedSlot) {
                    restoreSlot = selectedSlot;
                    selectSlot(slot);
                }
                break;
            }
        }

        Rot2f rotation = Managers.ROTATION.getRotation();
        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.getConnection().send(new ServerboundUseItemPacket(
                    hand, prediction.currentSequence(), rotation.getYaw(), rotation.getPitch()));
        }

        if (restoreSlot != -1) selectSlot(restoreSlot);
    }

    private boolean isInteractable(ItemStack stack) {
        if (stack.is(Items.ENDER_PEARL)
                || stack.is(Items.TNT)
                || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.WIND_CHARGE)) {
            return false;
        }

        ItemUseAnimation animation = stack.getUseAnimation();
        return animation != ItemUseAnimation.EAT
                && animation != ItemUseAnimation.DRINK
                && animation != ItemUseAnimation.BOW
                && animation != ItemUseAnimation.CROSSBOW;
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (warpInProgress || nullCheck()) return;
        event.cancel();
        missedOutTick++;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!balanceWarp.getValue() || missedOutTick <= 0 || warpInProgress || nullCheck()) return;

        float yaw = mc.gameRenderer.getMainCamera().yRot();
        float pitch = mc.gameRenderer.getMainCamera().xRot();
        List<Vec3> positions = FreezeMovementPredictor.predict(mc, missedOutTick, yaw, pitch);
        for (int i = 1; i < positions.size(); i++) {
            Render3DScheduler.INSTANCE.addLine(positions.get(i - 1), positions.get(i), PREDICTION_COLOR, 2.0F);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundPlayerRotationPacket) {
            missedOutTick = 0;
            if (disableOnFlag.getValue()) {
                if (notification.getValue()) {
                    Managers.NOTIFICATION.info(getTranslatedName(),
                            GravenTranslations.Freeze.DISABLED_ON_FLAG.getTranslatedName());
                }
                setEnabled(false);
                return;
            }
        }

        if (mode.is(Mode.Cancel) && includes(cancelOrigin, TransferOrigin.INCOMING)) event.cancel();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.Cancel)) {
            if (includes(cancelOrigin, TransferOrigin.OUTGOING)) event.cancel();
            return;
        }
        if (!mode.is(Mode.Stationary) || nullCheck()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPongPacket) {
            if (cancelC0B.getValue()) event.cancel();
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket) {
            event.cancel();
            return;
        }

        float yawOffset = this.yawOffset.next();
        float pitchOffset = this.pitchOffset.next();
        Rot2f rotation = Managers.ROTATION.getRotation();
        float yaw = rotation.getYaw();
        float pitch = rotation.getPitch();

        if (packet instanceof ServerboundUseItemPacket useItem) {
            event.cancel();
            sendRotation(mc.player.getYRot() + yawOffset, mc.player.getXRot() + pitchOffset);
            PacketUtils.sendSilently(new ServerboundUseItemPacket(
                    useItem.getHand(), useItem.getSequence(), yaw + yawOffset, pitch + pitchOffset));
        } else if (packet instanceof ServerboundInteractPacket || packet instanceof ServerboundUseItemOnPacket) {
            event.cancel();
            sendRotation(yaw + yawOffset, pitch + pitchOffset);
            PacketUtils.sendSilently(packet);
        }
    }

    @EventHandler
    private void onQueuePacket(QueuePacketEvent event) {
        if (mode.is(Mode.Queue) && includes(queueOrigin, event.getOrigin())) {
            event.setAction(PacketQueueManager.Action.QUEUE);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        setEnabled(false);
    }

    private void sendRotation(float yaw, float pitch) {
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
    }

    private boolean includes(StringListSetting setting, TransferOrigin origin) {
        return setting.contains(origin.settingName());
    }

    public enum Mode {
        Queue,
        Cancel,
        Stationary
    }

    private static final class FloatOffsetGenerator {
        private float previous;

        private float next() {
            float offset;
            do {
                offset = (float) ThreadLocalRandom.current().nextDouble(MIN_OFFSET, MAX_OFFSET);
            } while (Math.abs(offset - previous) < MIN_OFFSET_DIFFERENCE);
            previous = offset;
            return offset;
        }

        private void reset() {
            previous = 0.0F;
        }
    }
}
