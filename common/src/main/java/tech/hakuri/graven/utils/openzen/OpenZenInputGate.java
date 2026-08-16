package tech.hakuri.graven.utils.openzen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import tech.hakuri.graven.Constants;

/** 保存 OpenZen 的按键覆盖状态，并在状态机结束时恢复真实物理按键。 */
public final class OpenZenInputGate {
    private static volatile boolean neutral;
    private static volatile boolean suppressSprint;

    private OpenZenInputGate() {
    }

    public static void setNeutral(boolean value) {
        neutral = value;
    }

    public static boolean isNeutral() {
        return neutral;
    }

    public static void setSuppressSprint(boolean value) {
        suppressSprint = value;
    }

    public static boolean isSuppressSprint() {
        return suppressSprint;
    }

    public static void apply(tech.hakuri.graven.events.impl.KeyboardInputEvent event) {
        if (neutral) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
            event.setJump(false);
            event.setSprint(false);
        } else if (suppressSprint) {
            event.setSprint(false);
        }
    }

    public static void restore(KeyMapping mapping) {
        if (Constants.mc.getWindow() == null) return;
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        mapping.setDown(key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(Constants.mc.getWindow().handle(), key.getValue()) == 1
                : InputConstants.isKeyDown(Constants.mc.getWindow(), key.getValue()));
    }

    public static void restoreAll() {
        neutral = false;
        suppressSprint = false;
        if (Constants.mc.options != null) {
            restore(Constants.mc.options.keyUse);
            restore(Constants.mc.options.keySprint);
        }
    }
}
