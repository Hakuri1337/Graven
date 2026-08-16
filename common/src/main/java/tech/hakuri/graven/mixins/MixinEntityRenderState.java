package tech.hakuri.graven.mixins;

import tech.hakuri.graven.interfaces.EntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements EntityRenderStateAccessor {

    @Unique
    private Entity graven$entity;

    @Override
    public Entity graven$getEntity() {
        return graven$entity;
    }

    @Override
    public void graven$setEntity(Entity entity) {
        this.graven$entity = entity;
    }

}
