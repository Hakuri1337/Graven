package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.BlockCollisionEvent;
import tech.hakuri.graven.events.impl.BlockShapeEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public class MixinBlockCollisions {

    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState hookComputeNext(BlockGetter instance, BlockPos blockPos, Operation<BlockState> original) {
        BlockCollisionEvent event = EventBus.INSTANCE.post(new BlockCollisionEvent(original.call(instance, blockPos), blockPos));
        return event.getState();
    }

    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape hookCollisionShape(CollisionContext context, BlockState state, CollisionGetter level,
                                          BlockPos pos, Operation<VoxelShape> original) {
        VoxelShape originalShape = original.call(context, state, level, pos);
        return EventBus.INSTANCE.post(new BlockShapeEvent(state, pos.immutable(), originalShape)).getShape();
    }

}
