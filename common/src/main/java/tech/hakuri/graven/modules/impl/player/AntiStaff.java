package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameJoinedEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.utils.player.ChatUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AntiStaff extends Module {

    public static final AntiStaff INSTANCE = new AntiStaff();

    private static final String STAFF_LIST_B64 = "QuermeaQnOaXoOmHj+Wfn+mbqizkuInlm73mnYAs56yZ5qmZLE1lbmdDaGVuMzg4NCxBbmRyZXdrcmlzdCxGaWE5LOaeq+iQp+ael+eEtiznu7/osYbkuYPjgZXjgpMs5oqW6Z+z5Li25bCP5YyqLOaKlumfs19hd2Hpqazljp8sTW5hbUxlb18s5Lit5LqM5bCR5bm0REws5p6V5LiK5Lmm5Li25aGR5pyb5pyILElhbU1vbGluY2VuXywsQ29GdV9fLOaWl+aImOiDnOS9myzlj6rnjqnmlqXlgJks5p6V5LiK5Lmm5Li26Zuq5aScLGFpeXVraSxDYW5keUFwb3N0bGUsY2h1bnlpMSzmtYHlvbHlj6rkvJrlmKTlmKTlmKQscXRlc2RmXzY3NCxxeHRtbGM5OSxTa3lmb3ks56We5Z2R5LmL6YCXLOWco+S4iuiNo+iAgDIzMyzlsI/lhpvlkJvkuLblpKnkvb/kuYvnv7ws5p6V5LiK5Lmm5Li25YKy5a+SLF93aW5uZXJfLFNreV9ZdWFueGlhbw==";
    private static final Set<String> STAFF_NAMES = decodeStaffNames();

    private boolean exitTriggered;

    private AntiStaff() {
        super("Anti Staff", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        exitTriggered = false;
    }

    @Override
    protected void onDisable() {
        exitTriggered = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        exitTriggered = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        exitTriggered = false;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (exitTriggered || mc.level == null) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerInfoUpdatePacket infoUpdate
                && infoUpdate.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoUpdate.entries()) {
                if (entry.profile() != null && isStaffName(entry.profile().name())) {
                    exitGame();
                    return;
                }
                if (entry.displayName() != null && isStaffName(entry.displayName().getString())) {
                    exitGame();
                    return;
                }
            }
            return;
        }

        if (packet instanceof ClientboundAddEntityPacket addEntity
                && addEntity.getType() == EntityType.PLAYER) {
            String name = findPlayerName(addEntity.getUUID(), addEntity.getId());
            if (name != null && isStaffName(name)) {
                exitGame();
            }
        }
    }

    private String findPlayerName(UUID uuid, int entityId) {
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(uuid) != null) {
            var info = mc.getConnection().getPlayerInfo(uuid);
            if (info.getProfile() != null) return info.getProfile().name();
        }
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        return entity == null ? null : entity.getName().getString();
    }

    private boolean isStaffName(String name) {
        return name != null && !name.isEmpty() && STAFF_NAMES.contains(name);
    }

    private void exitGame() {
        if (exitTriggered || mc.player == null || mc.player.connection == null) return;
        exitTriggered = true;
        ChatUtils.addChatMessage(GravenTranslations.AntiStaff.DETECTED.getTranslatedName());
        mc.player.connection.sendCommand("hub");
    }

    private static Set<String> decodeStaffNames() {
        String decoded = new String(Base64.getDecoder().decode(STAFF_LIST_B64), StandardCharsets.UTF_8);
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(decoded.split(",", -1))));
    }
}
