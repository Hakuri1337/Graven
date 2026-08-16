package tech.hakuri.graven.managers.impl;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static tech.hakuri.graven.Constants.mc;

public class HealthManager {

    public HealthManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final Map<String, Integer> scoreboardHealth = new ConcurrentHashMap<>();

    public float getHealth(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            String name = livingEntity.getName().getString();
            Integer scoreHealth = scoreboardHealth.get(name);
            if (scoreHealth != null && scoreHealth > 0) {
                return scoreHealth;
            }
            return livingEntity.getHealth() + livingEntity.getAbsorptionAmount();
        }
        return 0f;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundSetScorePacket packet) {
            String objectiveName = packet.objectiveName();
            if (isHealthObjective(objectiveName)) {
                scoreboardHealth.put(packet.owner(), packet.score());
            }
        }
    }

    private boolean isHealthObjective(String objectiveName) {
        return "belowHealth".equalsIgnoreCase(objectiveName) || "health".equalsIgnoreCase(objectiveName);
    }

}
