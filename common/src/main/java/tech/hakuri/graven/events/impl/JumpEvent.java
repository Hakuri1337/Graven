package tech.hakuri.graven.events.impl;

import tech.hakuri.graven.events.bus.Cancellable;

public class JumpEvent extends Cancellable {

    private float yaw;

    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
