package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public class NoRotate extends Module {

    public static final NoRotate INSTANCE = new NoRotate();

    private NoRotate() {
        super("No Rotate", Category.PLAYER);
    }

}
