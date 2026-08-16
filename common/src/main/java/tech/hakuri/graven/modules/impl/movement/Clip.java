package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.Render2DEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

public final class Clip extends Module {

    public static final Clip INSTANCE = new Clip();

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Fancy);

    private final DoubleSetting oldHorizontal = doubleSetting(
            "Old Horizontal", 0.0, -10.0, 10.0, 0.1, () -> mode.is(Mode.Old));
    private final DoubleSetting oldVertical = doubleSetting(
            "Old Vertical", 5.0, -10.0, 10.0, 0.1, () -> mode.is(Mode.Old));
    private final BoolSetting resetVelocity = boolSetting(
            "Reset Velocity", true, () -> mode.is(Mode.Old));

    private final IntSetting horizontal = intSetting(
            "Horizontal", 0, 0, 6, 1, () -> mode.is(Mode.Fancy));
    private final IntSetting vertical = intSetting(
            "Vertical", 5, 0, 6, 1, () -> mode.is(Mode.Fancy));
    private final BoolSetting requiresStandOn = boolSetting(
            "Requires Stand On", true, () -> mode.is(Mode.Fancy));

    private final Set<Direction> possibleClipDirections = EnumSet.noneOf(Direction.class);
    private int cooldownTicks;

    private Clip() {
        super("Clip", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        cooldownTicks = 0;
        possibleClipDirections.clear();
        if (nullCheck() || !mode.is(Mode.Old)) return;

        double yaw = Math.toRadians(mc.player.getYRot());
        double x = -Math.sin(yaw) * oldHorizontal.getValue();
        double z = Math.cos(yaw) * oldHorizontal.getValue();
        mc.player.setPos(mc.player.getX() + x, mc.player.getY() + oldVertical.getValue(), mc.player.getZ() + z);
        if (resetVelocity.getValue()) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        }
        setEnabled(false);
    }

    @Override
    protected void onDisable() {
        cooldownTicks = 0;
        possibleClipDirections.clear();
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !mode.is(Mode.Fancy)) return;
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        possibleClipDirections.clear();
        if (Flight.INSTANCE.isEnabled()) return;

        tryClip(Direction.UP, vertical.getValue(), ignored -> possibleClipDirections.add(Direction.UP));
        tryClip(Direction.DOWN, vertical.getValue(), ignored -> possibleClipDirections.add(Direction.DOWN));

        Direction movementDirection = movementDirection();
        if (movementDirection == null) return;

        int clipLength = movementDirection.getAxis() == Direction.Axis.Y
                ? vertical.getValue()
                : horizontal.getValue();
        tryClip(movementDirection, clipLength, blockPos -> {
            mc.player.setPos(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
            Managers.NOTIFICATION.success(
                    getTranslatedName(), GravenTranslations.Clip.WHOOSH.getTranslatedName());
        });
        cooldownTicks = 5;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (mode.is(Mode.Old) || possibleClipDirections.isEmpty()) return;

        StringBuilder directionText = new StringBuilder("[ ");
        if (possibleClipDirections.contains(Direction.UP)) directionText.append('\u25B2');
        if (possibleClipDirections.contains(Direction.DOWN)) directionText.append('\u25BC');
        directionText.append(" ]");

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        graphics.text(
                mc.font,
                directionText.toString(),
                graphics.guiWidth() / 2 + 10,
                graphics.guiHeight() / 2 - mc.font.lineHeight / 2 + 1,
                0xFFFFFFFF,
                true);
    }

    private Direction movementDirection() {
        if (mc.player.horizontalCollision) {
            Direction facing = mc.player.getDirection();
            if (mc.options.keyUp.isDown()) return facing;
            if (mc.options.keyDown.isDown()) return facing.getOpposite();
            if (mc.options.keyLeft.isDown()) return facing.getClockWise().getOpposite();
            if (mc.options.keyRight.isDown()) return facing.getClockWise();
            return null;
        }

        if (mc.options.keyShift.isDown()) return Direction.DOWN;
        if (mc.options.keyJump.isDown()) return Direction.UP;
        return null;
    }

    private void tryClip(Direction movementDirection, int length, Consumer<BlockPos> clip) {
        if (length == 0) return;

        boolean wallBetween = false;
        BlockPos.MutableBlockPos position = mc.player.blockPosition().mutable();
        for (int i = 0; i < length; i++) {
            position.move(movementDirection);
            if (isPossibleLocation(
                    position,
                    requiresStandOn.getValue() && movementDirection != Direction.UP)) {
                if (wallBetween) {
                    clip.accept(position.immutable());
                    return;
                }
            } else {
                wallBetween = true;
            }
        }
    }

    private boolean isPossibleLocation(BlockPos blockPos, boolean requiresStandOn) {
        if (requiresStandOn) {
            BlockPos below = blockPos.below();
            if (!mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)) return false;
        }
        return mc.level.getBlockState(blockPos).isAir()
                && mc.level.getBlockState(blockPos.above()).isAir();
    }

    private enum Mode {
        Fancy,
        Old
    }
}
