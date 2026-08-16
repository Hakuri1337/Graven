package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.EnumSetting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

public final class Teams extends Module {

    public static final Teams INSTANCE = new Teams();

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.SCOREBOARD);

    private Teams() {
        super("Teams", Category.COMBAT);
    }

    public static boolean isTeammate(Entity entity) {
        Teams module = INSTANCE;
        if (!module.isEnabled() || module.mc.player == null || !(entity instanceof Player)) return false;

        if (module.mode.is(Mode.COLOR)) {
            return entity.getTeamColor() == module.mc.player.getTeamColor();
        }
        return Objects.equals(getTeam(entity), getTeam(module.mc.player));
    }

    public static String getTeam(Entity entity) {
        if (entity == null || INSTANCE.mc.getConnection() == null) return null;
        PlayerInfo playerInfo = INSTANCE.mc.getConnection().getPlayerInfo(entity.getUUID());
        if (playerInfo == null || playerInfo.getTeam() == null) return null;
        return playerInfo.getTeam().getName();
    }

    public enum Mode {
        COLOR("Color"),
        SCOREBOARD("Scoreboard");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
