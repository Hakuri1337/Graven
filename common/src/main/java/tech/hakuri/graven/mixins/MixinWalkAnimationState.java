package tech.hakuri.graven.mixins;

import tech.hakuri.graven.interfaces.WalkAnimationStateAccessor;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WalkAnimationState.class)
public class MixinWalkAnimationState implements WalkAnimationStateAccessor {

    @Shadow
    private float speedOld;

    @Shadow
    private float speed;

    @Shadow
    private float position;

    @Shadow
    private float positionScale;

    @Override
    public void graven$freeze(float position, float speed, float partialTicks) {
        this.speedOld = speed;
        this.speed = speed;
        this.positionScale = 1.0f;
        this.position = position + speed * (1.0f - partialTicks);
    }

}
