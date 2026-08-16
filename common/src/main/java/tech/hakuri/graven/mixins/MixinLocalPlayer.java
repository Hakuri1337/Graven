package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.*;
import tech.hakuri.graven.modules.impl.movement.Velocity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer extends AbstractClientPlayer {

    @Unique
    private SendPositionEvent graven$sendPositionEvent;

    @Unique
    private boolean graven$movementPacketPhase;

    @Unique
    private boolean graven$movementPacketCancelled;

    protected MixinLocalPlayer(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.BEFORE, ordinal = 0), cancellable = true)
    private void onPreTick(CallbackInfo ci) {
        graven$movementPacketPhase = false;
        graven$movementPacketCancelled = false;
        graven$sendPositionEvent = null;
        PlayerTickEvent.Pre event = EventBus.INSTANCE.post(new PlayerTickEvent.Pre());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER, ordinal = 0))
    private void onPostTick(CallbackInfo ci) {
        EventBus.INSTANCE.post(new PlayerTickEvent.Post());
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void onPreSendPosition(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        graven$sendPositionEvent = EventBus.INSTANCE.post(new SendPositionEvent(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.onGround()));
        if (graven$sendPositionEvent.isCancelled()) {
            if (graven$movementPacketPhase) {
                graven$movementPacketCancelled = true;
            }
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z", ordinal = 0, shift = At.Shift.BEFORE))
    private void onMovementPacketPhaseStart(CallbackInfo ci) {
        graven$movementPacketPhase = true;
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;ambientSoundHandlers:Ljava/util/List;", opcode = Opcodes.GETFIELD, ordinal = 0, shift = At.Shift.BEFORE))
    private void onMovementPacketPhaseEnd(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!graven$movementPacketCancelled) {
            SendPositionEvent sent = graven$sendPositionEvent;
            EventBus.INSTANCE.post(new PostMovementPacketEvent(
                    sent == null ? player.getX() : sent.getX(),
                    sent == null ? player.getY() : sent.getY(),
                    sent == null ? player.getZ() : sent.getZ(),
                    sent == null ? player.getYRot() : sent.getYaw(),
                    sent == null ? player.getXRot() : sent.getPitch(),
                    sent == null ? player.onGround() : sent.isOnGround(),
                    player.isSprinting()
            ));
        }
        graven$movementPacketPhase = false;
        graven$movementPacketCancelled = false;
        graven$sendPositionEvent = null;
    }

    @Inject(method = "swing", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, CallbackInfo ci) {
        SwingHandEvent event = EventBus.INSTANCE.post(new SwingHandEvent());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectPosition(LocalPlayer instance, Operation<Vec3> original) {
        return new Vec3(graven$sendPositionEvent.getX(), graven$sendPositionEvent.getY(), graven$sendPositionEvent.getZ());
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double redirectGetX(LocalPlayer instance, Operation<Double> original) {
        return graven$sendPositionEvent.getX();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double redirectGetY(LocalPlayer instance, Operation<Double> original) {
        return graven$sendPositionEvent.getY();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double redirectGetZ(LocalPlayer instance, Operation<Double> original) {
        return graven$sendPositionEvent.getZ();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float redirectGetYRot(LocalPlayer instance, Operation<Float> original) {
        return graven$sendPositionEvent.getYaw();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float redirectGetXRot(LocalPlayer instance, Operation<Float> original) {
        return graven$sendPositionEvent.getPitch();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
    private boolean redirectOnGround(LocalPlayer instance, Operation<Boolean> original) {
        return graven$sendPositionEvent.isOnGround();
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void hookPushOutOfBlocks(double x, double d, CallbackInfo info) {
        if (Velocity.INSTANCE.isEnabled() && Velocity.INSTANCE.blockPush.getValue()) {
            info.cancel();
        }
    }

    @WrapOperation(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean onSlowdown(LocalPlayer localPlayer, Operation<Boolean> original) {
        SlowdownEvent event = EventBus.INSTANCE.post(new SlowdownEvent(original.call(localPlayer)));
        return event.isSlowdown();
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    private void onMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        MoveEvent event = EventBus.INSTANCE.post(new MoveEvent(delta.x, delta.y, delta.z));
        if (event.isCancelled()) {
            super.move(moverType, new Vec3(event.getX(), event.getY(), event.getZ()));
            ci.cancel();
        }
    }

}
