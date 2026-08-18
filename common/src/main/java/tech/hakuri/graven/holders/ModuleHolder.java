package tech.hakuri.graven.holders;

import tech.hakuri.graven.assets.i18n.GravenTranslateComponent;
import tech.hakuri.graven.assets.i18n.TranslateComponent;
import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.KeyPressEvent;
import tech.hakuri.graven.events.impl.MousePressEvent;
import tech.hakuri.graven.gui.dropdown.DropdownScreen;
import tech.hakuri.graven.gui.panel.PanelScreen;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.sound.SoundKey;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.ModuleKey;
import tech.hakuri.graven.modules.impl.ClientSetting;
import tech.hakuri.graven.modules.impl.combat.*;
import tech.hakuri.graven.modules.impl.movement.*;
import tech.hakuri.graven.modules.impl.movement.elytrafly.ElytraFly;
import tech.hakuri.graven.modules.impl.movement.follower.Follower;
import tech.hakuri.graven.modules.impl.player.*;
import tech.hakuri.graven.modules.impl.render.*;
import tech.hakuri.graven.modules.impl.render.maseffects.MasEffects;
import tech.hakuri.graven.utils.client.KeybindUtils;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import static tech.hakuri.graven.Constants.mc;

public class ModuleHolder {

    public static final ModuleHolder INSTANCE = new ModuleHolder();

    public ModuleHolder() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final List<Module> modules = new ArrayList<>();
    private final Map<ModuleKey, Module> modulesByKey = new HashMap<>();

    public void initModules() {
        addModule(ClientSetting.INSTANCE);

        // Combat
        addModule(AimBot.INSTANCE);
        addModule(AnchorBlast.INSTANCE);
        addModule(AntiBot.INSTANCE);
        addModule(AutoThrow.INSTANCE);
        addModule(AutoClicker.INSTANCE);
        addModule(AutoDtap.INSTANCE);
        addModule(AutoHitCrystal.INSTANCE);
        addModule(AutoMend.INSTANCE);
        addModule(AutoTotem.INSTANCE);
        addModule(AutoWeapon.INSTANCE);
        addModule(Criticals.INSTANCE);
        addModule(ZealotCrystalPlus.INSTANCE);
        addModule(CrystalAura.INSTANCE);
        addModule(CrystalBlocker.INSTANCE);
        addModule(FeetTrap.INSTANCE);
        addModule(DoubleAnchor.INSTANCE);
        addModule(HoverTotem.INSTANCE);
        addModule(KillAura.INSTANCE);
        addModule(KeyPearl.INSTANCE);
        addModule(MaceAura.INSTANCE);
        addModule(PacketMine.INSTANCE);
        addModule(SafeAnchor.INSTANCE);
        addModule(SafeCrystal.INSTANCE);
        addModule(SilentAim.INSTANCE);
        addModule(SpearKill.INSTANCE);
        addModule(Teams.INSTANCE);
        addModule(TriggerBot.INSTANCE);
        addModule(Velocity.INSTANCE);
        addModule(TargetStrafe.INSTANCE);
        addModule(TpauraRise.INSTANCE);
        addModule(TpAura.INSTANCE);
        addModule(TpAuraPlus.INSTANCE);

        // Player
        addModule(AutoArmor.INSTANCE);
        addModule(AutoFirework.INSTANCE);
        addModule(AutoKouZi.INSTANCE);
        addModule(AutoMLG.INSTANCE);
        addModule(AutoTool.INSTANCE);
        addModule(BreakCooldown.INSTANCE);
        addModule(Disabler.INSTANCE);
        addModule(ElytraSwap.INSTANCE);
        addModule(FakePlayer.INSTANCE);
        addModule(GhostHand.INSTANCE);
        addModule(InvManager.INSTANCE);
        addModule(JumpCooldown.INSTANCE);
        addModule(MultiTask.INSTANCE);
        addModule(NoRotate.INSTANCE);
        addModule(PacketEat.INSTANCE);
        addModule(PlayerAlarms.INSTANCE);
        addModule(SoundFX.INSTANCE);
        addModule(Stealer.INSTANCE);
        addModule(Timer.INSTANCE);
        addModule(UseCooldown.INSTANCE);
        addModule(AutoQueue.INSTANCE);
        addModule(AutoRunAway.INSTANCE);
        addModule(AutoGapple.INSTANCE);
        addModule(ClickTP.INSTANCE);
        addModule(Derp.INSTANCE);
        addModule(Regen.INSTANCE);
        addModule(AntiStaff.INSTANCE);
        addModule(MusicPlayer.INSTANCE);
        addModule(Targets.INSTANCE);

        // Movement
        addModule(ElytraFly.INSTANCE);
        addModule(Follower.INSTANCE);
        addModule(AutoSprint.INSTANCE);
        addModule(Blink.INSTANCE);
        addModule(Eagle.INSTANCE);
        addModule(AutoMap.INSTANCE);
        addModule(FastWeb.INSTANCE);
        addModule(Clip.INSTANCE);
        addModule(Flight.INSTANCE);
        addModule(FlightDelayTrigger.INSTANCE);
        addModule(Freeze.INSTANCE);
        addModule(GUIMove.INSTANCE);
        addModule(HoleSnap.INSTANCE);
        addModule(JumpReset.INSTANCE);
        addModule(KeepSprint.INSTANCE);
        addModule(MovementFix.INSTANCE);
        addModule(NoFall.INSTANCE);
        addModule(NoSlow.INSTANCE);
        addModule(Phase.INSTANCE);
        addModule(SafeWalk.INSTANCE);
        addModule(Scaffold.INSTANCE);
        addModule(Speed.INSTANCE);
        addModule(Step.INSTANCE);
        addModule(Strafe.INSTANCE);
        addModule(Stuck.INSTANCE);
        addModule(AntiVoid.INSTANCE);

        // Render
        addModule(AntiAlias.INSTANCE);
        addModule(AspectRatio.INSTANCE);
        addModule(BlockESP.INSTANCE);
        addModule(BlockHighlight.INSTANCE);
        addModule(CameraClip.INSTANCE);
        addModule(Chams.INSTANCE);
        addModule(CrystalChams.INSTANCE);
        addModule(ESP2D.INSTANCE);
        addModule(Filter.INSTANCE);
        addModule(FreeCamera.INSTANCE);
        addModule(Fullbright.INSTANCE);
        addModule(GameAnimation.INSTANCE);
        addModule(HandsView.INSTANCE);
        addModule(Hat.INSTANCE);
        addModule(HitParticles.INSTANCE);
        addModule(HoleESP.INSTANCE);
        addModule(JumpCircle.INSTANCE);
        addModule(MasEffects.INSTANCE);
        addModule(NameTags.INSTANCE);
        addModule(NoRender.INSTANCE);
        addModule(Particles.INSTANCE);
        addModule(PopChams.INSTANCE);
        addModule(Shaders.INSTANCE);
        addModule(SneakTweak.INSTANCE);
        addModule(StreamerMode.INSTANCE);
        addModule(ItemPhysics.INSTANCE);
        addModule(Xray.INSTANCE);

    }

    private void addModule(Module module) {
        register("graven", module);
        module.initI18n(GravenTranslateComponent.create("modules", module.getName().toLowerCase()));
    }

    public synchronized void registerAddonModule(String addonId, Module module, TranslateComponent moduleComponent) {
        register(addonId, module);
        module.initI18n(moduleComponent);
    }

    public synchronized ExternalModuleRegistration registerExternal(String ownerId, Module module, TranslateComponent moduleComponent) {
        register(ownerId, module);
        module.initI18n(moduleComponent);
        return new ExternalModuleRegistration(module.getModuleKey(), module);
    }

    /** 在单个客户端线程临界区中替换某 owner 的全部外部 Module。 */
    public synchronized List<ExternalModuleRegistration> replaceExternal(
            String ownerId, List<? extends Module> replacements,
            Function<Module, TranslateComponent> translationFactory) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(replacements, "replacements");
        Objects.requireNonNull(translationFactory, "translationFactory");
        Set<ModuleKey> replacementKeys = new LinkedHashSet<>();
        for (Module module : replacements) {
            Objects.requireNonNull(module, "module");
            module.setAddonId(ownerId);
            ModuleKey key = module.getModuleKey();
            if (!replacementKeys.add(key)) throw new IllegalArgumentException("重复 Module ID: " + key);
            Module conflict = modulesByKey.get(key);
            if (conflict != null && !Objects.equals(conflict.getAddonId(), ownerId)) {
                throw new IllegalArgumentException("Module ID 与其他 owner 冲突: " + key);
            }
        }

        List<Module> previous = modules.stream()
                .filter(module -> Objects.equals(module.getAddonId(), ownerId))
                .toList();
        for (Module module : previous) module.setEnabled(false);
        modules.removeAll(previous);
        previous.forEach(module -> modulesByKey.remove(module.getModuleKey(), module));

        List<ExternalModuleRegistration> registrations = new ArrayList<>();
        for (Module module : replacements) {
            modulesByKey.put(module.getModuleKey(), module);
            modules.add(module);
            module.initI18n(translationFactory.apply(module));
            registrations.add(new ExternalModuleRegistration(module.getModuleKey(), module));
        }
        return List.copyOf(registrations);
    }

    public synchronized Module find(ModuleKey key) {
        return modulesByKey.get(key);
    }

    public synchronized List<Module> getModules() {
        return Collections.unmodifiableList(new ArrayList<>(modules));
    }

    private void register(String ownerId, Module module) {
        Objects.requireNonNull(module, "module");
        module.setAddonId(ownerId);
        ModuleKey key = module.getModuleKey();
        Module existing = modulesByKey.putIfAbsent(key, module);
        if (existing != null) {
            throw new IllegalArgumentException("重复 Module ID: " + key.ownerId() + ":" + key.moduleId());
        }
        modules.add(module);
    }

    public final class ExternalModuleRegistration implements AutoCloseable {
        private final ModuleKey key;
        private final Module module;
        private boolean closed;

        private ExternalModuleRegistration(ModuleKey key, Module module) {
            this.key = key;
            this.module = module;
        }

        public ModuleKey key() {
            return key;
        }

        public Module module() {
            return module;
        }

        @Override
        public void close() {
            synchronized (ModuleHolder.this) {
                if (closed) return;
                closed = true;
                if (modulesByKey.get(key) != module) return;
                module.setEnabled(false);
                modulesByKey.remove(key);
                modules.remove(module);
            }
        }
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (mc.level == null || mc.screen != null || event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;

        int keyCode = event.getKey();
        int action = event.getAction();

        ClientSetting cs = ClientSetting.INSTANCE;
        if (keyCode == cs.guiKeybind.getValue() && action == InputConstants.PRESS) {
            mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
                case Panel -> PanelScreen.INSTANCE;
                case Dropdown -> DropdownScreen.INSTANCE;
            });
        }

        dispatchKeyBind(keyCode, action);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.level != null && mc.screen == null) {
            dispatchKeyBind(KeybindUtils.encodeMouseButton(event.getButton()), event.getAction());
        }
    }

    private void dispatchKeyBind(int keyCode, int action) {
        boolean isPress = action == InputConstants.PRESS;
        boolean isRelease = action == InputConstants.RELEASE;

        List<Module> affectedModules = new ArrayList<>();
        boolean hasEnabling = false;

        for (Module module : modules) {
            if (module.getKeyBind() != keyCode) continue;

            if (module.getBindMode() == Module.BindMode.Toggle && isPress) {
                if (!module.isEnabled()) {
                    hasEnabling = true;
                }
                affectedModules.add(module);
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    hasEnabling = true;
                    affectedModules.add(module);
                } else if (isRelease && module.isEnabled()) {
                    affectedModules.add(module);
                }
            }
        }

        for (Module module : affectedModules) {
            if (module.getBindMode() == Module.BindMode.Toggle) {
                module.toggle();
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    module.setEnabled(true);
                } else if (isRelease && module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
        }

        if (!affectedModules.isEmpty() && ClientSetting.INSTANCE.soundNotify.getValue()) {
            if (hasEnabling) {
                Managers.SOUND.playInUi(SoundKey.ENABLE);
            } else {
                Managers.SOUND.playInUi(SoundKey.DISABLE);
            }
        }
    }

}
