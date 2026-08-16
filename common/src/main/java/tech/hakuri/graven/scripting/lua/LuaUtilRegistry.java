// 由 scripts/generate_graven_lib.py 自动生成，请勿手工编辑。
package tech.hakuri.graven.scripting.lua;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaUtilRegistry {
    private static final Map<String, Class<?>> BY_NAME = new LinkedHashMap<>();

    static {
        register("BlockRegistryUtils", tech.hakuri.graven.utils.world.BlockRegistryUtils.class);
        register("BlockUtils", tech.hakuri.graven.utils.world.BlockUtils.class);
        register("ChatUtils", tech.hakuri.graven.utils.player.ChatUtils.class);
        register("ClickSlotUtils", tech.hakuri.graven.utils.player.ClickSlotUtils.class);
        register("ClientUtils", tech.hakuri.graven.utils.client.ClientUtils.class);
        register("ColorUtils", tech.hakuri.graven.utils.render.ColorUtils.class);
        register("DamageUtils", tech.hakuri.graven.utils.combat.DamageUtils.class);
        register("EnchantmentUtils", tech.hakuri.graven.utils.player.EnchantmentUtils.class);
        register("HoleUtils", tech.hakuri.graven.utils.world.hole.HoleUtils.class);
        register("InteractionUtils", tech.hakuri.graven.utils.player.InteractionUtils.class);
        register("InvUtils", tech.hakuri.graven.utils.player.InvUtils.class);
        register("KeybindUtils", tech.hakuri.graven.utils.client.KeybindUtils.class);
        register("MathUtils", tech.hakuri.graven.utils.math.MathUtils.class);
        register("MoveUtils", tech.hakuri.graven.utils.player.MoveUtils.class);
        register("PacketUtils", tech.hakuri.graven.utils.network.PacketUtils.class);
        register("PlayerUtils", tech.hakuri.graven.utils.player.PlayerUtils.class);
        register("RaytraceUtils", tech.hakuri.graven.utils.rotation.RaytraceUtils.class);
        register("RotationUtils", tech.hakuri.graven.utils.rotation.RotationUtils.class);
        register("ScissorUtils", tech.hakuri.graven.utils.render.ScissorUtils.class);
        register("TimerUtils", tech.hakuri.graven.utils.timer.TimerUtils.class);
        register("WorldToScreen", tech.hakuri.graven.utils.render.WorldToScreen.class);
    }

    private LuaUtilRegistry() {
    }

    public static Class<?> resolve(String name) {
        Class<?> utilClass = BY_NAME.get(name);
        if (utilClass == null) {
            throw new IllegalArgumentException("未知 Graven util: " + name
                    + "，可用名称: " + String.join(", ", BY_NAME.keySet()));
        }
        return utilClass;
    }

    private static void register(String name, Class<?> type) {
        Class<?> previous = BY_NAME.putIfAbsent(name, type);
        if (previous != null) throw new IllegalStateException("重复 Graven util name: " + name);
    }
}
