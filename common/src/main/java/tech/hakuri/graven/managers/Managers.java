package tech.hakuri.graven.managers;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.managers.impl.FriendManager;
import tech.hakuri.graven.managers.impl.HealthManager;
import tech.hakuri.graven.managers.impl.NotificationManager;
import tech.hakuri.graven.managers.impl.TimerManager;
import tech.hakuri.graven.managers.impl.OpenZenTickRateController;
import tech.hakuri.graven.managers.impl.network.ClientboundPacketManager;
import tech.hakuri.graven.managers.impl.network.PacketQueueManager;
import tech.hakuri.graven.managers.impl.network.ServerboundPacketManager;
import tech.hakuri.graven.managers.impl.rotations.RotationManager;
import tech.hakuri.graven.managers.impl.rotations.SilentRotationManager;
import tech.hakuri.graven.managers.impl.rotations.SnapRotationManager;
import tech.hakuri.graven.managers.impl.sound.SoundManager;
import tech.hakuri.graven.managers.impl.target.TargetManager;
import tech.hakuri.graven.modules.impl.ClientSetting;

public class Managers {

    public static RotationManager ROTATION;
    public static TargetManager TARGET;
    public static HealthManager HEALTH;
    public static ServerboundPacketManager C2SPACKET;
    public static ClientboundPacketManager S2CPACKET;
    public static PacketQueueManager PACKET_QUEUE;
    public static FriendManager FRIEND;
    public static SoundManager SOUND;
    public static NotificationManager NOTIFICATION;
    public static TimerManager TIMER;
    public static OpenZenTickRateController OPENZEN_TICK_RATE;

    public static void initManagers() {
        switchRotationManager(ClientSetting.INSTANCE.rotationMode.getValue());
        TARGET = new TargetManager();
        HEALTH = new HealthManager();
        C2SPACKET = new ServerboundPacketManager();
        S2CPACKET = new ClientboundPacketManager();
        PACKET_QUEUE = new PacketQueueManager();
        FRIEND = new FriendManager();
        SOUND = new SoundManager();
        NOTIFICATION = new NotificationManager();
        TIMER = new TimerManager();
        OPENZEN_TICK_RATE = new OpenZenTickRateController();
    }

    public static void switchRotationManager(RotationManager.RotationMode mode) {
        RotationManager previous = ROTATION;
        RotationManager next = switch (mode) {
            case SNAP -> new SnapRotationManager();
            case SILENT -> new SilentRotationManager();
        };

        if (previous != null) {
            next.copyStateFrom(previous);
            EventBus.INSTANCE.unsubscribe(previous);
        }

        ROTATION = next;
        EventBus.INSTANCE.subscribe(next);
    }

}
