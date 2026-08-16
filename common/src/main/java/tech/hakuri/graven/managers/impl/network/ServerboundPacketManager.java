package tech.hakuri.graven.managers.impl.network;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.LevelUpdateEvent;
import tech.hakuri.graven.managers.Managers;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.LinkedBlockingQueue;

import static tech.hakuri.graven.Constants.mc;

// This is BlinkManager
public class ServerboundPacketManager {

    public ServerboundPacketManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public final LinkedBlockingQueue<Packet<?>> packets = new LinkedBlockingQueue<>();

    public boolean blinking = false;
    static boolean forceFlush;

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        forceFlush = true;
        blinking = false;
    }

    public void flush() {
        while (!packets.isEmpty()) {
            try {
                mc.getConnection().send(packets.poll());
            } catch (Exception e) {
                Managers.NOTIFICATION.error("Serverbound Packet", "failed to flush packets: " + e.getMessage());
            }
        }
    }

    public void stopBlinking() {
        blinking = false;
    }

    public void startBlinking() {
        blinking = true;
    }

    public boolean onPacketSend(Packet<?> packet) {
        if (forceFlush) {
            flush();
            forceFlush = false;
            return false;
        }

        if (!blinking) return false;

        if (mc.player == null || mc.level == null) return false;

        packets.add(packet);
        return true;
    }

}
