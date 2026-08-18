package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.Random;

public final class Regen extends Module {

    public static final Regen INSTANCE = new Regen();

    private enum Mode { Normal, AntiCheat }

    private final DoubleSetting health = doubleSetting("Health", 10, 0, 20, 1);
    private final IntSetting packetsPerTick = intSetting("Packets/Tick", 5, 2, 20, 1);
    private final EnumSetting<Mode> regenMode = enumSetting("Regen Mode", Mode.Normal);
    private final IntSetting minPackets = intSetting("Min Packets", 3, 1, 10, 1);
    private final IntSetting maxPackets = intSetting("Max Packets", 8, 2, 20, 1);
    private final IntSetting packetDelayMin = intSetting("Delay Min", 10, 0, 100, 5);
    private final IntSetting packetDelayMax = intSetting("Delay Max", 40, 0, 100, 5);
    private final BoolSetting randomizePackets = boolSetting("Randomize Packets", true);
    private final BoolSetting randomizePosition = boolSetting("Randomize Position", true);
    private final DoubleSetting positionOffset = doubleSetting("Position Offset", 0.001, 0.0, 0.01, 0.0005);
    private final BoolSetting simulateReaction = boolSetting("Simulate Reaction", true);
    private final IntSetting reactionDelay = intSetting("Reaction Delay", 300, 0, 1500, 50);
    private final BoolSetting simulateKeyJitter = boolSetting("Simulate Key Jitter", true);
    private final IntSetting pauseChance = intSetting("Pause Chance", 10, 0, 30, 5);

    private final Random random = new Random();
    private long reactionTime;
    private long packetTime;
    private long pauseTime;
    private boolean hasReacted;
    private int currentPacketsToSend;
    private int packetsSentThisRound;
    private boolean paused;

    private Regen() {
        super("Regen", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;
        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (currentHealth > health.getValue()) {
            hasReacted = false;
            return;
        }
        if (regenMode.is(Mode.AntiCheat)) processAntiCheat();
        else for (int i = 0; i < packetsPerTick.getValue(); i++) sendMovePacket(false);
    }

    private void processAntiCheat() {
        long now = System.currentTimeMillis();
        if (simulateReaction.getValue() && !hasReacted) {
            if (now - reactionTime < reactionDelay.getValue()) return;
            hasReacted = true;
            currentPacketsToSend = getRandomPackets();
            packetsSentThisRound = 0;
            packetTime = now;
            return;
        }
        if (paused) {
            if (now - pauseTime < 40 + random.nextInt(80)) return;
            paused = false;
            currentPacketsToSend = getRandomPackets();
            packetsSentThisRound = 0;
            packetTime = now;
            return;
        }
        if (packetsSentThisRound >= currentPacketsToSend) {
            if (random.nextInt(100) < pauseChance.getValue()) {
                paused = true;
                pauseTime = now;
                return;
            }
            currentPacketsToSend = getRandomPackets();
            packetsSentThisRound = 0;
            packetTime = now;
            return;
        }
        int min = packetDelayMin.getValue();
        int max = packetDelayMax.getValue();
        long interval = min + (max > min ? random.nextInt(max - min) : 0);
        if (now - packetTime < interval) return;
        sendMovePacket(true);
        packetsSentThisRound++;
        packetTime = now;
    }

    private void sendMovePacket(boolean withRandomOffset) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        boolean horizontalCollision = mc.player.horizontalCollision;
        if (withRandomOffset && randomizePosition.getValue()) {
            double offset = positionOffset.getValue();
            x += (random.nextDouble() - 0.5) * offset;
            y += (random.nextDouble() - 0.5) * offset * 0.3;
            z += (random.nextDouble() - 0.5) * offset;
            yaw += (random.nextFloat() - 0.5F) * 0.3F;
            pitch += (random.nextFloat() - 0.5F) * 0.2F;
        }
        if (withRandomOffset && simulateKeyJitter.getValue() && random.nextInt(100) < 8) {
            horizontalCollision = !horizontalCollision;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                x, y, z, yaw, pitch, mc.player.onGround(), horizontalCollision));
    }

    private int getRandomPackets() {
        if (!randomizePackets.getValue()) return packetsPerTick.getValue();
        int min = minPackets.getValue();
        int max = maxPackets.getValue();
        return min >= max ? min : min + random.nextInt(max - min);
    }

    private void resetState() {
        hasReacted = false;
        packetsSentThisRound = 0;
        currentPacketsToSend = 0;
        paused = false;
        reactionTime = packetTime = pauseTime = System.currentTimeMillis();
    }
}
