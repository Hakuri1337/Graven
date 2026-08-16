package tech.hakuri.graven.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * 协调每个游戏 tick 的攻击占用状态，并提供不触发客户端攻击副作用的发包攻击。
 */
public final class FightManager {

    private static final Minecraft mc = Minecraft.getInstance();
    private static int currentTick;
    private static boolean tickAttacked;
    private static boolean tickLocked;

    private FightManager() {
    }

    public static boolean hasAttackedThisTick() {
        checkTick();
        return tickAttacked;
    }

    public static boolean attackAndLock() {
        checkTick();
        if (tickLocked) return false;
        tickLocked = true;
        return true;
    }

    public static void attackByPacket(Entity entity) {
        attackByPacket(entity, false);
    }

    public static void attackByPacket(Entity entity, boolean swingFirst) {
        if (mc.getConnection() == null) return;
        if (swingFirst) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
        } else {
            mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        tickAttacked = true;
        tickLocked = true;
    }

    public static void markVanillaAttack() {
        checkTick();
        tickAttacked = true;
        tickLocked = true;
    }

    public static void reset() {
        currentTick = -1;
        tickAttacked = false;
        tickLocked = false;
    }

    private static void checkTick() {
        int gameTick = mc.player != null ? mc.player.tickCount : 0;
        if (gameTick != currentTick) {
            currentTick = gameTick;
            tickAttacked = false;
            tickLocked = false;
        }
    }
}
