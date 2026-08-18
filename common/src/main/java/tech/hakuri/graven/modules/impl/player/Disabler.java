package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameJoinedEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.SendPositionEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.player.ChatUtils;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.player.Input;

import java.util.Random;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private enum Mode {
        Heypixel,
        CubeCraft
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Heypixel, this::onModeChanged);

    // 原有设置全部归属于 Heypixel 模式，保留配置键和默认值以兼容旧配置。
    private final BoolSetting badPacketsA = boolSetting("Grim Bad PacketsA", true, this::isHeypixelMode);
    private final BoolSetting grimDuplicateRotPlace = boolSetting("Grim Duplicate RotPlace", true, this::isHeypixelMode);
    private final BoolSetting acaFastSwitch = boolSetting("ACA Fast Switch", true, this::isHeypixelMode);
    private final BoolSetting acaInventoryFrequency = boolSetting("ACA Inventory Frequency", false, this::isHeypixelMode);
    private final BoolSetting acaAimStep = boolSetting("ACA Aim Step", true, this::isHeypixelMode);
    private final BoolSetting acaPerfectRotation = boolSetting("ACA Perfect Rotation", true, this::isHeypixelMode);
    private final BoolSetting themisBlink = boolSetting("Themis Blink", true, this::isHeypixelMode);
    private final BoolSetting onlyRemoteServer = boolSetting("Only Remote Server", false, this::isHeypixelMode);
    private final BoolSetting logging = boolSetting("Logging", false, this::isHeypixelMode);

    private final BoolSetting sprinting = boolSetting("Sprinting", true, this::isHeypixelMode);
    private final BoolSetting input = boolSetting("Input", true, this::isHeypixelMode);

    private static final long CUBECRAFT_WAIT_DURATION_MS = 5_000L;
    private static final long CUBECRAFT_PACKET_DELAY_MS = 2_000L;
    private final Queue<CubePacketEntry> cubePacketQueue = new ConcurrentLinkedQueue<>();
    private boolean cubeLag;
    private long cubeWaitStartedAt;
    private boolean cubeWaiting;
    private long cubeDisabledSince;

    private final Random random = new Random();
    private int lastSentSlot = -1;
    private long inventoryOpenTime;
    private boolean inventoryOpen;
    private ServerboundContainerClosePacket storedClosePacket;
    private long inventoryCloseDelay;
    private long inventoryCloseQueuedAt;
    private long themisBlinkLastSend;
    private int themisBlinkCount;
    private float lastYaw;
    private float lastPitch;
    private float currentYaw;
    private float currentPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastPlacedYawDiff;
    private float lastPlacedPitchDiff;
    private boolean rotated;
    private Input oldInput;
    private boolean shouldRestore;

    private static final double[] PERFECT_ROTATION_STEPS = {
            0.0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45.0,
            50.625, 56.25, 61.875, 67.5, 73.125, 78.75, 84.375, 90.0
    };

    private Disabler() {
        super("Disabler", Category.PLAYER);
        resetState();
        resetCubeCraftState();
    }

    @Override
    protected void onEnable() {
        resetState();
        resetCubeCraftState();
    }

    @Override
    protected void onDisable() {
        if (shouldRestore && mc.player != null) {
            restoreInput();
        }
        flushCubeCraftPackets();
        resetState();
        resetCubeCraftState();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (shouldRestore && mc.player != null) {
            restoreInput();
        }
        resetState();
        resetCubeCraftState();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (shouldRestore && mc.player != null) {
            restoreInput();
        }
        resetState();
        resetCubeCraftState();
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (!mode.is(Mode.CubeCraft) || mc.player == null) {
            return;
        }

        boolean waiting = isCubeCraftWaiting();
        if (cubeWaiting && !waiting) {
            cubeDisabledSince = System.currentTimeMillis();
        }
        cubeWaiting = waiting;

        CubePacketEntry entry;
        while ((entry = cubePacketQueue.peek()) != null) {
            if (System.currentTimeMillis() - entry.queuedAt() < CUBECRAFT_PACKET_DELAY_MS) {
                break;
            }
            cubePacketQueue.poll();
            PacketUtils.sendSilently(entry.packet());
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (mode.is(Mode.CubeCraft)) {
            handleCubeCraftReceive(packet);
            return;
        }
        if (packet instanceof ClientboundLoginPacket) {
            resetState();
            return;
        }
        if (!isActiveContext()) {
            resetState();
            return;
        }

        releaseStoredClosePacketIfReady();
        if (packet instanceof ClientboundOpenScreenPacket) {
            inventoryOpenTime = System.currentTimeMillis();
            inventoryOpen = true;
            log("Inventory opened");
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.CubeCraft)) {
            handleCubeCraftSend(event);
            return;
        }
        if (!isActiveContext()) {
            resetState();
            return;
        }

        releaseStoredClosePacketIfReady();
        Packet<?> packet = event.getPacket();

        processCarriedItem(event, packet);
        if (event.isCancelled()) {
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket closePacket && acaInventoryFrequency.getValue()
                && inventoryOpen) {
            long openDuration = System.currentTimeMillis() - inventoryOpenTime;
            inventoryOpen = false;
            if (openDuration <= 150L) {
                event.cancel();
                storedClosePacket = closePacket;
                inventoryCloseDelay = 151L - openDuration;
                inventoryCloseQueuedAt = System.currentTimeMillis();
                log("InventoryFrequency: delayed close by " + inventoryCloseDelay + "ms");
                return;
            }
        }

        processThemisBlink(packet);
        processDuplicateRotation(packet);
        processAimRotation(packet);

        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            event.cancel();
            boolean sprinted = false;
            if (input.getValue()) {
                spoofInput();
            }
            if (sprinting.getValue() && mc.player.isSprinting()) {
                sendSprinting(false);
                sprinted = true;
            }
            PacketUtils.sendSilently(packet);
            if (sprinted) {
                sendSprinting(true);
            }
            if (shouldRestore) {
                restoreInput();
            }
        }
    }

    private void handleCubeCraftReceive(Packet<?> packet) {
        if (mc.player == null) {
            return;
        }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            cubeWaitStartedAt = System.currentTimeMillis();
            cubeLag = true;
            flushCubeCraftPackets();
            ChatUtils.addChatMessage("Lag detected!");
        }
    }

    private void handleCubeCraftSend(PacketEvent.Send event) {
        if (mc.player == null || isCubeCraftWaiting()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerCommandPacket command
                && command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            event.cancel();
            return;
        }
        if (packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket) {
            cubePacketQueue.add(new CubePacketEntry(packet, System.currentTimeMillis()));
            event.cancel();
        }
    }

    private boolean isHeypixelMode() {
        return mode.is(Mode.Heypixel);
    }

    private void onModeChanged(Mode ignored) {
        if (!isEnabled()) {
            return;
        }
        if (shouldRestore && mc.player != null) {
            restoreInput();
        }
        flushCubeCraftPackets();
        resetState();
        resetCubeCraftState();
    }

    private boolean isCubeCraftWaiting() {
        return mc.player != null && cubeLag && cubeWaitStartedAt > 0L
                && System.currentTimeMillis() - cubeWaitStartedAt < CUBECRAFT_WAIT_DURATION_MS;
    }

    private void flushCubeCraftPackets() {
        CubePacketEntry entry;
        while ((entry = cubePacketQueue.poll()) != null) {
            PacketUtils.sendSilently(entry.packet());
        }
    }

    private void resetCubeCraftState() {
        cubePacketQueue.clear();
        cubeLag = false;
        cubeWaitStartedAt = 0L;
        cubeWaiting = false;
        cubeDisabledSince = System.currentTimeMillis();
    }

    public double getCubeCraftDisabledDuration() {
        if (isCubeCraftWaiting()) {
            return 0.0D;
        }
        return (System.currentTimeMillis() - cubeDisabledSince) / 1_000.0D;
    }

    public int getCubeCraftQueuedPacketCount() {
        return cubePacketQueue.size();
    }

    public String getInfo() {
        return mode.is(Mode.CubeCraft) ? (isCubeCraftWaiting() ? "Waiting" : "Sentinel") : mode.getValue().toString();
    }

    private boolean isActiveContext() {
        return mc.player != null
                && !(mc.isSingleplayer() && onlyRemoteServer.getValue())
                && mc.player.isAlive()
                && !mc.player.isDeadOrDying()
                && !mc.player.isSpectator()
                && !(mc.screen instanceof ProgressScreen);
    }

    private void processThemisBlink(Packet<?> packet) {
        if (!themisBlink.getValue()) return;
        long now = System.currentTimeMillis();
        if (now - themisBlinkLastSend > 200L) {
            if (themisBlinkCount == 0) {
                PacketUtils.sendSilently(new ServerboundPongPacket(0));
            }
            themisBlinkLastSend = now;
            themisBlinkCount = 0;
        }
        if (packet instanceof ServerboundMovePlayerPacket.StatusOnly || packet instanceof ServerboundPongPacket) {
            themisBlinkCount++;
        }
    }

    private void processCarriedItem(PacketEvent.Send event, Packet<?> packet) {
        if (!(packet instanceof ServerboundSetCarriedItemPacket carried)) return;
        int slot = carried.getSlot();
        if (badPacketsA.getValue() && slot == lastSentSlot && slot != -1) {
            event.cancel();
            log("BadPacketsA: cancelled duplicate slot " + slot);
            return;
        }
        if (acaFastSwitch.getValue() && lastSentSlot != -1 && slot != lastSentSlot) {
            sendIntermediateSlots(lastSentSlot, slot);
        }
        lastSentSlot = slot;
    }

    private void releaseStoredClosePacketIfReady() {
        if (storedClosePacket == null || System.currentTimeMillis() - inventoryCloseQueuedAt < inventoryCloseDelay) {
            return;
        }
        PacketUtils.sendSilently(storedClosePacket);
        storedClosePacket = null;
        inventoryCloseDelay = 0L;
        inventoryCloseQueuedAt = 0L;
        log("InventoryFrequency: released close packet");
    }

    private void processDuplicateRotation(Packet<?> packet) {
        if (!grimDuplicateRotPlace.getValue()) return;
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasRotation()) {
            float previousYaw = currentYaw;
            float previousPitch = currentPitch;
            currentYaw = move.yRot;
            currentPitch = move.xRot;
            yawDiff = Math.abs(currentYaw - previousYaw);
            pitchDiff = Math.abs(currentPitch - previousPitch);
            rotated = true;

            float yawDelta = Math.abs(yawDiff - lastPlacedYawDiff);
            if (yawDiff > 2.0F && yawDelta < 1.0E-4F) {
                move.yRot = currentYaw - (0.001F + random.nextFloat() * 0.009F);
                log("DuplicateRotPlace: modified yaw");
            }
            float pitchDelta = Math.abs(pitchDiff - lastPlacedPitchDiff);
            if (pitchDiff > 2.0F && pitchDelta < 1.0E-4F) {
                move.xRot = clampPitch(currentPitch - (0.001F + random.nextFloat() * 0.009F));
                log("DuplicateRotPlace: modified pitch");
            }
        } else if (packet instanceof ServerboundUseItemOnPacket && rotated) {
            lastPlacedYawDiff = yawDiff;
            lastPlacedPitchDiff = pitchDiff;
            rotated = false;
        }
    }

    private void processAimRotation(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket move) || !move.hasRotation()) return;
        if (!acaAimStep.getValue() && !acaPerfectRotation.getValue()) return;

        float yaw = move.yRot;
        float pitch = move.xRot;
        boolean modified = false;
        if (acaAimStep.getValue() && isAimStepRotation(yaw, pitch)) {
            float[] stepped = applyAimStep(yaw, pitch);
            yaw = stepped[0];
            pitch = stepped[1];
            modified = true;
        }
        if (acaPerfectRotation.getValue()) {
            float[] perfected = applyPerfectRotation(yaw, pitch);
            if (perfected[0] != yaw || perfected[1] != pitch) {
                yaw = perfected[0];
                pitch = perfected[1];
                modified = true;
            }
        }
        if (modified) {
            move.yRot = yaw;
            move.xRot = clampPitch(pitch);
            log("PerfectRotation: modified rotation");
        }
        lastYaw = move.yRot;
        lastPitch = move.xRot;
    }

    private boolean isAimStepRotation(float yaw, float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) return false;
        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        return yawDelta < 1.0E-5 && pitchDelta > 1.0 || pitchDelta < 1.0E-5 && yawDelta > 1.0;
    }

    private float[] applyAimStep(float yaw, float pitch) {
        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            yaw = lastYaw + (float) (random.nextGaussian() * 0.001);
        }
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            pitch = lastPitch + (float) (random.nextGaussian() * 0.001);
        }
        return new float[]{yaw, pitch};
    }

    private float[] applyPerfectRotation(float yaw, float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) return new float[]{yaw, pitch};
        double yawDelta = Math.abs(wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        if (!isNearZeroOrMultiple(yawDelta) && isKnownRotationStep(yawDelta)) {
            yaw += (float) (random.nextGaussian() * 0.005);
        }
        if (!isNearZeroOrMultiple(pitchDelta) && isKnownRotationStep(pitchDelta)) {
            pitch += (float) (random.nextGaussian() * 0.005);
        }
        return new float[]{yaw, pitch};
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        int distance = Math.abs(fromSlot - toSlot);
        if (distance <= 1 || isWrapAroundSlot(fromSlot, toSlot)) return;
        int step = fromSlot > toSlot ? -1 : 1;
        for (int slot = fromSlot + step; slot != toSlot; slot += step) {
            if (slot >= 0 && slot <= 8) {
                PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
                log("ACA Fast Switch: sent intermediate slot " + slot);
            }
        }
    }

    private boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }

    private void sendSprinting(boolean sprintState) {
        mc.player.setSprinting(sprintState);
        mc.player.wasSprinting = sprintState;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
                sprintState ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    private void spoofInput() {
        if (shouldRestore) return;
        oldInput = mc.player.input.keyPresses;
        mc.player.input.keyPresses = Input.EMPTY;
        mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
        mc.player.lastSentInput = Input.EMPTY;
        shouldRestore = true;
    }

    private void restoreInput() {
        if (!shouldRestore || mc.player == null) return;
        mc.player.input.keyPresses = oldInput == null ? Input.EMPTY : oldInput;
        mc.player.lastSentInput = mc.player.input.keyPresses;
        oldInput = null;
        shouldRestore = false;
    }

    private void resetState() {
        lastSentSlot = -1;
        inventoryOpenTime = 0L;
        inventoryOpen = false;
        storedClosePacket = null;
        inventoryCloseDelay = 0L;
        inventoryCloseQueuedAt = 0L;
        themisBlinkLastSend = System.currentTimeMillis();
        themisBlinkCount = 0;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        currentYaw = 0.0F;
        currentPitch = 0.0F;
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastPlacedYawDiff = 0.0F;
        lastPlacedPitchDiff = 0.0F;
        rotated = false;
        oldInput = null;
        shouldRestore = false;
    }

    private boolean isNearZeroOrMultiple(double value) {
        return Math.abs(value) <= 1.0E-10 || isMultipleOf(360.0, value);
    }

    private boolean isKnownRotationStep(double value) {
        if (!Double.isFinite(value)) return false;
        for (double step : PERFECT_ROTATION_STEPS) {
            if (isMultipleOf(step, value)) return true;
        }
        return false;
    }

    private boolean isMultipleOf(double base, double value) {
        if (base == 0.0) return Math.abs(value) <= 1.0E-10;
        return Math.abs(value / base - Math.round(value / base)) <= 1.0E-10;
    }

    private float wrapDegrees(float degrees) {
        while (degrees > 180.0F) degrees -= 360.0F;
        while (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }

    private float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private void log(String message) {
        if (logging.getValue()) {
            ChatUtils.addChatMessage(false, "[Disabler] " + message);
        }
    }

    private record CubePacketEntry(Packet<?> packet, long queuedAt) {
    }
}
