package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public final class ItemPhysics extends Module {

    public static final ItemPhysics INSTANCE = new ItemPhysics();

    private ItemPhysics() {
        super("ItemPhysics", Category.RENDER);
    }
}
