package tech.hakuri.graven.events.impl;

import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import tech.hakuri.graven.managers.impl.network.PacketQueueManager;
import tech.hakuri.graven.utils.network.TransferOrigin;

public final class QueuePacketEvent {

    private final @Nullable Packet<?> packet;
    private final TransferOrigin origin;
    private PacketQueueManager.Action action = PacketQueueManager.Action.FLUSH;

    public QueuePacketEvent(@Nullable Packet<?> packet, TransferOrigin origin) {
        this.packet = packet;
        this.origin = origin;
    }

    public @Nullable Packet<?> getPacket() {
        return packet;
    }

    public TransferOrigin getOrigin() {
        return origin;
    }

    public PacketQueueManager.Action getAction() {
        return action;
    }

    public void setAction(PacketQueueManager.Action action) {
        if (action != null && action.priority() > this.action.priority()) {
            this.action = action;
        }
    }
}
