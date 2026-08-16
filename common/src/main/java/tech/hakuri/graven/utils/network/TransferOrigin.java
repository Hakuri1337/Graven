package tech.hakuri.graven.utils.network;

public enum TransferOrigin {
    INCOMING("Incoming"),
    OUTGOING("Outgoing");

    private final String settingName;

    TransferOrigin(String settingName) {
        this.settingName = settingName;
    }

    public String settingName() {
        return settingName;
    }
}
