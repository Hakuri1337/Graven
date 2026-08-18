package tech.hakuri.graven.managers.impl.network;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.utils.network.PacketUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static tech.hakuri.graven.Constants.mc;

/** Remix PacketManager/SubCore/Blink 的共享 holder 状态机。 */
public final class RemixBlinkManager {

    private final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    private final List<Object> holders = new ArrayList<>();
    private boolean active;

    public RemixBlinkManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public synchronized void start() {
        active = true;
    }

    public synchronized void start(Object holder) {
        if (!holders.contains(holder)) holders.add(holder);
        active = true;
    }

    public synchronized void release(boolean clear) {
        if (mc.getConnection() == null || mc.player == null) {
            if (clear) packets.clear();
            return;
        }
        packets.forEach(PacketUtils::sendSilently);
        if (clear) packets.clear();
    }

    public synchronized void dispatch(boolean releasePackets) {
        if (releasePackets) release(true);
        holders.clear();
        active = false;
    }

    public synchronized void dispatch(Object holder, boolean releasePackets) {
        holders.remove(holder);
        if (holders.isEmpty()) {
            if (releasePackets) release(true);
            active = false;
        }
    }

    public void dispatch(Object holder) {
        dispatch(holder, true);
    }

    public void dispatch() {
        dispatch(true);
    }

    public synchronized void clear() {
        packets.clear();
        holders.clear();
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public int size() {
        return packets.size();
    }

    public void discardPackets() {
        packets.clear();
    }

    @EventHandler(priority = -800)
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) {
            clear();
            return;
        }
        if (mc.player.isDeadOrDying() || mc.getConnection() == null || mc.hasSingleplayerServer()) {
            dispatch(true);
            return;
        }
        Packet<?> packet = event.getPacket();
        if (!active || shouldIgnore(packet)) return;
        event.cancel();
        packets.add(packet);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        dispatch(true);
    }

    private boolean shouldIgnore(Packet<?> packet) {
        return packet instanceof ServerboundKeepAlivePacket
                || packet instanceof ServerboundPongPacket
                || packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ServerboundChatCommandSignedPacket;
    }
}
