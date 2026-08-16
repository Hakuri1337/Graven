package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public class MultiTask extends Module {

    public static final MultiTask INSTANCE = new MultiTask();

    private MultiTask() {
        super("Multi Task", Category.PLAYER);
    }

}
