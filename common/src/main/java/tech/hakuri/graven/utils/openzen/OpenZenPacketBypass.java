package tech.hakuri.graven.utils.openzen;

import net.minecraft.network.protocol.Packet;
import tech.hakuri.graven.Constants;
import tech.hakuri.graven.utils.network.PacketUtils;

import java.util.ArrayDeque;
import java.util.Queue;

/** OpenZen PacketUtil 的 Graven 适配层：队列包只绕过本地事件一次。 */
public final class OpenZenPacketBypass {
    private static final Queue<Packet<?>> QUEUE = new ArrayDeque<>();

    private OpenZenPacketBypass() {
    }

    public static void send(Packet<?> packet) {
        if (packet == null || Constants.mc.getConnection() == null) return;
        PacketUtils.sendSilently(packet);
    }

    public static void queue(Packet<?> packet) {
        if (packet != null) QUEUE.add(packet);
    }

    public static void flush() {
        while (!QUEUE.isEmpty()) send(QUEUE.poll());
    }

    public static void clear() {
        QUEUE.clear();
    }
}
