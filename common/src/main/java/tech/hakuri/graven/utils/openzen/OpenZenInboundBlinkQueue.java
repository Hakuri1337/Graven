package tech.hakuri.graven.utils.openzen;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import tech.hakuri.graven.Constants;

import java.util.ArrayDeque;
import java.util.Queue;

/** OpenZen NoSlow 的入站 blink 队列，保留白名单与强制释放语义。 */
public final class OpenZenInboundBlinkQueue {
    private final Queue<Packet<?>> packets = new ArrayDeque<>();
    private boolean blinking;

    public void start() {
        blinking = true;
    }

    public void stop() {
        flush();
        blinking = false;
    }

    public void clear() {
        packets.clear();
        blinking = false;
    }

    public boolean isBlinking() {
        return blinking;
    }

    public boolean offer(Packet<?> packet) {
        if (!blinking || packet == null || Constants.mc.player == null || Constants.mc.level == null) return false;
        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundPlayerRotationPacket
                || packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            stop();
            return false;
        }
        if (!isBlinkable(packet)) return false;
        packets.add(packet);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void flush() {
        if (Constants.mc.getConnection() == null) {
            packets.clear();
            return;
        }
        PacketListener listener = Constants.mc.getConnection().getConnection().getPacketListener();
        while (!packets.isEmpty()) {
            Packet packet = packets.poll();
            try {
                packet.handle(listener);
            } catch (RuntimeException exception) {
                Constants.LOGGER.error("OpenZen 入站包回放失败: {}", packet.type(), exception);
                packets.clear();
                return;
            }
        }
    }

    private boolean isBlinkable(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket) return true;
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            return Constants.mc.player != null && motion.id() == Constants.mc.player.getId();
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            return slot.getSlot() == 45 || slot.getContainerId() == 0;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            return equipment.getSlots().stream().anyMatch(pair -> pair.getFirst() == net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        return false;
    }
}
