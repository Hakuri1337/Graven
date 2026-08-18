package tech.hakuri.graven.utils.asaka.grimvelocity;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * Manages per-tick packet-only attacks for Grim-mode velocity reduction.
 * <p>
 * Tracks whether any module has attacked via {@link #attackByPacket(Entity)}
 * this tick, and provides a lock mechanism to prevent double attacks.
 * <p>
 * Used by {@code Velocity(Grim)} to trigger AttackSlow (client *0.6 knockback
 * reduction) without going through {@code MultiPlayerGameMode.attack()},
 * which would call {@code Player.attack()} client-side and trigger unwanted
 * side effects.
 */
public class FightManager {

    private static final Minecraft mc = Minecraft.getInstance();
    private static int currentTick = 0;
    private static boolean tickAttacked = false;
    private static boolean tickLocked = false;

    /**
     * Returns true if {@link #attackByPacket(Entity)} has already been called
     * this tick. This allows other components (e.g. KillAura) to piggyback on
     * the AttackSlow without double-applying the client *0.6.
     */
    public static boolean hasAttackedThisTick() {
        checkTick();
        return tickAttacked;
    }

    /**
     * Claims the per-tick attack slot. Returns true if the slot was
     * successfully claimed, false if already claimed this tick.
     * <p>
     * This prevents multiple AttackSlow attempts in the same tick.
     */
    public static boolean attackAndLock() {
        checkTick();
        if (tickLocked) return false;
        tickLocked = true;
        return true;
    }

    /**
     * Sends a packet-only attack ({@link ServerboundAttackPacket} +
     * {@link ServerboundSwingPacket}) without calling
     * {@code Player.attack()} on the client side.
     * <p>
     * This triggers server-side attack processing while avoiding the
     * client-side knockback and sprint-stop from
     * {@code MultiPlayerGameMode.attack()}.
     */
    public static void attackByPacket(Entity entity) {
        attackByPacket(entity, false);
    }

    /**
     * Sends a packet-only attack with configurable packet order.
     *
     * @param entity    the entity to attack
     * @param swingFirst if true, send swing before attack packet
     */
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

    /**
     * Called by MixinMultiPlayerGameMode to record that a vanilla
     * {@code MultiPlayerGameMode.attack()} call was made this tick.
     * This allows the Grim mode to detect when KillAura (or similar)
     * has already triggered the client *0.6.
     */
    public static void markVanillaAttack() {
        checkTick();
        tickAttacked = true;
        tickLocked = true;
    }

    private static void checkTick() {
        int gameTick = mc.player != null ? mc.player.tickCount : 0;
        if (gameTick != currentTick) {
            currentTick = gameTick;
            tickAttacked = false;
            tickLocked = false;
        }
    }

    /**
     * Resets all tracked state. Called on world disconnect.
     */
    public static void reset() {
        currentTick = -1;
        tickAttacked = false;
        tickLocked = false;
    }
}
