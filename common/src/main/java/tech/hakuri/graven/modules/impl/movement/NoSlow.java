package tech.hakuri.graven.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.SlowdownEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.openzen.OpenZenInboundBlinkQueue;
import tech.hakuri.graven.utils.openzen.OpenZenInputGate;
import tech.hakuri.graven.utils.openzen.OpenZenPacketBypass;

import java.util.ArrayDeque;
import java.util.Queue;

/** OpenZen NoSlow 的完整包状态机适配，保留食物/药水与弓/弩两条路径。 */
public final class NoSlow extends Module {
    public static final NoSlow INSTANCE = new NoSlow();

    private enum Mode { Vanilla, Jump, Grim1_2, Grim1_3 }
    private enum UseState { IDLE, WAITING, SWAPPING, USING }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true);
    private final BoolSetting crossbow = boolSetting("Crossbow", true);
    private final BoolSetting potion = boolSetting("Potion", true);
    private final BoolSetting keepSprinting = boolSetting("Keep Sprinting", true);
    private final IntSetting useItemTicks = intSetting("Use Item Ticks", 1, 1, 20, 1,
            () -> isGrimMode() && bow.getValue());

    private int onGroundTick;
    private int blinkTicks;
    private int blinkDuration;
    private int releaseTicksRemaining;
    private int pendingUseSequence;
    private InteractionHand pendingUseHand;
    private InteractionHand useHand = InteractionHand.MAIN_HAND;
    private InteractionHand lastUseHand = InteractionHand.MAIN_HAND;
    private int swapInitSlot = -1;
    private boolean didSwapHand;
    private boolean shouldReleaseItem;
    private boolean didSwapOffhand;
    private int idleTicks;
    private UseState useState = UseState.IDLE;
    private int savedHotbarSlot = -1;
    private final Queue<ServerboundPongPacket> pongQueue = new ArrayDeque<>();
    private final OpenZenInboundBlinkQueue inboundBlink = new OpenZenInboundBlinkQueue();

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
    }

    private boolean isGrimMode() {
        return mode.is(Mode.Grim1_2) || mode.is(Mode.Grim1_3);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        inboundBlink.stop();
        resetOffhandState();
        resetState();
        OpenZenInputGate.restoreAll();
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (nullCheck() || !mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (stack.isEmpty()) return;
        if (!isEnabledFor(stack)) return;

        if (isGrimMode() && !bow.getValue() && isFoodOrPotion(stack)) {
            if (useState != UseState.USING) {
                mc.options.keyUse.setDown(false);
                if (useState == UseState.IDLE) {
                    useState = UseState.WAITING;
                    savedHotbarSlot = mc.player.getInventory().getSelectedSlot();
                }
            } else {
                event.setSlowdown(false);
                if (keepSprinting.getValue()) mc.player.setSprinting(true);
            }
            return;
        }

        if (mode.is(Mode.Vanilla)) {
            event.setSlowdown(false);
        } else if (mode.is(Mode.Jump)) {
            if (onGroundTick == 1 && mc.player.getUseItemRemainingTicks() <= 30) event.setSlowdown(false);
        } else if (mode.is(Mode.Grim1_2)) {
            event.setSlowdown(mc.player.getUseItemRemainingTicks() % 2 != 0 || mc.player.getUseItemRemainingTicks() > 30);
        } else if (mode.is(Mode.Grim1_3)) {
            event.setSlowdown(mc.player.getUseItemRemainingTicks() % 3 != 0 || mc.player.getUseItemRemainingTicks() > 30);
        }

        if (isGrimMode() && keepSprinting.getValue()) mc.player.setSprinting(true);
    }

    @EventHandler(priority = 150)
    private void onKeyboardInput(KeyboardInputEvent event) {
        OpenZenInputGate.apply(event);
        if (mode.is(Mode.Jump) && !nullCheck() && mc.player.onGround() && mc.player.isUsingItem()
                && (event.getForward() != 0 || event.getStrafe() != 0)) event.setJump(true);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) {
            resetState();
            return;
        }
        if (mc.player.onGround()) onGroundTick++; else onGroundTick = 0;
        if (inboundBlink.isBlinking()) blinkTicks++;

        if (pendingUseHand != null) {
            startUseItem(pendingUseHand, pendingUseSequence);
            pendingUseHand = null;
            pendingUseSequence = 0;
        }
        if (releaseTicksRemaining > 0) {
            releaseUseKey();
            if (--releaseTicksRemaining == 0) restoreUseKeyState();
        }
        if (blinkTicks >= blinkDuration && inboundBlink.isBlinking()) finishBlink();

        if (useState == UseState.USING && !mc.player.isUsingItem() && ++idleTicks >= 5) resetOffhandState();
        else if (mc.player.isUsingItem()) idleTicks = 0;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPongPacket pong && useState != UseState.IDLE && isGrimMode() && !bow.getValue()) {
            event.cancel();
            pongQueue.add(pong);
            if (useState == UseState.WAITING) {
                useState = UseState.SWAPPING;
                didSwapOffhand = true;
                sendSwapOffhand();
            }
            return;
        }
        if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                && useState == UseState.USING) resetOffhandState();

        if (!(packet instanceof ServerboundUseItemPacket use)) return;
        if (didSwapHand || releaseTicksRemaining > 0) {
            event.cancel();
            return;
        }
        if (!isGrimMode() || !bow.getValue()) return;
        ItemStack stack = mc.player.getItemInHand(use.getHand());
        if (isBowOrCrossbow(stack)) {
            if (canSwapHands()) {
                shouldReleaseItem = false;
                startBlink(2);
            } else shouldReleaseItem = true;
        } else if (isFoodOrPotion(stack)) {
            event.cancel();
            pendingUseHand = use.getHand();
            pendingUseSequence = use.getSequence();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        Packet<?> packet = event.getPacket();
        if (inboundBlink.offer(packet)) {
            event.cancel();
            return;
        }
        if (useState == UseState.SWAPPING && isEquipmentChange(packet)) {
            mc.options.keyUse.setDown(true);
            useState = UseState.USING;
            idleTicks = 0;
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId() && useState == UseState.USING) {
            mc.options.keyUse.setDown(false);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
        OpenZenInputGate.restoreAll();
    }

    private boolean isEnabledFor(ItemStack stack) {
        ItemUseAnimation anim = stack.getUseAnimation();
        if (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) return food.getValue() || potion.getValue();
        if (stack.getItem() instanceof BowItem) return bow.getValue();
        if (stack.getItem() instanceof CrossbowItem) return crossbow.getValue();
        return false;
    }

    private boolean isFoodOrPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemUseAnimation anim = stack.getUseAnimation();
        return (stack.has(DataComponents.FOOD) && (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) && food.getValue())
                || stack.getItem() instanceof PotionItem && potion.getValue();
    }

    private boolean isBowOrCrossbow(ItemStack stack) {
        return stack.getItem() instanceof BowItem && bow.getValue()
                || stack.getItem() instanceof CrossbowItem && crossbow.getValue();
    }

    private boolean canSwapHands() {
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        if (main.isEmpty() || off.isEmpty()) return true;
        return main.getItem() != off.getItem();
    }

    private void startUseItem(InteractionHand hand, int sequence) {
        didSwapHand = true;
        lastUseHand = hand;
        swapInitSlot = mc.player.getInventory().getSelectedSlot();
        useHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        sendSwapOffhand();
        if (sequence > 0) OpenZenPacketBypass.send(new ServerboundUseItemPacket(useHand, sequence, mc.player.getYRot(), mc.player.getXRot()));
        else {
            try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
                OpenZenPacketBypass.send(new ServerboundUseItemPacket(useHand, prediction.currentSequence(), mc.player.getYRot(), mc.player.getXRot()));
            }
        }
        startBlink(2);
    }

    private void finishBlink() {
        if (!didSwapHand) {
            inboundBlink.stop();
            return;
        }
        if (useHand != lastUseHand) sendSwapOffhand();
        didSwapHand = false;
        releaseTicksRemaining = useItemTicks.getValue();
        releaseUseKey();
        OpenZenPacketBypass.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO, Direction.DOWN));
        inboundBlink.stop();
    }

    private void startBlink(int duration) {
        blinkTicks = 0;
        blinkDuration = duration;
        inboundBlink.start();
    }

    private void sendSwapOffhand() {
        OpenZenPacketBypass.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO, Direction.DOWN));
    }

    private void resetOffhandState() {
        while (!pongQueue.isEmpty()) OpenZenPacketBypass.send(pongQueue.poll());
        if (didSwapOffhand) sendSwapOffhand();
        didSwapOffhand = false;
        useState = UseState.IDLE;
        idleTicks = 0;
        savedHotbarSlot = -1;
        OpenZenInputGate.restore(mc.options.keyUse);
    }

    private void releaseUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) { }
    }

    private void restoreUseKeyState() {
        if (mc.getWindow() == null) return;
        InputConstants.Key key = InputConstants.getKey(mc.options.keyUse.saveString());
        mc.options.keyUse.setDown(key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == 1
                : InputConstants.isKeyDown(mc.getWindow(), key.getValue()));
    }

    private boolean isEquipmentChange(Packet<?> packet) {
        if (packet instanceof ClientboundContainerSetSlotPacket slot) return slot.getSlot() == 45 || slot.getContainerId() == 0;
        return packet instanceof ClientboundSetEquipmentPacket equipment
                && equipment.getSlots().stream().anyMatch(pair -> pair.getFirst() == EquipmentSlot.OFFHAND);
    }

    private void resetState() {
        inboundBlink.clear();
        pongQueue.clear();
        didSwapHand = false;
        didSwapOffhand = false;
        shouldReleaseItem = false;
        pendingUseHand = null;
        releaseTicksRemaining = 0;
        blinkTicks = 0;
        blinkDuration = 0;
        useState = UseState.IDLE;
        savedHotbarSlot = -1;
        onGroundTick = 0;
    }
}
