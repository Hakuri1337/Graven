package tech.hakuri.graven.holders;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.addon.GravenAddon;

import java.util.*;

public class AddonHolder {

    public static final AddonHolder INSTANCE = new AddonHolder();

    private final List<GravenAddon> addons = new ArrayList<>();
    private final Set<String> addonIds = new HashSet<>();
    private boolean setupComplete;

    public synchronized void registerAddon(GravenAddon addon) {
        if (Objects.isNull(addon)) {
            return;
        }

        String addonId = addon.getAddonId();
        if (isBlank(addonId)) {
            Constants.LOGGER.warn("忽略无有效ID的插件：{}", addon.getClass().getName());
            return;
        }

        // 重复ID校验
        if (!addonIds.add(addonId)) {
            Constants.LOGGER.warn("忽略重复插件ID：{}", addonId);
            return;
        }

        addons.add(addon);
    }

    public synchronized void registerAddons(Iterable<GravenAddon> addonIterable) {
        if (Objects.isNull(addonIterable)) {
            return;
        }
        addonIterable.forEach(this::registerAddon);
    }

    public synchronized void setupAddons() {
        if (setupComplete) {
            return;
        }
        setupComplete = true;
        for (GravenAddon addon : addons) {
            try {
                addon.initAddonI18n();
                addon.onSetup();
                Constants.LOGGER.info("插件加载完成：{}", addon.getAddonId());
            } catch (Throwable t) {
                Constants.LOGGER.error("插件初始化失败：{}", addon.getAddonId(), t);
            }
        }
    }

    public synchronized List<GravenAddon> getAddons() {
        return Collections.unmodifiableList(addons);
    }

    /**
     * 兼容低版本jdk的字符串空判断
     *
     * @param str 字符串
     * @return 是否为空/空白
     */
    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

}
