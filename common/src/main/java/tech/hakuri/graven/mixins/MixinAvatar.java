package tech.hakuri.graven.mixins;

import tech.hakuri.graven.modules.impl.render.SneakTweak;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static tech.hakuri.graven.Constants.mc;

@Mixin(Avatar.class)
public class MixinAvatar {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions hookSneakTweakDefaultDimensions(EntityDimensions dimensions, Pose pose) {
        SneakTweak sneakTweak = SneakTweak.INSTANCE;
        if (
                (Avatar) (Object) this == mc.player
                        && sneakTweak.isEnabled()
                        && pose == Pose.CROUCHING
                        && mc.player.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING)
        ) {
            return dimensions.withEyeHeight(sneakTweak.modifySneakingEyeHeight(dimensions.eyeHeight()));
        }


        return dimensions;
    }

}
