package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.Render3DEvent;
import tech.hakuri.graven.events.impl.SendPositionEvent;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public final class ClickTP extends Module {

    public static final ClickTP INSTANCE = new ClickTP();

    private final DoubleSetting maxDistance = doubleSetting("Max Distance", 50, 5, 200, 5);
    private final DoubleSetting stepDistance = doubleSetting("Step Distance", 8, 1, 20, 1);
    private final BoolSetting renderBox = boolSetting("Render Box", true);
    private Vec3 targetVec;

    private ClickTP() {
        super("ClickTP", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        targetVec = null;
    }

    @Override
    protected void onDisable() {
        targetVec = null;
    }

    @EventHandler(priority = -900)
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || !renderBox.getValue()) return;
        HitResult hit = mc.player.pick(maxDistance.getValue(), 1.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            Render3DScheduler.INSTANCE.addFilledBox(new AABB(pos), new Color(0, 255, 200, 100));
        }
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (nullCheck()) return;
        if (targetVec == null) {
            if (!mc.options.keyPickItem.isDown()) return;
            HitResult hit = mc.player.pick(maxDistance.getValue(), 1.0F, false);
            if (hit instanceof BlockHitResult blockHit) {
                targetVec = blockHit.getBlockPos().getBottomCenter().add(0.0, 1.0, 0.0);
                mc.options.keyPickItem.setDown(false);
            }
            return;
        }

        Vec3 current = mc.player.position();
        mc.player.setDeltaMovement(Vec3.ZERO);
        double step = stepDistance.getValue();
        if (current.distanceTo(targetVec) <= step) {
            mc.player.setPos(targetVec);
            targetVec = null;
        } else {
            mc.player.setPos(current.add(targetVec.subtract(current).normalize().scale(step)));
        }
    }
}
