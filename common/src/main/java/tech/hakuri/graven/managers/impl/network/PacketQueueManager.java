package tech.hakuri.graven.managers.impl.network;

import net.minecraft.network.PacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import tech.hakuri.graven.Constants;
import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.QueuePacketEvent;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.network.TransferOrigin;
import tech.hakuri.graven.utils.render.WireframeEntityRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import static tech.hakuri.graven.Constants.mc;

/** 统一保存并按传输方向重放网络包，行为与 BMW PacketQueueManager 对齐。 */
public final class PacketQueueManager {

    private static final int FINAL_DECISION_PRIORITY = -1000;
    private static final int PATH_COLOR = new Color(116, 152, 255, 220).getRGB();
    private static final Color LAG_PLAYER_SIDE_COLOR = new Color(36, 32, 147, 87);
    private static final Color LAG_PLAYER_LINE_COLOR = new Color(36, 32, 147, 255);

    private final ConcurrentLinkedQueue<PacketSnapshot> packetQueue = new ConcurrentLinkedQueue<>();

    public PacketQueueManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public boolean isLagging() {
        return !packetQueue.isEmpty();
    }

    public List<Vec3> positions() {
        List<Vec3> positions = new ArrayList<>();
        for (PacketSnapshot snapshot : packetQueue) {
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
                positions.add(new Vec3(move.getX(0.0), move.getY(0.0), move.getZ(0.0)));
            }
        }
        return positions;
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (mc.getConnection() == null || !mc.getConnection().getConnection().isConnected()) {
            packetQueue.clear();
            return;
        }

        flushIfUnclaimed(TransferOrigin.OUTGOING);
        flushIfUnclaimed(TransferOrigin.INCOMING);
    }

    @EventHandler(priority = FINAL_DECISION_PRIORITY)
    private void onPacketSend(PacketEvent.Send event) {
        handle(event, event.getPacket(), TransferOrigin.OUTGOING);
    }

    @EventHandler(priority = FINAL_DECISION_PRIORITY)
    private void onPacketReceive(PacketEvent.Receive event) {
        handle(event, event.getPacket(), TransferOrigin.INCOMING);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        packetQueue.clear();
    }

    @EventHandler(priority = -900)
    private void onRender3D(tech.hakuri.graven.events.impl.Render3DEvent event) {
        List<Vec3> positions = positions();
        for (int i = 1; i < positions.size(); i++) {
            Render3DScheduler.INSTANCE.addLine(positions.get(i - 1), positions.get(i), PATH_COLOR, 2.0F);
        }

        if (!positions.isEmpty()
                && mc.player != null
                && mc.level != null
                && !mc.options.getCameraType().isFirstPerson()) {
            RemotePlayer lagPlayer = new RemotePlayer(mc.level, mc.player.getGameProfile());
            lagPlayer.copyPosition(mc.player);
            lagPlayer.setPos(positions.getFirst());
            lagPlayer.setYRot(Managers.ROTATION.getLastRotation().getYaw());
            lagPlayer.setXRot(Managers.ROTATION.getLastRotation().getPitch());
            lagPlayer.setYHeadRot(lagPlayer.getYRot());
            lagPlayer.yBodyRot = lagPlayer.getYRot();
            lagPlayer.getAttributes().assignAllValues(mc.player.getAttributes());
            lagPlayer.getInventory().replaceWith(mc.player.getInventory());
            lagPlayer.setOldPosAndRot();
            WireframeEntityRenderer.render(event.getPoseStack(), lagPlayer, 1.0,
                    LAG_PLAYER_SIDE_COLOR, LAG_PLAYER_LINE_COLOR, 2.0F);
        }
    }

    private void handle(Object cancellable, Packet<?> packet, TransferOrigin origin) {
        boolean alreadyCancelled = cancellable instanceof PacketEvent.Send send
                ? send.isCancelled()
                : ((PacketEvent.Receive) cancellable).isCancelled();
        if (alreadyCancelled) return;

        Action action = fireEvent(packet, origin);
        if (action == Action.FLUSH) {
            flush(origin);
            return;
        }
        if (action == Action.PASS || mustPass(packet)) return;

        if (mustFlush(packet)) {
            flush(origin);
            return;
        }

        if (cancellable instanceof PacketEvent.Send send) send.cancel();
        else ((PacketEvent.Receive) cancellable).cancel();
        packetQueue.add(new PacketSnapshot(packet, origin, System.currentTimeMillis()));
    }

    private void flushIfUnclaimed(TransferOrigin origin) {
        if (fireEvent(null, origin) == Action.FLUSH) flush(origin);
    }

    private Action fireEvent(Packet<?> packet, TransferOrigin origin) {
        return EventBus.INSTANCE.post(new QueuePacketEvent(packet, origin)).getAction();
    }

    private boolean mustPass(Packet<?> packet) {
        if (packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket
                || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ServerboundChatCommandSignedPacket
                || packet instanceof ClientboundSystemChatPacket) {
            return true;
        }
        return packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == SoundEvents.PLAYER_HURT;
    }

    private boolean mustFlush(Packet<?> packet) {
        return packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundPlayerRotationPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F;
    }

    public void flush(TransferOrigin origin) {
        flush(snapshot -> snapshot.origin() == origin);
    }

    public void flush(Predicate<PacketSnapshot> condition) {
        packetQueue.removeIf(snapshot -> {
            if (!condition.test(snapshot)) return false;
            flushSnapshot(snapshot);
            return true;
        });
    }

    public void flush(int movementPacketCount) {
        int count = 0;
        while (count < movementPacketCount) {
            PacketSnapshot snapshot = packetQueue.poll();
            if (snapshot == null) break;
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket move && move.hasPosition()) count++;
            flushSnapshot(snapshot);
        }
    }

    public void cancel() {
        Vec3 firstPosition = positions().stream().findFirst().orElse(null);
        if (firstPosition != null && mc.player != null) mc.player.setPos(firstPosition);
        for (PacketSnapshot snapshot : packetQueue) {
            if (!(snapshot.packet() instanceof ServerboundMovePlayerPacket)) flushSnapshot(snapshot);
        }
        packetQueue.clear();
    }

    public boolean isAboveTime(long delayMillis) {
        PacketSnapshot first = packetQueue.peek();
        return first != null && System.currentTimeMillis() - first.timestamp() >= delayMillis;
    }

    private void flushSnapshot(PacketSnapshot snapshot) {
        try {
            if (snapshot.origin() == TransferOrigin.OUTGOING) {
                PacketUtils.sendSilently(snapshot.packet());
            } else {
                handleIncoming(snapshot.packet());
            }
        } catch (RuntimeException exception) {
            Constants.LOGGER.error("重放 {} 网络包 {} 失败", snapshot.origin(), snapshot.packet().type(), exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleIncoming(Packet<?> packet) {
        PacketListener listener = mc.getConnection().getConnection().getPacketListener();
        ((Packet) packet).handle(listener);
    }

    public enum Action {
        FLUSH(0),
        PASS(1),
        QUEUE(2);

        private final int priority;

        Action(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    public record PacketSnapshot(Packet<?> packet, TransferOrigin origin, long timestamp) {
    }
}
