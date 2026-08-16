package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.Render2DEvent;
import tech.hakuri.graven.graphics.LuminRenderSystem;
import tech.hakuri.graven.gui.theme.GravenUiTheme;
import tech.hakuri.graven.gui.theme.OpalHudStyle;
import tech.hakuri.graven.gui.utils.UiCoordinateMapper;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.impl.ClientSetting;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.ColorSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.render.WorldToScreen;
import tech.hakuri.graven.utils.player.EnchantmentUtils;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ESP2D extends Module {

    public static final ESP2D INSTANCE = new ESP2D();

    private ESP2D() {
        super("ESP 2D", Category.RENDER);
    }

    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting friends = boolSetting("Friends", true);
    private final BoolSetting creatures = boolSetting("Creatures", false);
    private final BoolSetting monsters = boolSetting("Monsters", false);
    private final BoolSetting ambients = boolSetting("Ambients", false);
    private final BoolSetting others = boolSetting("Others", false);
    private final BoolSetting renderHealth = boolSetting("Render Health", true);
    private final DoubleSetting healthBarWidth = doubleSetting("Health Bar Width", 2.0, 0.5, 6.0, 0.5, renderHealth::getValue);
    private final BoolSetting healthBarOutline = boolSetting("Health Bar Outline", true, renderHealth::getValue);
    private final DoubleSetting healthBarOutlineWidth = doubleSetting("Health Bar Outline Width", 1.0, 0.5, 3.0, 0.5, () -> renderHealth.getValue() && healthBarOutline.getValue());
    private final BoolSetting renderBox = boolSetting("Render Box", true);
    private final BoolSetting boxOutline = boolSetting("Box Outline", true, renderBox::getValue);
    private final BoolSetting opalNameTags = boolSetting("Opal Name Tags", true, OpalHudStyle::active);
    private final BoolSetting opalName = boolSetting("Opal Name", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalHealth = boolSetting("Opal Health", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalDistance = boolSetting("Opal Distance", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalEquipment = boolSetting("Opal Equipment", false,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalSneaking = boolSetting("Opal Sneaking", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalStrength = boolSetting("Opal Strength", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalInvisible = boolSetting("Opal Invisible", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());
    private final BoolSetting opalBlocking = boolSetting("Opal Blocking", true,
            () -> OpalHudStyle.active() && opalNameTags.getValue());

    private final ColorSetting playersColor = colorSetting("Players Color", new Color(0xFF9200), false);
    private final ColorSetting friendsColor = colorSetting("Friends Color", new Color(0x30FF00), false);
    private final ColorSetting creaturesColor = colorSetting("Creatures Color", new Color(0xA0A4A6), false);
    private final ColorSetting monstersColor = colorSetting("Monsters Color", new Color(0xFF0000), false);
    private final ColorSetting ambientsColor = colorSetting("Ambients Color", new Color(0x7B00FF), false);
    private final ColorSetting othersColor = colorSetting("Others Color", new Color(0xFF0062), false);
    private final ColorSetting healthColor = colorSetting("Health Color", new Color(0x2FFF00), false, renderHealth::getValue);

    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private static final float OPAL_BOX_THICKNESS = 0.5f;
    private static final float OPAL_TAG_SCALE = 0.40f;
    private static final float OPAL_ICON_SCALE = 0.42f;
    private static final Map<String, String> OPAL_ENCHANTMENT_NAMES = Map.ofEntries(
            Map.entry("protection", "Pr"), Map.entry("fire_protection", "Fp"),
            Map.entry("feather_falling", "Ff"), Map.entry("blast_protection", "Bp"),
            Map.entry("projectile_protection", "Pp"), Map.entry("respiration", "Re"),
            Map.entry("aqua_affinity", "Aa"), Map.entry("thorns", "Th"),
            Map.entry("depth_strider", "Ds"), Map.entry("frost_walker", "Fw"),
            Map.entry("binding_curse", "Bc"), Map.entry("soul_speed", "Ss"),
            Map.entry("swift_sneak", "Sn"), Map.entry("sharpness", "Sh"),
            Map.entry("smite", "Sm"), Map.entry("bane_of_arthropods", "BoA"),
            Map.entry("knockback", "Kb"), Map.entry("fire_aspect", "Fa"),
            Map.entry("looting", "Lo"), Map.entry("sweeping_edge", "Sw"),
            Map.entry("efficiency", "Ef"), Map.entry("silk_touch", "St"),
            Map.entry("unbreaking", "Un"), Map.entry("fortune", "Fo"),
            Map.entry("power", "Po"), Map.entry("punch", "Pu"),
            Map.entry("flame", "Fl"), Map.entry("infinity", "In"),
            Map.entry("luck_of_the_sea", "Lu"), Map.entry("lure", "Lr"),
            Map.entry("loyalty", "Ly"), Map.entry("impaling", "Ip"),
            Map.entry("riptide", "Ri"), Map.entry("channeling", "Ch"),
            Map.entry("multishot", "Mu"), Map.entry("quick_charge", "Qc"),
            Map.entry("piercing", "Pi"), Map.entry("mending", "Me"),
            Map.entry("vanishing_curse", "Vc"));

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || mc.options.hideGui) return;

        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();

        List<Projection> projections = collectProjections(partialTick, screenWidth, screenHeight);
        UiTree tree = UiTree.build(scope -> {
            for (Projection projection : projections) {
                if (OpalHudStyle.active()) {
                    drawOpalProjection(scope, projection);
                } else {
                    drawDefaultProjection(scope, projection);
                }
            }
        });

        if (tree.nodeCount() > 0) {
            runtime.render(scene(runtime), UiLayer.CONTENT, tree);
        }
        if (OpalHudStyle.active() && opalEquipment.getValue()) {
            renderOpalEquipment(event.getGuiGraphics(), projections);
        }
    }

    private List<Projection> collectProjections(float partialTick, float screenWidth, float screenHeight) {
        List<Projection> result = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity) || !shouldRender(livingEntity)) continue;
            Vec3 renderPosition = livingEntity.getPosition(partialTick);
            AABB box = livingEntity.getBoundingBox().move(renderPosition.subtract(livingEntity.position()));
            float x = Float.POSITIVE_INFINITY;
            float y = Float.POSITIVE_INFINITY;
            float endX = Float.NEGATIVE_INFINITY;
            float endY = Float.NEGATIVE_INFINITY;
            int projectedVertices = 0;

            for (int vertex = 0; vertex < 8; vertex++) {
                Vec3 worldVertex = new Vec3(
                        (vertex & 1) == 0 ? box.minX : box.maxX,
                        (vertex & 2) == 0 ? box.minY : box.maxY,
                        (vertex & 4) == 0 ? box.minZ : box.maxZ);
                Vector3f projected = WorldToScreen.calcWorld2Screen(worldVertex);
                if (projected == null || !Float.isFinite(projected.x) || !Float.isFinite(projected.y)) continue;
                projectedVertices++;
                x = Math.min(x, projected.x);
                y = Math.min(y, projected.y);
                endX = Math.max(endX, projected.x);
                endY = Math.max(endY, projected.y);
            }

            if (projectedVertices != 8 || !Float.isFinite(x) || endX <= x || endY <= y
                    || endX < 0.0f || endY < 0.0f || x > screenWidth || y > screenHeight) continue;
            result.add(new Projection(livingEntity, x, y, endX, endY));
        }
        return result;
    }

    private void drawDefaultProjection(UiTree.Scope scope, Projection projection) {
        float x = projection.x();
        float y = projection.y();
        float endX = projection.endX();
        float endY = projection.endY();
        if (renderBox.getValue()) {
            if (boxOutline.getValue()) {
                scope.rect(x - 1.0f, y, 1.5f, endY - y + 0.5f, Color.BLACK);
                scope.rect(x - 1.0f, y - 0.5f, endX - x + 1.5f, 1.0f, Color.BLACK);
                scope.rect(endX - 1.0f, y, 1.5f, endY - y + 0.5f, Color.BLACK);
                scope.rect(x - 1.0f, endY - 1.0f, endX - x + 1.5f, 1.5f, Color.BLACK);
            }
            drawSolidBox(scope, x, y, endX, endY, getEntityColor(projection.entity()));
        }
        if (renderHealth.getValue()) {
            drawHealthBar(scope, projection.entity(), x, y, endY);
        }
    }

    private void drawOpalProjection(UiTree.Scope scope, Projection projection) {
        LivingEntity entity = projection.entity();
        float x = projection.x();
        float y = projection.y();
        float endX = projection.endX();
        float endY = projection.endY();
        Color color = getEntityColor(entity);

        if (renderBox.getValue()) {
            if (boxOutline.getValue()) {
                drawBoxOutline(scope, x, y, endX, endY, OPAL_BOX_THICKNESS * 3.0f, Color.BLACK);
            }
            drawBoxOutline(scope, x, y, endX, endY, OPAL_BOX_THICKNESS, color);
        }
        if (renderHealth.getValue()) {
            drawOpalHealthBar(scope, entity, x, y, endY);
        }
        if (opalNameTags.getValue()) {
            List<TagElement> elements = opalTagElements(entity);
            drawOpalTags(scope, projection, elements);
            if (opalEquipment.getValue()) {
                drawOpalEnchantmentLabels(scope, projection, !elements.isEmpty());
            }
        }
    }

    private void drawBoxOutline(UiTree.Scope scope, float x, float y, float endX, float endY,
                                float thickness, Color color) {
        float half = thickness * 0.5f;
        scope.rect(x - half, y - half, endX - x + thickness, thickness, color);
        scope.rect(x - half, endY - half, endX - x + thickness, thickness, color);
        scope.rect(x - half, y + half, thickness, endY - y - thickness, color);
        scope.rect(endX - half, y + half, thickness, endY - y - thickness, color);
    }

    private void drawOpalHealthBar(UiTree.Scope scope, LivingEntity entity, float x, float y, float endY) {
        float height = endY - y;
        if (height <= 0.0f) return;
        float health = Managers.HEALTH.getHealth(entity);
        float maxHealth = Math.max(1.0f, entity.getMaxHealth());
        float healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        float fillY = endY - height * healthRatio;
        float barX = x - OPAL_BOX_THICKNESS - 0.5f
                - (renderBox.getValue() && boxOutline.getValue() ? 0.5f : 0.0f);
        if (healthBarOutline.getValue()) {
            scope.rect(barX - OPAL_BOX_THICKNESS, y - OPAL_BOX_THICKNESS,
                    OPAL_BOX_THICKNESS * 3.0f, height + OPAL_BOX_THICKNESS * 2.0f, Color.BLACK);
        }
        scope.rect(barX, fillY, OPAL_BOX_THICKNESS, endY - fillY, new Color(0, 255, 0));
    }

    private List<TagElement> opalTagElements(LivingEntity entity) {
        List<TagElement> elements = new ArrayList<>();
        if (opalStrength.getValue() && entity.hasEffect(MobEffects.STRENGTH)) {
            elements.add(TagElement.icon("\uEFE4", new Color(255, 0, 0), 0.25f));
        }
        if (opalSneaking.getValue() && entity.isCrouching()) {
            elements.add(TagElement.icon("\uF19F", new Color(255, 85, 85), 0.0f));
        }
        if (opalInvisible.getValue() && entity.isInvisible()) {
            elements.add(TagElement.icon("\uE8F5", new Color(170, 170, 170), 0.30f));
        }
        if (opalBlocking.getValue() && entity.isUsingItem() && entity.getUseItem().is(Items.SHIELD)) {
            elements.add(TagElement.icon("\uE1D5", new Color(65, 175, 125), 0.15f));
        }
        if (opalDistance.getValue() && entity != mc.player) {
            elements.add(TagElement.textIcon(Integer.toString((int) Math.floor(entity.distanceTo(mc.player))),
                    "\uE55C", new Color(170, 170, 170), IconPosition.RIGHT));
        }
        if (opalName.getValue()) {
            elements.add(TagElement.text(entity.getName().getString(), Color.WHITE));
        }
        if (opalHealth.getValue()) {
            String health = String.format(Locale.ROOT, "%.1f", Math.max(0.0f, entity.getHealth()));
            elements.add(TagElement.textIcon(health, "\uE87D", Color.WHITE, IconPosition.RIGHT,
                    new Color(255, 75, 75)));
            if (entity.getAbsorptionAmount() > 0.0f) {
                String absorption = String.format(Locale.ROOT, "%.1f", entity.getAbsorptionAmount());
                elements.add(TagElement.textIcon(absorption, "\uE87D", new Color(255, 194, 71),
                        IconPosition.RIGHT, new Color(255, 194, 71)));
            }
        }
        return elements;
    }

    private void drawOpalTags(UiTree.Scope scope, Projection projection, List<TagElement> elements) {
        if (elements.isEmpty()) return;
        float totalWidth = 0.0f;
        for (int index = 0; index < elements.size(); index++) {
            totalWidth += tagElementWidth(elements.get(index));
            if (index + 1 < elements.size()) totalWidth += 5.0f;
        }
        float currentX = projection.x() + (projection.endX() - projection.x() - totalWidth) * 0.5f;
        float tagY = projection.y() - 9.0f;
        for (TagElement element : elements) {
            float width = tagElementWidth(element);
            float backgroundX = currentX - 2.0f;
            float backgroundY = tagY - 2.0f;
            OpalHudStyle.applyBlur(backgroundX, backgroundY, width + 4.0f, 9.0f,
                    2.0f, 2.0f, 2.0f, 2.0f);
            scope.roundRect(backgroundX, backgroundY, width + 4.0f, 9.0f, 2.0f,
                    new Color(0, 0, 0, 128));

            float textX = currentX;
            if (element.icon() != null && element.iconPosition() == IconPosition.LEFT) {
                scope.text(element.icon(), textX + element.iconOffset(), tagY,
                        OPAL_ICON_SCALE, element.iconColor(), OpalHudStyle.ICON_FONT);
                textX += opalTextWidth(element.icon(), OPAL_ICON_SCALE, OpalHudStyle.ICON_FONT);
            }
            if (element.text() != null) {
                scope.text(element.text(), textX, tagY, OPAL_TAG_SCALE, element.textColor(), OpalHudStyle.BOLD_FONT);
                textX += opalTextWidth(element.text(), OPAL_TAG_SCALE, OpalHudStyle.BOLD_FONT);
            }
            if (element.icon() != null && element.iconPosition() == IconPosition.RIGHT) {
                scope.text(element.icon(), textX + element.iconOffset(), tagY,
                        OPAL_ICON_SCALE, element.iconColor(), OpalHudStyle.ICON_FONT);
            }
            currentX += width + 5.0f;
        }
    }

    private float tagElementWidth(TagElement element) {
        float width = element.text() == null ? 0.0f
                : opalTextWidth(element.text(), OPAL_TAG_SCALE, OpalHudStyle.BOLD_FONT);
        if (element.icon() != null) {
            width += opalTextWidth(element.icon(), OPAL_ICON_SCALE, OpalHudStyle.ICON_FONT);
        }
        return width;
    }

    private float opalTextWidth(String text, float scale, String font) {
        return MinecraftUiRuntime2612.current().textMetrics().textWidth(text, scale, font);
    }

    private void drawOpalEnchantmentLabels(UiTree.Scope scope, Projection projection, boolean hasNameTag) {
        List<ItemStack> equipment = opalEquipment(projection.entity());
        if (equipment.isEmpty()) return;
        float scale = 0.65f;
        float itemRowWidth = equipment.size() * 16.0f * scale;
        float startX = projection.x() + (projection.endX() - projection.x() - itemRowWidth) * 0.5f;
        float itemY = projection.y() - (hasNameTag ? 23.5f : 14.0f);
        for (int index = 0; index < equipment.size(); index++) {
            ItemStack stack = equipment.get(index);
            Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntArrayMap<>();
            EnchantmentUtils.getEnchantments(stack, enchantments);
            int line = 0;
            for (Object2IntMap.Entry<Holder<Enchantment>> enchantment : enchantments.object2IntEntrySet()) {
                String registered = enchantment.getKey().getRegisteredName();
                int separator = registered.indexOf(':');
                String path = separator >= 0 ? registered.substring(separator + 1) : registered;
                String shortName = OPAL_ENCHANTMENT_NAMES.get(path);
                if (shortName == null) continue;
                scope.text(shortName + enchantment.getIntValue(), startX + index * 16.0f * scale + 1.5f,
                        itemY + 8.0f - line * 5.0f, 0.32f, Color.WHITE, OpalHudStyle.MEDIUM_FONT);
                line++;
            }
        }
    }

    private void renderOpalEquipment(GuiGraphicsExtractor graphics, List<Projection> projections) {
        for (Projection projection : projections) {
            if (!opalNameTags.getValue()) continue;
            List<TagElement> elements = opalTagElements(projection.entity());
            List<ItemStack> equipment = opalEquipment(projection.entity());
            if (equipment.isEmpty()) continue;
            float scale = 0.65f;
            float rowWidth = equipment.size() * 16.0f * scale;
            float startX = projection.x() + (projection.endX() - projection.x() - rowWidth) * 0.5f;
            float itemY = projection.y() - (!elements.isEmpty() ? 23.5f : 14.0f);
            for (int index = 0; index < equipment.size(); index++) {
                drawOpalItem(graphics, projection.entity(), equipment.get(index),
                        startX + index * 16.0f * scale, itemY, scale, projection.entity().getId() + index);
            }
        }
    }

    private List<ItemStack> opalEquipment(LivingEntity entity) {
        List<ItemStack> equipment = new ArrayList<>(5);
        appendEquipment(equipment, entity.getItemBySlot(EquipmentSlot.HEAD));
        appendEquipment(equipment, entity.getItemBySlot(EquipmentSlot.CHEST));
        appendEquipment(equipment, entity.getItemBySlot(EquipmentSlot.LEGS));
        appendEquipment(equipment, entity.getItemBySlot(EquipmentSlot.FEET));
        appendEquipment(equipment, entity.getMainHandItem());
        return equipment;
    }

    private void appendEquipment(List<ItemStack> equipment, ItemStack stack) {
        if (!stack.isEmpty()) equipment.add(stack);
    }

    private void drawOpalItem(GuiGraphicsExtractor graphics, LivingEntity owner, ItemStack stack,
                              float x, float y, float scale, int seed) {
        float guiX = (float) UiCoordinateMapper.toMinecraftX(x);
        float guiY = (float) UiCoordinateMapper.toMinecraftY(y);
        float guiScale = (float) UiCoordinateMapper.toMinecraftLength(scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(guiX, guiY);
        graphics.pose().scale(guiScale, guiScale);
        graphics.item(owner, stack, 0, 0, seed);
        graphics.pose().popMatrix();
    }

    private boolean shouldRender(Entity entity) {
        if (mc.player == null) return false;
        if (!entity.isAlive() || entity.isSpectator()) return false;

        if (entity instanceof Player player) {
            if (entity == mc.player) return false;
            if (Managers.FRIEND.isFriend(player)) return friends.getValue();
            return players.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

    private Color getEntityColor(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (Managers.FRIEND.isFriend(player)) return friendsColor.getValue();
            return playersColor.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creaturesColor.getValue();
            case MONSTER -> monstersColor.getValue();
            case AMBIENT, WATER_AMBIENT -> ambientsColor.getValue();
            default -> othersColor.getValue();
        };
    }

    private void drawSolidBox(UiTree.Scope scope, float x, float y, float endX, float endY, Color color) {
        scope.rect(x - 0.5f, y, 0.5f, endY - y, color);
        scope.rect(x, endY - 0.5f, endX - x, 0.5f, color);
        scope.rect(x - 0.5f, y, endX - x + 0.5f, 0.5f, color);
        scope.rect(endX - 0.5f, y, 0.5f, endY - y, color);
    }

    private void drawHealthBar(UiTree.Scope scope, LivingEntity entity, float x, float y, float endY) {
        float height = endY - y;
        if (height <= 0.0f) return;

        float health = Managers.HEALTH.getHealth(entity);
        float maxHealth = Math.max(1.0f, entity.getMaxHealth() + Math.max(0.0f, entity.getAbsorptionAmount()));
        float healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        float fillY = endY - height * healthRatio;

        float distanceScale = height / 45.0f;
        float width = healthBarWidth.getValue().floatValue() * distanceScale;
        float gap = 3.0f * distanceScale;
        float outlineWidth = healthBarOutline.getValue() ? healthBarOutlineWidth.getValue().floatValue() * distanceScale : 0.0f;
        float barX = x - gap - outlineWidth - width;

        if (healthBarOutline.getValue()) {
            scope.rect(barX - outlineWidth, y - outlineWidth, width + outlineWidth * 2.0f, height + outlineWidth * 2.0f, Color.BLACK);
        } else {
            scope.rect(barX, y, width, height, Color.BLACK);
        }

        scope.rect(barX, fillY, width, endY - fillY, healthColor.getValue());
    }

    @Override
    protected void onDisable() {
        releaseScene();
    }

    private UiScene scene(MinecraftUiRuntime2612 runtime) {
        if (scene == null || sceneRuntime != runtime) {
            releaseScene();
            scene = runtime.createScene(GravenUiTheme.lumin());
            sceneRuntime = runtime;
        }
        return scene;
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        if (previous != null) previous.close();
    }

    private record Projection(LivingEntity entity, float x, float y, float endX, float endY) {
    }

    private enum IconPosition {
        LEFT,
        RIGHT
    }

    private record TagElement(String text, String icon, Color textColor, Color iconColor,
                              IconPosition iconPosition, float iconOffset) {
        private static TagElement text(String text, Color color) {
            return new TagElement(text, null, color, color, IconPosition.LEFT, 0.0f);
        }

        private static TagElement icon(String icon, Color color, float offset) {
            return new TagElement(null, icon, color, color, IconPosition.LEFT, offset);
        }

        private static TagElement textIcon(String text, String icon, Color color, IconPosition position) {
            return textIcon(text, icon, color, position, color);
        }

        private static TagElement textIcon(String text, String icon, Color textColor,
                                           IconPosition position, Color iconColor) {
            return new TagElement(text, icon, textColor, iconColor, position, 0.0f);
        }
    }

}
