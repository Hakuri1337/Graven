package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public class JumpReset extends Module {

    public static final JumpReset INSTANCE = new JumpReset();

    private JumpReset() {
        super("Jump Reset", Category.MOVEMENT);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.player.onGround() && mc.player.hurtTime == 9) {
            event.setJump(true);
        }
    }

}
