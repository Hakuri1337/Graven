package tech.hakuri.graven.elements.impl;

import tech.hakuri.graven.elements.HudModule;
import tech.hakuri.graven.gui.utils.UiCoordinateMapper;
import tech.hakuri.graven.gui.theme.OpalHudStyle;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import tech.hakuri.graven.gui.hudeditor.HudEditorScreen;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.combat.KillAura;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.ColorSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.render.animation.Easing;
import tech.hakuri.graven.utils.render.animation.Animation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TargetHUD extends HudModule {

    public static final TargetHUD INSTANCE = new TargetHUD();

    private TargetHUD() {
        super("Target HUD", 0f, 0f, 180f, 80f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 0.9, 0.5, 2.0, 0.1);
    private final DoubleSetting width = doubleSetting("Width", 150.0, 100.0, 300.0, 1.0);
    private final DoubleSetting height = doubleSetting("Height", 52.0, 30.0, 100.0, 1.0);
    private final DoubleSetting radius = doubleSetting("Radius", 5.0, 0.0, 20.0, 1.0);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0);
    private final DoubleSetting healthBarHeight = doubleSetting("Bar Height", 3.0, 2.0, 20.0, 1.0);
    private final DoubleSetting healthBarRadius = doubleSetting("Bar Radius", 1.2, 0.0, 15.0, 1.0);
    private final DoubleSetting nameSize = doubleSetting("Name Size", 10.5, 8.0, 18.0, 0.5);
    private final BoolSetting delayBar = boolSetting("Delay Bar", true);
    private final BoolSetting delayWait = boolSetting("Delay Wait", true, delayBar::getValue);
    private final DoubleSetting delayTime = doubleSetting("Delay Time", 250.0, 0.0, 500.0, 50.0, () -> delayBar.getValue() && delayWait.getValue());
    private final DoubleSetting delaySpeed = doubleSetting("Delay Speed", 2.0, 0.1, 10.0, 0.1, delayBar::getValue);
    private final BoolSetting barOutline = boolSetting("Bar Outline", true);
    private final DoubleSetting barOutlineWidth = doubleSetting("Bar Outline Width", 1.0, 0.5, 5.0, 0.5, barOutline::getValue);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145));
    private final ColorSetting barBackgroundColor = colorSetting("Bar Background Color", new Color(255, 255, 255, 55));
    private final ColorSetting barFillColor = colorSetting("Bar Fill Color", new Color(255, 236, 248, 235));
    private final ColorSetting delayBarColor = colorSetting("Delay Bar Color", new Color(190, 190, 190, 100), delayBar::getValue);
    private final ColorSetting barOutlineColor = colorSetting("Bar Outline Color", new Color(255, 255, 255, 85), barOutline::getValue);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);

    private static final long VISIBILITY_ANIMATION_DURATION_MS = 300L;
    private static final float HEAD_DAMAGE_SCALE_FACTOR = 0.15f;
    private static final float EQUIPMENT_ITEM_SCALE = 0.85f;
    private static final float OPAL_HEIGHT = 31.5f;
    private static final float OPAL_PADDING = 3.0f;
    private static final float OPAL_HEAD_OFFSET = 22.5f;
    private static final float OPAL_EQUIPMENT_WIDTH = 55.0f;
    private static final float OPAL_NAME_SCALE = 0.48f;
    private static final float OPAL_HP_SCALE = 0.42f;

    private int lastTargetId = Integer.MIN_VALUE;
    private float displayedHealth = 0.0f;
    private float delayedHealth = 0.0f;
    private float lastKnownHealth = -1.0f;
    private float lastKnownMaxHealth = 1.0f;
    private long lastDamageTimeMs = 0L;
    private LivingEntity renderedTarget;
    private float visibilityProgress = 0.0f;
    private long lastVisibilityUpdateMs = 0L;
    private final Animation opalHealthAnimation = new Animation(Easing.EASE_OUT_EXPO, 1000L);
    private int opalHealthTargetId = Integer.MIN_VALUE;


    @Override
    public void render(DeltaTracker deltaTracker) {
        if (OpalHudStyle.active()) {
            renderOpal(deltaTracker);
            return;
        }

        float panelScale = scale.getValue().floatValue();
        float panelWidth = width.getValue().floatValue() * panelScale;
        float panelHeight = height.getValue().floatValue() * panelScale;
        setBounds(panelWidth, panelHeight);

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        LivingEntity target = updateRenderedTarget(resolveTarget());
        float animationScale = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        if (target == null || animationScale <= 0.01f) return;

        UiTree.Scope scope = renderScope();

        LivingEntity liveTarget = resolveTarget();
        float maxHealth = lastKnownMaxHealth;
        float healthPercent;
        if (liveTarget == target) {
            float health = Managers.HEALTH.getHealth(target);
            maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
            lastKnownMaxHealth = maxHealth;
            healthPercent = updateAnimatedHealth(target, health, maxHealth, frameTime);
        } else {
            maxHealth = Math.max(1.0f, lastKnownMaxHealth);
            displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth);
            delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth);
            healthPercent = Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f);
        }
        float delayHealthPercent = Mth.clamp(delayedHealth / maxHealth, 0.0f, 1.0f);

        float pad = 5.0f * panelScale;
        float cornerRadius = radius.getValue().floatValue() * panelScale;
        float barHeight = healthBarHeight.getValue().floatValue() * panelScale;
        float barRadius = healthBarRadius.getValue().floatValue() * panelScale;
        float barWidth = Math.max(1.0f, panelWidth - pad * 2.0f);
        float delayedBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * delayHealthPercent);
        float filledBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * healthPercent);

        float innerHeight = Math.max(1.0f, panelHeight - pad * 2.0f);
        float contentAreaHeight = Math.max(1.0f, innerHeight - pad - barHeight);
        float headSize = Math.min(contentAreaHeight, Math.max(26.0f * panelScale, panelHeight * 0.6f) * 1.05f);
        float textScale = Math.max(0.45f, nameSize.getValue().floatValue() / 14.0f) * panelScale;
        float textHeight = textHeight(textScale, "graven-default");
        float contentRowHeight = Math.max(headSize, textHeight);
        float contentBlockHeight = contentRowHeight + pad + barHeight;
        float contentStartY = this.y + pad + Math.max(0.0f, (innerHeight - contentBlockHeight) / 2.0f);
        float headY = contentStartY + (contentRowHeight - headSize) / 2.0f;
        float headX = this.x + pad;
        float barY = contentStartY + contentRowHeight + pad;

        float textStartX = headX + headSize + pad;

        String nameText = target.getName().getString();
        String healthText = String.format(Locale.ROOT, "%.1f", displayedHealth);

        float contentY = headY + 2.0f * panelScale;
        float healthTextWidth = textWidth(healthText, textScale, "graven-default");
        float healthTextX = this.x + panelWidth - pad - healthTextWidth;
        float equipmentY = contentY + textHeight + 2.8f * panelScale;
        float equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale;
        float equipmentGap = 1.5f * panelScale;

        float centerX = this.x + panelWidth / 2.0f;
        float centerY = this.y + panelHeight / 2.0f;
        float scaledPanelX = Mth.lerp(animationScale, centerX, this.x);
        float scaledPanelY = Mth.lerp(animationScale, centerY, this.y);
        float scaledPanelWidth = panelWidth * animationScale;
        float scaledPanelHeight = panelHeight * animationScale;
        float scaledCornerRadius = cornerRadius * animationScale;
        float scaledBarHeight = barHeight * animationScale;
        float scaledBarRadius = barRadius * animationScale;
        float scaledBarOutlineWidth = barOutlineWidth.getValue().floatValue() * panelScale * animationScale;
        float scaledTextScale = textScale * animationScale;
        float scaledHeadRadius = headSize * 0.23f * animationScale;
        float scaledPadX = Mth.lerp(animationScale, centerX, this.x + pad);
        float scaledBarY = Mth.lerp(animationScale, centerY, barY);
        float scaledBarWidth = barWidth * animationScale;
        float scaledDelayedBarWidth = delayedBarWidth * animationScale;
        float scaledFilledBarWidth = filledBarWidth * animationScale;
        float scaledHeadX = Mth.lerp(animationScale, centerX, headX);
        float scaledHeadY = Mth.lerp(animationScale, centerY, headY);
        float scaledHeadSize = headSize * animationScale;
        float scaledTextStartX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledContentY = Mth.lerp(animationScale, centerY, contentY);
        float scaledHealthTextX = Mth.lerp(animationScale, centerX, healthTextX);
        float scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY);
        float scaledEquipmentScale = equipmentScale * animationScale;
        float scaledEquipmentGap = equipmentGap * animationScale;
        float damageProgress = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f));
        float headDamageScale = 1.0f - damageProgress * HEAD_DAMAGE_SCALE_FACTOR;
        float finalHeadSize = scaledHeadSize * headDamageScale;
        float finalHeadX = scaledHeadX + (scaledHeadSize - finalHeadSize) / 2.0f;
        float finalHeadY = scaledHeadY + (scaledHeadSize - finalHeadSize) / 2.0f;
        float finalHeadRadius = scaledHeadRadius * headDamageScale;

        MinecraftUiRuntime2612.current().applyBlur(MinecraftBlurRegion2612.rounded(
                new UiRect(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight),
                scaledCornerRadius, blurStrength.getValue().floatValue()));

        if (drawShadow.getValue()) {
            scope.shadow(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, shadowBlur.getValue().floatValue() * animationScale, lumin(withAlpha(shadowColor.getValue(), animationScale)));
        }

        scope.roundRect(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, lumin(withAlpha(backgroundColor.getValue(), animationScale)));
        scope.roundRect(scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius, lumin(withAlpha(barBackgroundColor.getValue(), animationScale)));
        if (delayBar.getValue() && delayedHealth > displayedHealth) {
            scope.roundRect(scaledPadX, scaledBarY, scaledDelayedBarWidth, scaledBarHeight, scaledBarRadius, lumin(withAlpha(delayBarColor.getValue(), animationScale)));
        }
        scope.roundRect(scaledPadX, scaledBarY, scaledFilledBarWidth, scaledBarHeight, scaledBarRadius, lumin(withAlpha(barFillColor.getValue(), animationScale)));
        if (!(target instanceof AbstractClientPlayer)) {
            scope.roundRect(finalHeadX, finalHeadY, finalHeadSize, finalHeadSize, finalHeadRadius, lumin(withAlpha(tintColor(new Color(80, 80, 80, 200), damageProgress), animationScale)));
        }

        if (barOutline.getValue() && scaledBarOutlineWidth > 0.0f) {
            scope.outline(
                    scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius,
                    scaledBarOutlineWidth, lumin(withAlpha(barOutlineColor.getValue(), animationScale))
            );
        }

        // 玩家头像在 overlay 阶段使用 NEAREST 采样，避免 Lumin 默认 LINEAR 放大 8x8 面部区域。

        scope.text(nameText, scaledTextStartX, scaledContentY, scaledTextScale, lumin(withAlpha(textColor.getValue(), animationScale)));
        scope.text(healthText, scaledHealthTextX, scaledContentY, scaledTextScale, lumin(withAlpha(textColor.getValue(), animationScale)));
    }

    private void renderOpal(DeltaTracker deltaTracker) {
        float panelScale = scale.getValue().floatValue();
        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        LivingEntity target = updateRenderedTarget(resolveTarget());
        float visibility = Easing.EASE_OUT_EXPO.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        if (target == null || visibility <= 0.01f) return;

        LivingEntity liveTarget = resolveTarget();
        float maxHealth = Math.max(1.0f, lastKnownMaxHealth);
        float trueHealth = displayedHealth;
        if (liveTarget == target) {
            trueHealth = Managers.HEALTH.getHealth(target);
            maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
            updateAnimatedHealth(target, trueHealth, maxHealth, frameTime);
        }
        float trueHealthPercent = Mth.clamp(trueHealth / maxHealth, 0.0f, 1.0f);
        if (opalHealthTargetId != target.getId()) {
            opalHealthTargetId = target.getId();
            opalHealthAnimation.setValue(trueHealthPercent);
        }
        opalHealthAnimation.run(trueHealthPercent);
        float animatedHealthPercent = Mth.clamp(opalHealthAnimation.getValue(), 0.0f, 1.0f);

        String targetName = target.getName().getString();
        float baseNameWidth = textWidth(targetName, OPAL_NAME_SCALE, OpalHudStyle.BOLD_FONT);
        float baseWidth = OPAL_PADDING * 2.0f
                + Math.max(50.0f, Math.max(OPAL_EQUIPMENT_WIDTH, baseNameWidth))
                + OPAL_HEAD_OFFSET + 1.0f;
        float panelWidth = baseWidth * panelScale;
        float panelHeight = OPAL_HEIGHT * panelScale;
        setBounds(panelWidth, panelHeight);

        UiTree.Scope scope = renderScope();
        float centerX = x + panelWidth * 0.5f;
        float centerY = y + panelHeight * 0.5f;
        float panelX = Mth.lerp(visibility, centerX, x);
        float panelY = Mth.lerp(visibility, centerY, y);
        float visibleWidth = panelWidth * visibility;
        float visibleHeight = panelHeight * visibility;
        float radius = 4.0f * panelScale * visibility;
        OpalHudStyle.applyBlur(panelX, panelY, visibleWidth, visibleHeight, radius, radius, radius, radius);
        OpalHudStyle.drawSurface(scope, panelX, panelY, visibleWidth, visibleHeight,
                radius, radius, radius, radius, visibility);

        float contentX = x + (OPAL_PADDING + OPAL_HEAD_OFFSET) * panelScale;
        float nameY = y + 7.0f * panelScale;
        scope.text(targetName, contentX, nameY, OPAL_NAME_SCALE * panelScale,
                lumin(OpalHudStyle.withAlpha(OpalHudStyle.TEXT, visibility)), OpalHudStyle.BOLD_FONT);

        List<ItemStack> equipment = opalEquipment(target);
        for (int index = 0; index < equipment.size(); index++) {
            float slotX = x + (OPAL_PADDING + OPAL_HEAD_OFFSET - 0.5f + index * 11.5f) * panelScale;
            float slotY = y + (OPAL_PADDING + 8.5f) * panelScale;
            scope.roundRect(slotX, slotY, 10.5f * panelScale, 10.5f * panelScale,
                    panelScale, lumin(OpalHudStyle.withAlpha(new Color(0, 0, 0, 51), visibility)));
        }

        float absorption = Math.max(0.0f, target.getAbsorptionAmount());
        String healthText = String.format(Locale.ROOT, "%.1f", Math.max(0.0f, trueHealth));
        String heart = "\uE87D";
        float heartScale = OPAL_HP_SCALE * panelScale;
        float heartWidth = textWidth(heart, heartScale, OpalHudStyle.ICON_FONT);
        float healthWidth = textWidth(healthText, heartScale, OpalHudStyle.MEDIUM_FONT);
        float healthX = x + panelWidth - OPAL_PADDING * panelScale - heartWidth - healthWidth - 1.0f * panelScale;
        float healthY = y + 24.1f * panelScale;
        scope.text(healthText, healthX, healthY, heartScale,
                lumin(OpalHudStyle.withAlpha(OpalHudStyle.TEXT, visibility)), OpalHudStyle.MEDIUM_FONT);
        scope.text(heart, healthX + healthWidth + 0.5f * panelScale, healthY - 0.25f * panelScale,
                heartScale,
                lumin(OpalHudStyle.withAlpha(absorption > 0.0f
                        ? new Color(255, 194, 71) : new Color(255, 75, 75), visibility)),
                OpalHudStyle.ICON_FONT);

        float sampleHealthWidth = textWidth(healthText.length() > 2 ? healthText : "88.",
                heartScale, OpalHudStyle.MEDIUM_FONT);
        float barX = x + (OPAL_PADDING - 0.125f) * panelScale;
        float barY = y + 24.75f * panelScale;
        float barWidth = panelWidth - 2.75f * OPAL_PADDING * panelScale - sampleHealthWidth - heartWidth;
        float barHeight = 4.0f * panelScale;
        float barRadius = 5.0f / 3.0f * panelScale;
        Color accentStart = OpalHudStyle.accent(0);
        Color accentEnd = OpalHudStyle.accent(18);
        scope.roundRect(barX, barY, barWidth, barHeight, barRadius,
                lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentEnd, 0.8f, 1.0f), visibility * 0.6f)));
        if (animatedHealthPercent > 0.01f) {
            scope.roundRectGradient(barX, barY, barWidth * animatedHealthPercent, barHeight,
                    barRadius, barRadius, barRadius, barRadius,
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentStart, 0.6f, 1.0f), visibility)),
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentEnd, 0.6f, 1.0f), visibility)),
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentEnd, 0.6f, 1.0f), visibility)),
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentStart, 0.6f, 1.0f), visibility)));
        }
        if (trueHealthPercent > 0.01f) {
            scope.roundRectGradient(barX, barY, barWidth * trueHealthPercent, barHeight,
                    barRadius, barRadius, barRadius, barRadius,
                    lumin(OpalHudStyle.withAlpha(accentStart, visibility)),
                    lumin(OpalHudStyle.withAlpha(accentEnd, visibility)),
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentEnd, 0.6f, 1.0f), visibility)),
                    lumin(OpalHudStyle.withAlpha(OpalHudStyle.darker(accentStart, 0.6f, 1.0f), visibility)));
        }

        float headX = x + (OPAL_PADDING + 0.25f) * panelScale;
        float headY = y + OPAL_PADDING * panelScale;
        float headSize = 19.5f * panelScale;
        float headRadius = 2.0f * panelScale;
        float damageProgress = Mth.clamp(target.hurtTime / (float) Math.max(1, target.hurtDuration), 0.0f, 1.0f);
        if (!(target instanceof AbstractClientPlayer)) {
            scope.roundRect(headX, headY, headSize, headSize, headRadius,
                    lumin(OpalHudStyle.withAlpha(new Color(80, 80, 80, 200), visibility)));
        }
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (OpalHudStyle.active()) {
            renderOpalOverlay(graphics);
            return;
        }

        float panelScale = scale.getValue().floatValue();
        LivingEntity target = renderedTarget;
        if (target == null || visibilityProgress <= 0.01f) return;

        float panelWidth = width.getValue().floatValue() * panelScale;
        float panelHeight = height.getValue().floatValue() * panelScale;
        float animationScale = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        float pad = 5.0f * panelScale;
        float barHeight = healthBarHeight.getValue().floatValue() * panelScale;
        float innerHeight = Math.max(1.0f, panelHeight - pad * 2.0f);
        float contentAreaHeight = Math.max(1.0f, innerHeight - pad - barHeight);
        float headSize = Math.min(contentAreaHeight, Math.max(26.0f * panelScale, panelHeight * 0.6f) * 1.05f);
        float textScale = Math.max(0.45f, nameSize.getValue().floatValue() / 14.0f) * panelScale;
        float textHeight = textHeight(textScale, "graven-default");
        float contentRowHeight = Math.max(headSize, textHeight);
        float contentBlockHeight = contentRowHeight + pad + barHeight;
        float contentStartY = this.y + pad + Math.max(0.0f, (innerHeight - contentBlockHeight) / 2.0f);
        float headY = contentStartY + (contentRowHeight - headSize) / 2.0f;
        float headX = this.x + pad;
        float textStartX = headX + headSize + pad;
        float contentY = headY + 2.0f * panelScale;
        float equipmentY = contentY + textHeight + 2.8f * panelScale;
        float equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale;
        float equipmentGap = 1.5f * panelScale;
        float centerX = this.x + panelWidth / 2.0f;
        float centerY = this.y + panelHeight / 2.0f;
        float scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY);
        float scaledEquipmentScale = equipmentScale * animationScale;
        float scaledEquipmentGap = equipmentGap * animationScale;
        float scaledHeadX = Mth.lerp(animationScale, centerX, headX);
        float scaledHeadY = Mth.lerp(animationScale, centerY, headY);
        float scaledHeadSize = headSize * animationScale;
        float damageProgress = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f));
        float finalHeadSize = scaledHeadSize * (1.0f - damageProgress * HEAD_DAMAGE_SCALE_FACTOR);
        float finalHeadX = scaledHeadX + (scaledHeadSize - finalHeadSize) / 2.0f;
        float finalHeadY = scaledHeadY + (scaledHeadSize - finalHeadSize) / 2.0f;

        if (target instanceof AbstractClientPlayer player) {
            renderPlayerHead(graphics, player, finalHeadX, finalHeadY, finalHeadSize);
        }
        renderEquipmentItems(graphics, target, scaledEquipmentX, scaledEquipmentY, scaledEquipmentScale, scaledEquipmentGap);
    }

    private void renderOpalOverlay(GuiGraphicsExtractor graphics) {
        LivingEntity target = renderedTarget;
        if (target == null || visibilityProgress <= 0.01f) return;
        float panelScale = scale.getValue().floatValue();
        float visibility = Easing.EASE_OUT_EXPO.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        if (target instanceof AbstractClientPlayer player) {
            renderPlayerHead(graphics, player,
                    x + (OPAL_PADDING + 0.25f) * panelScale,
                    y + OPAL_PADDING * panelScale,
                    19.5f * panelScale);
        }
        List<ItemStack> equipment = opalEquipment(target);
        float itemScale = 0.625f * panelScale * visibility;
        float itemY = y + (OPAL_PADDING + 8.5f) * panelScale;
        for (int index = 0; index < equipment.size(); index++) {
            ItemStack stack = equipment.get(index);
            if (stack.isEmpty()) continue;
            float itemX = x + (OPAL_PADDING + OPAL_HEAD_OFFSET - 0.5f + index * 11.6f) * panelScale;
            drawItem(graphics, target, stack, itemX, itemY, itemScale, target.getId() + index);
        }
    }

    private List<ItemStack> opalEquipment(LivingEntity target) {
        List<ItemStack> equipment = new ArrayList<>(5);
        equipment.add(target.getMainHandItem());
        equipment.add(target.getItemBySlot(EquipmentSlot.FEET));
        equipment.add(target.getItemBySlot(EquipmentSlot.LEGS));
        equipment.add(target.getItemBySlot(EquipmentSlot.CHEST));
        equipment.add(target.getItemBySlot(EquipmentSlot.HEAD));
        return equipment;
    }

    private void renderEquipmentItems(GuiGraphicsExtractor graphics, LivingEntity target, float startX, float y, float scale, float gap) {
        List<ItemStack> equipmentItems = new ArrayList<>(5);
        appendEquipmentItem(equipmentItems, target.getMainHandItem());
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.HEAD));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.CHEST));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.LEGS));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.FEET));

        if (equipmentItems.isEmpty()) {
            return;
        }

        float itemSize = 16.0f * scale;
        float itemX = startX;
        for (int i = 0; i < equipmentItems.size(); i++) {
            drawItem(graphics, target, equipmentItems.get(i), itemX, y, scale, target.getId() + i);
            itemX += itemSize + gap;
        }
    }

    private void appendEquipmentItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack);
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, LivingEntity owner, ItemStack stack, float x, float y, float scale, int seed) {
        float guiX = (float) UiCoordinateMapper.toMinecraftX(x);
        float guiY = (float) UiCoordinateMapper.toMinecraftY(y);
        float guiScale = (float) UiCoordinateMapper.toMinecraftLength(scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(guiX + guiScale, guiY + guiScale);
        graphics.pose().scale(guiScale, guiScale);
        graphics.item(owner, stack, 0, 0, seed);
        graphics.pose().popMatrix();
    }

    private void renderPlayerHead(GuiGraphicsExtractor graphics, AbstractClientPlayer player,
                                  float projectionX, float projectionY, float projectionSize) {
        Identifier skinId = player.getSkin().body().texturePath();
        AbstractTexture skinTexture = mc.getTextureManager().getTexture(skinId);
        GpuTextureView view = skinTexture.getTextureView();
        if (view == null || view.texture().getWidth(0) < 64 || view.texture().getHeight(0) < 32) {
            return;
        }

        int x0 = (int) Math.round(UiCoordinateMapper.toMinecraftX(projectionX));
        int y0 = (int) Math.round(UiCoordinateMapper.toMinecraftY(projectionY));
        int x1 = Math.max(x0 + 1, (int) Math.round(UiCoordinateMapper.toMinecraftX(projectionX + projectionSize)));
        int y1 = Math.max(y0 + 1, (int) Math.round(UiCoordinateMapper.toMinecraftY(projectionY + projectionSize)));
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        graphics.blit(view, sampler, x0, y0, x1, y1,
                8f / 64f, 16f / 64f, 8f / 64f, 16f / 64f);
        graphics.blit(view, sampler, x0, y0, x1, y1,
                40f / 64f, 48f / 64f, 8f / 64f, 16f / 64f);
    }

    private LivingEntity resolveTarget() {
        LivingEntity target = KillAura.INSTANCE.target;
        if (isRenderableTarget(target)) {
            return target;
        }
        return mc.screen instanceof HudEditorScreen ? mc.player : null;
    }

    private boolean isRenderableTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isDeadOrDying();
    }

    private LivingEntity updateRenderedTarget(LivingEntity liveTarget) {
        long now = System.currentTimeMillis();
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now;
        }

        long duration = OpalHudStyle.active() ? 200L : VISIBILITY_ANIMATION_DURATION_MS;
        float delta = Mth.clamp((now - lastVisibilityUpdateMs) / (float) duration, 0.0f, 1.0f);
        lastVisibilityUpdateMs = now;

        if (liveTarget != null) {
            renderedTarget = liveTarget;
            visibilityProgress = Math.min(1.0f, visibilityProgress + delta);
            return renderedTarget;
        }

        if (renderedTarget == null) {
            visibilityProgress = 0.0f;
            return null;
        }

        visibilityProgress = Math.max(0.0f, visibilityProgress - delta);
        if (visibilityProgress <= 0.01f) {
            renderedTarget = null;
            resetAnimatedState();
            return null;
        }
        return renderedTarget;
    }

    private Color tintColor(Color baseColor, float damageProgress) {
        int greenBlue = Mth.clamp(Math.round(255.0f - 155.0f * damageProgress), 100, 255);
        int red = Mth.clamp(Math.round(baseColor.getRed() + (255 - baseColor.getRed()) * damageProgress), 0, 255);
        int green = Mth.clamp(Math.round(baseColor.getGreen() * greenBlue / 255.0f), 0, 255);
        int blue = Mth.clamp(Math.round(baseColor.getBlue() * greenBlue / 255.0f), 0, 255);
        return new Color(red, green, blue, baseColor.getAlpha());
    }

    private Color withAlpha(Color color, float alphaScale) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * alphaScale), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float updateAnimatedHealth(LivingEntity target, float currentHealth, float maxHealth, float frameTime) {
        int targetId = target.getId();
        if (targetId != lastTargetId) {
            lastTargetId = targetId;
            displayedHealth = currentHealth;
            delayedHealth = currentHealth;
            lastKnownHealth = currentHealth;
            lastKnownMaxHealth = maxHealth;
            lastDamageTimeMs = 0L;
        } else {
            if (lastKnownHealth >= 0.0f && currentHealth < lastKnownHealth) {
                lastDamageTimeMs = System.currentTimeMillis();
            }
            float speed = Mth.clamp(frameTime * 10.0f, 0.0f, 1.0f);
            displayedHealth = Mth.lerp(speed, displayedHealth, currentHealth);
            delayedHealth = updateDelayedHealth(currentHealth, frameTime);
            lastKnownHealth = currentHealth;
            lastKnownMaxHealth = maxHealth;
        }
        displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth);
        delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth);
        return Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f);
    }

    private float updateDelayedHealth(float currentHealth, float frameTime) {
        if (!delayBar.getValue()) {
            return currentHealth;
        }
        if (currentHealth >= delayedHealth) {
            return currentHealth;
        }
        if (delayWait.getValue() && System.currentTimeMillis() - lastDamageTimeMs < delayTime.getValue().longValue()) {
            return delayedHealth;
        }
        float speed = Mth.clamp(frameTime * delaySpeed.getValue().floatValue() * 2.0f, 0.0f, 1.0f);
        return Mth.lerp(speed, delayedHealth, currentHealth);
    }

    private void resetAnimatedState() {
        lastTargetId = Integer.MIN_VALUE;
        displayedHealth = 0.0f;
        delayedHealth = 0.0f;
        lastKnownHealth = -1.0f;
        lastKnownMaxHealth = 1.0f;
        lastDamageTimeMs = 0L;
        opalHealthTargetId = Integer.MIN_VALUE;
    }

}
