package tech.hakuri.graven;

import tech.hakuri.graven.assets.i18n.GravenLanguageManager;
import tech.hakuri.graven.assets.i18n.I18NFileGenerator;
import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.holders.AddonHolder;
import tech.hakuri.graven.holders.ConfigHolder;
import tech.hakuri.graven.holders.HudElementHolder;
import tech.hakuri.graven.holders.ModuleHolder;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.ClientSetting;
import tech.hakuri.graven.scripting.lua.LuaScriptManager;

import java.lang.invoke.MethodHandles;

public class graven {

    public static void init() {
        Constants.LOGGER.info("Welcome to " + Constants.NAME + ".");

        EventBus.INSTANCE.registerLambdaFactory(graven.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // 初始化客户端系统
        ModuleHolder.INSTANCE.initModules();
        HudElementHolder.INSTANCE.initElements();
        AddonHolder.INSTANCE.setupAddons();
        ConfigHolder.INSTANCE.initConfig();
        GravenLanguageManager.INSTANCE.selectLanguage(ClientSetting.INSTANCE.language.getValue());

        // 初始化 Managers
        Managers.initManagers();

        // 初始化 Render3DScheduler 里的 RenderPipeline
        Render3DScheduler.init();

        LuaScriptManager.INSTANCE.init(ClientSetting.INSTANCE.luaScriptsEnabled.getValue());

        // 生成空的 i18n 文件
        I18NFileGenerator.generate("graven-empty-i18n.json");

        // 添加一个退出游戏时候的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigHolder.INSTANCE.saveNow();
            LuaScriptManager.INSTANCE.close();
            Constants.LOGGER.info(Constants.NAME + " saved config on shutdown.");
        }));

        Constants.LOGGER.info(Constants.NAME + " has loaded successfully.");
    }

}
