package tech.hakuri.graven.managers.impl;

import tech.hakuri.graven.managers.Managers;

/** OpenZen serverTickRate 的独立拥有者，避免覆盖 TimerManager 的用户设置。 */
public final class OpenZenTickRateController {
    private float requested = 1.0f;

    public void set(float factor) {
        requested = factor;
        if (Managers.TIMER != null) Managers.TIMER.set(factor);
    }

    public void reset() {
        requested = 1.0f;
        if (Managers.TIMER != null) Managers.TIMER.tryReset();
    }

    public float get() {
        return requested;
    }
}
