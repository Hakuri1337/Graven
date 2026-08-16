package tech.hakuri.graven.modules.impl.combat;

import net.minecraft.network.protocol.common.ClientboundPingPacket;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PostMovementPacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.RespawnEvent;
import tech.hakuri.graven.events.impl.StrafeEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.impl.combat.antikb.AntiKBMode;
import tech.hakuri.graven.modules.impl.combat.antikb.JumpResetMode;
import tech.hakuri.graven.modules.impl.combat.antikb.MixMode;
import tech.hakuri.graven.modules.impl.combat.antikb.NoXZMode;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.openzen.OpenZenInputGate;

/** OpenZen AntiKB 分发器，策略边界与原实现保持独立。 */
public final class AntiKB extends Module {
    public static final AntiKB INSTANCE = new AntiKB();

    public enum Mode { NoXZ, JumpReset, Mix }

    public final EnumSetting<Mode> mode = enumSetting("Mode", Mode.NoXZ);
    public final BoolSetting rotate = boolSetting("Rotate", false, () -> mode.is(Mode.JumpReset) || mode.is(Mode.Mix));
    public final BoolSetting followDirection = boolSetting("Follow Direction", false, () -> mode.is(Mode.JumpReset));
    public final BoolSetting tryAttack = boolSetting("Try Attack", false, () -> mode.is(Mode.Mix));
    public final BoolSetting movementOverride = boolSetting("Movement Override", false, () -> mode.is(Mode.Mix));
    public final IntSetting rotateTicks = intSetting("Rotate Ticks", 12, 3, 20, 1,
            () -> mode.is(Mode.JumpReset) && (rotate.getValue() || followDirection.getValue()));
    public final IntSetting attackAmount = intSetting("Attack Amount", 5, 1, 20, 1, () -> mode.is(Mode.NoXZ));
    public final BoolSetting instantAttack = boolSetting("Instant Attack", false, () -> mode.is(Mode.NoXZ));
    public final BoolSetting sprintStateCheck = boolSetting("Sprint State Check", true, () -> mode.is(Mode.NoXZ));

    private final NoXZMode noXZ = new NoXZMode(this);
    private final JumpResetMode jumpReset = new JumpResetMode(this);
    private final MixMode mix = new MixMode(this);

    private AntiKB() {
        super("Anti KB", Category.COMBAT);
    }

    private AntiKBMode current() {
        return switch (mode.getValue()) {
            case NoXZ -> noXZ;
            case JumpReset -> jumpReset;
            case Mix -> mix;
        };
    }

    public boolean isSuspending() {
        return isEnabled() && current().isSuspending();
    }

    @Override
    protected void onEnable() {
        noXZ.disable();
        jumpReset.disable();
        mix.disable();
        current().enable();
    }

    @Override
    protected void onDisable() {
        noXZ.disable();
        jumpReset.disable();
        mix.disable();
        OpenZenInputGate.restoreAll();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        current().receive(event);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        current().send(event);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        current().tick(event);
    }

    @EventHandler
    private void onPostMovement(PostMovementPacketEvent event) {
        current().postMovement(event);
    }

    @EventHandler(priority = 140)
    private void onKeyboardInput(KeyboardInputEvent event) {
        current().input(event);
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        current().strafe(event);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        onDisable();
        OpenZenInputGate.restoreAll();
    }

    @EventHandler
    private void onRespawn(RespawnEvent event) {
        current().disable();
    }
}
