package tech.hakuri.graven.events.impl;

public final class MouseTurnEvent {

    private final double inputX;
    private final double inputY;
    private double x;
    private double y;

    public MouseTurnEvent(double x, double y) {
        this.inputX = x;
        this.inputY = y;
        this.x = x;
        this.y = y;
    }

    public double getInputX() {
        return inputX;
    }

    public double getInputY() {
        return inputY;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }
}
