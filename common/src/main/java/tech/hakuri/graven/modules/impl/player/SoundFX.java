package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.AttackEntityEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.sound.SoundKey;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.math.MathUtils;
import tech.hakuri.graven.utils.timer.TimerUtils;

public class SoundFX extends Module {

    public static final SoundFX INSTANCE = new SoundFX();

    private SoundFX() {
        super("Sound FX", Category.PLAYER);
    }

    private enum HitSound {
        UwU,
        Nya,
        Moan,
        OFF
    }

    private final IntSetting volume = intSetting("Volume", 100, 1, 100, 5);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final EnumSetting<HitSound> hitSound = enumSetting("Hit Sound", HitSound.OFF);

    private final TimerUtils timer = new TimerUtils();

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (timer.hasDelayed(delay.getValue())) {
            playHitSound(hitSound.getValue());
            timer.reset();
        }
    }

    private void playHitSound(HitSound value) {
        switch (value) {
            case UwU -> playSound(SoundKey.UWU);
            case Nya -> playSound(SoundKey.NYA);
            case Moan -> {
                SoundKey sound = switch (MathUtils.getRandom(0, 3)) {
                    case 0 -> SoundKey.MOAN1;
                    case 1 -> SoundKey.MOAN2;
                    case 2 -> SoundKey.MOAN3;
                    default -> SoundKey.MOAN4;
                };
                playSound(sound);
            }
        }
    }

    private void playSound(SoundKey key) {
        Managers.SOUND.playSound(key, volume.getValue().floatValue() / 100.0f);
    }

}
