package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.DoubleSetting;

public class AspectRatio extends Module {

    public static final AspectRatio INSTANCE = new AspectRatio();

    private AspectRatio() {
        super("Aspect Ratio", Category.RENDER);
    }

    public final DoubleSetting ratio = doubleSetting("Ratio", 1.78, 0.1, 8.0, 0.1);

}
