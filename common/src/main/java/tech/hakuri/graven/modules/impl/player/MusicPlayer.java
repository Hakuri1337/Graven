package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.gui.screen.MusicPlayerScreen;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;

/** 作为模块入口打开独立 MusicPlayer 界面。 */
public final class MusicPlayer extends Module {
    public static final MusicPlayer INSTANCE = new MusicPlayer();

    private MusicPlayer() {
        super("Music Player", Category.PLAYER);
        setDefaultHidden(false);
    }

    public final BoolSetting showOnDynamicIsland = boolSetting("Dynamic Island", true);
    public final BoolSetting showLyrics = boolSetting("Island Lyrics", true, showOnDynamicIsland::getValue);
    public final BoolSetting showCover = boolSetting("Island Cover", true, showOnDynamicIsland::getValue);

    @Override
    protected void onEnable() {
        if (mc.screen != MusicPlayerScreen.INSTANCE) mc.setScreen(MusicPlayerScreen.INSTANCE);
    }

    @Override
    protected void onDisable() {
        if (mc.screen == MusicPlayerScreen.INSTANCE) mc.setScreen(null);
    }
}
