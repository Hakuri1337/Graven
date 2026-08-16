package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameJoinedEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.player.ChatUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiBot extends Module {

    public static final AntiBot INSTANCE = new AntiBot();

    private final IntSetting newPlayerTimeout = intSetting("Respawn Time", 2500, 0, 10000, 100);
    private final BoolSetting debug = boolSetting("Debug", true);

    private final Map<UUID, String> suspectNames = new ConcurrentHashMap<>();
    private final Map<Integer, String> confirmedBotNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suspectJoinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerAddTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingEntityIds = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> entityUuids = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> confirmedBotIds = ConcurrentHashMap.newKeySet();

    private AntiBot() {
        super("Anti Bot", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        clearState();
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    @Override
    protected void resetCustomState() {
        clearState();
    }

    public boolean isBot(Entity entity) {
        return isEnabled() && entity != null && confirmedBotIds.contains(entity.getId());
    }

    public boolean isBedWarsBot(Entity entity) {
        if (!isEnabled() || entity == null) return false;
        if (entity.getId() >= 1000000000 || entity.getId() <= -1) return true;
        if (entity.getName() == null || entity.getScoreboardName().isEmpty()) return true;
        long timeout = newPlayerTimeout.getValue();
        Long addTime = playerAddTimes.get(entity.getUUID());
        return timeout >= 1L && addTime != null && System.currentTimeMillis() - addTime < timeout;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.level == null) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerInfoUpdatePacket infoUpdate) {
            if (!infoUpdate.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoUpdate.entries()) {
                UUID uuid = entry.profileId();
                playerAddTimes.put(uuid, System.currentTimeMillis());

                if (entry.displayName() != null) {
                    String profileName = entry.profile() == null ? "" : entry.profile().name();
                    String displayName = entry.displayName().getString();
                    debugMarker(profileName, displayName, "debug marker 11", "汤圆来了11");
                    debugMarker(profileName, displayName, "debug marker 1", "汤圆来了1");
                }

                if (entry.displayName() == null
                        || !entry.displayName().getSiblings().isEmpty()
                        || entry.gameMode() != GameType.SURVIVAL) {
                    continue;
                }
                String suspectName = entry.displayName().getString();
                suspectJoinTimes.put(uuid, System.currentTimeMillis());
                suspectNames.put(uuid, suspectName);
                Integer entityId = pendingEntityIds.remove(uuid);
                if (entityId != null) confirmBot(uuid, entityId, suspectName);
            }
            return;
        }

        if (packet instanceof ClientboundPlayerInfoRemovePacket removeInfo) {
            for (UUID uuid : removeInfo.profileIds()) {
                playerAddTimes.remove(uuid);
                suspectJoinTimes.remove(uuid);
                suspectNames.remove(uuid);
                Integer entityId = pendingEntityIds.remove(uuid);
                if (entityId != null) entityUuids.remove(entityId, uuid);
            }
            return;
        }

        if (packet instanceof ClientboundAddEntityPacket addEntity
                && addEntity.getType() == EntityType.PLAYER) {
            entityUuids.put(addEntity.getId(), addEntity.getUUID());
            UUID uuid = addEntity.getUUID();
            String suspectName = suspectNames.get(uuid);
            if (suspectName != null && suspectJoinTimes.containsKey(uuid)) {
                confirmBot(uuid, addEntity.getId(), suspectName);
            } else {
                pendingEntityIds.put(uuid, addEntity.getId());
            }
            return;
        }

        if (packet instanceof ClientboundAnimatePacket animate && animate.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND) {
            UUID uuid = entityUuids.get(animate.getId());
            if (uuid != null) playerAddTimes.remove(uuid);
            else if (mc.level.getEntity(animate.getId()) != null) playerAddTimes.remove(mc.level.getEntity(animate.getId()).getUUID());
            return;
        }

        if (packet instanceof ClientboundRemoveEntitiesPacket removeEntities) {
            for (int entityId : removeEntities.getEntityIds()) {
                String botName = confirmedBotNames.remove(entityId);
                confirmedBotIds.remove(entityId);
                UUID uuid = entityUuids.remove(entityId);
                if (uuid != null) pendingEntityIds.remove(uuid, entityId);
                if (botName != null && debug.getValue()) {
                    ChatUtils.addChatMessage(formatMessage(GravenTranslations.AntiBot.BOT_REMOVED, botName));
                }
            }
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : suspectJoinTimes.entrySet()) {
            if (now - entry.getValue() <= 500L) continue;
            String name = suspectNames.remove(entry.getKey());
            suspectJoinTimes.remove(entry.getKey(), entry.getValue());
            if (debug.getValue()) {
                ChatUtils.addChatMessage(formatMessage(GravenTranslations.AntiBot.FAKE_STAFF_DETECTED, name));
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clearState();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearState();
    }

    private void confirmBot(UUID uuid, int entityId, String name) {
        suspectJoinTimes.remove(uuid);
        suspectNames.remove(uuid);
        pendingEntityIds.remove(uuid, entityId);
        confirmedBotNames.put(entityId, name);
        confirmedBotIds.add(entityId);
        if (debug.getValue()) {
            ChatUtils.addChatMessage(formatMessage(GravenTranslations.AntiBot.BOT_DETECTED, name));
        }
    }

    private void debugMarker(String profileName, String displayName, String key, String fallback) {
        if (debug.getValue() && (profileName.contains("Sky_Yuanxiao") || displayName.contains("Sky_Yuanxiao"))) {
            ChatUtils.addChatMessage(translateMessage(key, fallback));
        }
    }

    private String formatMessage(tech.hakuri.graven.assets.i18n.TranslateComponent component, String name) {
        String template = component.getTranslatedName();
        return template.replace("{name}", String.valueOf(name));
    }

    private String translateMessage(String key, String fallback) {
        return switch (key) {
            case "debug marker 11" -> GravenTranslations.AntiBot.DEBUG_MARKER_11.getTranslatedName();
            case "debug marker 1" -> GravenTranslations.AntiBot.DEBUG_MARKER_1.getTranslatedName();
            default -> fallback;
        };
    }

    private void clearState() {
        suspectNames.clear();
        confirmedBotNames.clear();
        suspectJoinTimes.clear();
        playerAddTimes.clear();
        pendingEntityIds.clear();
        entityUuids.clear();
        confirmedBotIds.clear();
    }
}
