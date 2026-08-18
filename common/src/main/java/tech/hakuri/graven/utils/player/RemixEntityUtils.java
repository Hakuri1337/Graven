package tech.hakuri.graven.utils.player;

import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.combat.AntiBot;
import tech.hakuri.graven.modules.impl.combat.Teams;
import tech.hakuri.graven.modules.impl.player.Targets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import static tech.hakuri.graven.Constants.mc;

public final class RemixEntityUtils {

    private RemixEntityUtils() {
    }

    public static boolean isSelected(Entity entity) {
        return isSelected(entity, true, true, true, true);
    }

    public static boolean isSelected(Entity entity, boolean checkBot, boolean checkTeams,
                                     boolean checkFriend, boolean checkSelf) {
        if (!(entity instanceof LivingEntity living)) return false;
        Targets targets = Targets.INSTANCE;
        if (!targets.invisible.getValue() && living.isInvisible()) return false;
        if ((!living.isAlive() && !targets.dead.getValue()) || living.isSpectator()) return false;

        if (living instanceof Player player) {
            if (checkSelf && player == mc.player) return false;
            if (checkFriend && Managers.FRIEND != null && Managers.FRIEND.isFriend(player)) return false;
            if (checkTeams && Teams.isTeammate(player)) return false;
            if (checkBot && AntiBot.INSTANCE.isBot(player)) return false;
            return targets.player.getValue();
        }

        if (living instanceof Villager) return targets.villager.getValue();
        if (isMob(living)) return targets.mob.getValue();
        return isAnimal(living) && targets.animal.getValue();
    }

    public static boolean isAnimal(Entity entity) {
        return entity instanceof Animal || entity instanceof Squid
                || entity instanceof IronGolem || entity instanceof Bat;
    }

    public static boolean isMob(Entity entity) {
        return entity instanceof Monster || entity instanceof Slime
                || entity instanceof Ghast || entity instanceof Shulker
                || entity instanceof EnderDragon;
    }
}
