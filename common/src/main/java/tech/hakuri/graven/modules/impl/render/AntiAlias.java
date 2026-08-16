package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.AfterRender3DEvent;
import tech.hakuri.graven.graphics.shaders.FXAAShader;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public class AntiAlias extends Module {

    public static final AntiAlias INSTANCE = new AntiAlias();

    private AntiAlias() {
        super("Anti Alias", Category.RENDER);
    }

    @EventHandler
    private void onAfterRender3D(AfterRender3DEvent event) {
        FXAAShader.INSTANCE.renderMainTarget();
    }

}
