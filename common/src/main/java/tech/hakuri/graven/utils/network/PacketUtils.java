package tech.hakuri.graven.utils.network;

import net.minecraft.network.protocol.Packet;

import java.util.HashSet;
import java.util.Set;

import static tech.hakuri.graven.Constants.mc;

public class PacketUtils {

    public static final Set<Packet<?>> bypassedPackets = new HashSet<>();

    /**
     * 发送网络包，并使该包绕过 Graven 的发送事件。
     *
     * @param packet 待发送或过滤的网络包
     */
    public static void sendSilently(Packet<?> packet) {
        bypassedPackets.add(packet);
        mc.getConnection().send(packet);
    }

}
