package tech.hakuri.graven.events.impl;

import tech.hakuri.graven.events.bus.Cancellable;

public class MouseScrollEvent extends Cancellable {

    private final double value;

    public MouseScrollEvent(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

}
