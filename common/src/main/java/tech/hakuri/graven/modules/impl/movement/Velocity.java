package tech.hakuri.graven.modules.impl.movement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.utils.player.EnchantmentUtils;
import tech.hakuri.graven.utils.player.PlayerUtils;
import tech.hakuri.graven.utils.timer.TimerUtils;

import java.util.Optional;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.COMBAT);
    }

    private enum Mode {
        Cancel,
        Legit,
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel);
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));

    private final SettingGroup sgExclusions = settingGroup("Exclusions");

    private final BoolSetting excludeSpearLunge = boolSetting("Exclude Spear Lunge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);
    private final BoolSetting excludeWindCharge = boolSetting("Exclude Wind Charge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);

    private final TimerUtils windChargeTimer = new TimerUtils();

    private boolean jump;

    @Override
    protected void onEnable() {
        jump = false;
        windChargeTimer.reset();
    }

    @Override
    protected void onDisable() {
        jump = false;
        windChargeTimer.reset();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;

        if (excludeWindCharge.getValue() && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            ItemStack stack = mc.player.getItemInHand(packet.getHand());
            if (stack.getItem() instanceof WindChargeItem) {
                windChargeTimer.reset();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (serverMotion.getValue()
                        && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                        && packet.id() == mc.player.getId()) {
                    if (!shouldExcludeMotion(packet)) {
                        event.cancel();
                    }
                    return;
                }

                if (explosion.getValue()
                        && event.getPacket() instanceof ClientboundExplodePacket packet
                        && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())) {
                    if (shouldExcludeExplosion(packet)) {
                        return;
                    }
                    event.setPacket(new ClientboundExplodePacket(
                            packet.center(),
                            packet.radius(),
                            packet.blockCount(),
                            Optional.empty(),
                            packet.explosionParticle(),
                            packet.explosionSound(),
                            packet.blockParticles()
                    ));
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                        && packet.id() == mc.player.getId()) {
                    jump = true;
                }
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (nullCheck()) return;

        if (jump) {
            if (mc.player.onGround() && mc.player.isMoving()) {
                mc.player.input.makeJump();
            }
            jump = false;
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        jump = false;
        windChargeTimer.reset();
    }

    private boolean shouldExcludeMotion(ClientboundSetEntityMotionPacket packet) {
        return excludeSpearLunge.getValue() && isSpearLungeMotion(packet);
    }

    private boolean shouldExcludeExplosion(ClientboundExplodePacket packet) {
        return excludeWindCharge.getValue() && isWindChargeExplosion(packet);
    }

    private boolean isSpearLungeMotion(ClientboundSetEntityMotionPacket packet) {
        if (!isSpearWithLunge(mc.player.getMainHandItem())) return false;
        if (!mc.options.keyAttack.isDown()) return false;

        Vec3 velocity = packet.movement();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal < 0.15) return false;

        Vec3 look = mc.player.getLookAngle();
        double dot = velocity.x * look.x + velocity.z * look.z;
        return dot > 0;
    }

    private boolean isWindChargeExplosion(ClientboundExplodePacket packet) {
        if (windChargeTimer.passedMillise(3000)) return false;

        double distance = packet.center().distanceTo(mc.player.position());
        if (distance > 12.0) return false;
        if (packet.radius() > 3.0f) return false;

        return packet.playerKnockback().isPresent();
    }

    private boolean isSpearWithLunge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.PIERCING_WEAPON)
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.LUNGE) > 0;
    }

}
