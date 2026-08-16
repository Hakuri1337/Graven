package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.player.ClickSlotUtils;
import tech.hakuri.graven.utils.player.InvHelper;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.openzen.OpenZenInputGate;
import tech.hakuri.graven.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class InvManager extends Module {

    public static final InvManager INSTANCE = new InvManager();

    private enum OffhandItemMode {
        None,
        GoldenApple,
        Projectile,
        FishingRod,
        Block
    }

    private enum BowPriorityMode {
        Crossbow,
        PowerBow,
        PunchBow
    }

    private final IntSetting minDelay = intSetting("Min Delay", 50, 0, 1000, 50);
    private final IntSetting delay = intSetting("Delay", 50, 0, 1000, 50);
    private final EnumSetting<OffhandItemMode> offhandItems = enumSetting("Offhand Items", OffhandItemMode.None);
    private final BoolSetting autoArmor = boolSetting("Auto Armor", true);
    private final BoolSetting inventoryOnly = boolSetting("Inventory Only", true);
    private final BoolSetting switchSword = boolSetting("Switch Sword", true);
    private final IntSetting swordSlot = intSetting("Sword Slot", 1, 1, 9, 1, switchSword::getValue);
    private final BoolSetting switchBlock = boolSetting("Switch Block", true, () -> !offhandItems.is(OffhandItemMode.Block));
    private final IntSetting blockSlot = intSetting("Block Slot", 2, 1, 9, 1, () -> switchBlock.getValue() && !offhandItems.is(OffhandItemMode.Block));
    public final IntSetting maxBlockSize = intSetting("Max Block Size", 256, 64, 512, 64, switchBlock::getValue);
    private final BoolSetting switchPickaxe = boolSetting("Switch Pickaxe", true);
    private final IntSetting pickaxeSlot = intSetting("Pickaxe Slot", 3, 1, 9, 1, switchPickaxe::getValue);
    private final BoolSetting switchAxe = boolSetting("Switch Axe", true);
    private final IntSetting axeSlot = intSetting("Axe Slot", 4, 1, 9, 1, switchAxe::getValue);
    private final BoolSetting switchBow = boolSetting("Switch Bow or Crossbow", true);
    private final IntSetting bowSlot = intSetting("Bow Slot", 5, 1, 9, 1, switchBow::getValue);
    private final EnumSetting<BowPriorityMode> preferBow = enumSetting("Bow Priority", BowPriorityMode.Crossbow, switchBow::getValue);
    public final IntSetting maxArrowSize = intSetting("Max Arrow Size", 256, 64, 512, 64, switchBow::getValue);
    private final BoolSetting switchWaterBucket = boolSetting("Switch Water Bucket", true);
    private final IntSetting waterBucketSlot = intSetting("Water Bucket Slot", 6, 1, 9, 1, switchWaterBucket::getValue);
    private final BoolSetting switchEnderPearl = boolSetting("Switch Ender Pearl", true);
    private final IntSetting enderPearlSlot = intSetting("Ender Pearl Slot", 7, 1, 9, 1, switchEnderPearl::getValue);
    private final BoolSetting switchFireball = boolSetting("Switch Fireball", true);
    private final IntSetting fireballSlot = intSetting("Fireball Slot", 8, 1, 9, 1, switchFireball::getValue);
    private final BoolSetting switchGoldenApple = boolSetting("Switch Golden Apple", true, () -> !offhandItems.is(OffhandItemMode.GoldenApple));
    private final IntSetting goldenAppleSlot = intSetting("Golden Apple Slot", 9, 1, 9, 1, () -> switchGoldenApple.getValue() && !offhandItems.is(OffhandItemMode.GoldenApple));
    private final BoolSetting throwItems = boolSetting("Throw Items", true);
    public final IntSetting waterBucketCount = intSetting("Keep Water Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final IntSetting lavaBucketCount = intSetting("Keep Lava Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final BoolSetting keepProjectile = boolSetting("Keep Eggs & Snowballs", true);
    private final BoolSetting switchProjectile = boolSetting("Switch Eggs & Snowballs", false, () -> keepProjectile.getValue() && !offhandItems.is(OffhandItemMode.Projectile));
    private final IntSetting projectileSlot = intSetting("Eggs & Snowballs Slot", 9, 1, 9, 1, () -> switchProjectile.getValue() && keepProjectile.getValue() && !offhandItems.is(OffhandItemMode.Projectile));
    public final IntSetting maxProjectileSize = intSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16, keepProjectile::getValue);
    private final BoolSetting switchRod = boolSetting("Switch Rod", false, () -> !offhandItems.is(OffhandItemMode.FishingRod));
    private final IntSetting rodSlot = intSetting("Rod Slot", 9, 1, 9, 1, () -> switchRod.getValue() && !offhandItems.is(OffhandItemMode.FishingRod));

    private static final TimerUtils organizeTimer = new TimerUtils();
    private static final TimerUtils throwTimer = new TimerUtils();
    private static final Random random = new Random();

    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;
    private boolean automationCapture;
    private Packet<?> pendingClick;
    private ActionPhase actionPhase = ActionPhase.IDLE;
    private long actionTick;
    private long clickReleasedTick;
    private long closeReleasedTick;
    private long tickCounter;

    private enum ActionPhase {
        IDLE, INPUT_NEUTRAL, CLICK_RELEASED, CLOSE_RELEASED
    }

    private InvManager() {
        super("Inv Manager", Category.PLAYER);
    }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InvHelper.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InvHelper.isArmor(stack)) {
            float protection = InvHelper.getProtection(stack);
            if (InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)) >= protection) return false;
            float bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack));
            return !(protection < bestArmor);
        }
        if (InvHelper.isSword(stack)) return InvHelper.getBestSword() == stack;
        if (InvHelper.isPickaxe(stack)) return InvHelper.getBestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !InvHelper.isSharpnessAxe(stack))
            return InvHelper.getBestAxe() == stack;
        if (stack.getItem() instanceof ShovelItem) return InvHelper.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InvHelper.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack))
            return InvHelper.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack))
            return InvHelper.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) > waterBucketCount.getValue())
            return false;
        if (stack.getItem() == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue())
            return false;
        if (stack.getItem() instanceof FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) > 1)
            return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.getValue())
            return false;
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundContainerClickPacket && this.automationCapture) {
            event.cancel();
            if (this.actionPhase == ActionPhase.IDLE && this.pendingClick == null) {
                this.pendingClick = packet;
                this.actionTick = this.tickCounter;
                this.actionPhase = ActionPhase.INPUT_NEUTRAL;
                this.inventoryOpen = true;
                OpenZenInputGate.setNeutral(true);
                OpenZenInputGate.setSuppressSprint(true);
            }
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket) {
            if (this.actionPhase != ActionPhase.IDLE) event.cancel();
            else this.inventoryOpen = false;
        }
    }

    @EventHandler(priority = 150)
    private void onKeyboardInput(tech.hakuri.graven.events.impl.KeyboardInputEvent event) {
        if (this.actionPhase == ActionPhase.INPUT_NEUTRAL) OpenZenInputGate.apply(event);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundOpenScreenPacket || packet instanceof ClientboundContainerSetContentPacket) {
            clearActionState();
        }
    }

    private boolean checkConfig() {
        List<Pair<BoolSetting, IntSetting>> pairs = new ArrayList<>();
        if (!this.keepProjectile.getValue()) this.switchProjectile.setValue(false);
        pairs.add(Pair.of(this.switchSword, this.swordSlot));
        pairs.add(Pair.of(this.switchPickaxe, this.pickaxeSlot));
        pairs.add(Pair.of(this.switchAxe, this.axeSlot));
        pairs.add(Pair.of(this.switchBow, this.bowSlot));
        pairs.add(Pair.of(this.switchWaterBucket, this.waterBucketSlot));
        pairs.add(Pair.of(this.switchEnderPearl, this.enderPearlSlot));
        pairs.add(Pair.of(this.switchFireball, this.fireballSlot));
        if (!this.offhandItems.is(OffhandItemMode.GoldenApple))
            pairs.add(Pair.of(this.switchGoldenApple, this.goldenAppleSlot));
        if (!this.offhandItems.is(OffhandItemMode.Projectile))
            pairs.add(Pair.of(this.switchProjectile, this.projectileSlot));
        if (!this.offhandItems.is(OffhandItemMode.FishingRod)) pairs.add(Pair.of(this.switchRod, this.rodSlot));
        if (!this.offhandItems.is(OffhandItemMode.Block)) pairs.add(Pair.of(this.switchBlock, this.blockSlot));
        Set<Integer> usedSlot = new HashSet<>();
        for (Pair<BoolSetting, IntSetting> pair : pairs) {
            if (pair.getKey().getValue()) {
                int targetSlot = pair.getValue().getValue() - 1;
                if (usedSlot.contains(targetSlot)) return false;
                usedSlot.add(targetSlot);
            }
        }
        return true;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        this.tickCounter++;
        if (advanceActionState()) return;
        if (InvHelper.shouldDisableFeatures()) return;
        if (mc.player.isMoving()) this.noMoveTicks = 0;
        else this.noMoveTicks++;
        boolean allowMove = !this.inventoryOnly.getValue();
        if (Stealer.INSTANCE.isWorking() || (this.inventoryOnly.getValue() ? !(mc.screen instanceof InventoryScreen) : (!allowMove && this.noMoveTicks <= 1))) {
            this.clickOffHand = false;
            return;
        }
        // Movement is allowed here. The action state machine captures the click,
        // neutralizes input for the release tick, and sends the close packet later.
        if (mc.screen instanceof AbstractContainerScreen container && container.getMenu().containerId != mc.player.inventoryMenu.containerId)
            return;
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        int throwDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));

        if (this.autoArmor.getValue()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < armorSlots.length; i++) {
                ItemStack stack = InvHelper.getArmorStack(armorSlots[i]);
                if (InvHelper.isArmor(stack)) {
                    if (!stack.isEmpty() && organizeTimer.passedMillise(nextDelay) && InvHelper.getBestArmorScore(armorSlots[i]) > InvHelper.getProtection(stack)) {
                        this.dropAll(4 + (4 - i));
                        this.inventoryOpen = true;
                        organizeTimer.reset();
                    }
                }
            }
            for (int ix = 0; ix < mc.player.getInventory().getNonEquipmentItems().size(); ix++) {
                ItemStack stack = InvHelper.getInventoryStack(ix);
                if (!stack.isEmpty() && InvHelper.isArmor(stack)) {
                    float currentItemScore = InvHelper.getProtection(stack);
                    boolean isBestItem = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)) == currentItemScore;
                    boolean isBetterItem = InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)) < currentItemScore;
                    if (isBestItem && isBetterItem && organizeTimer.passedMillise(nextDelay)) {
                        if (ix < 9)
                            this.shiftClick(ix + 36);
                        else
                            this.shiftClick(ix);
                        this.inventoryOpen = true;
                        organizeTimer.reset();
                    }
                }
            }
        }

        if (this.clickOffHand && organizeTimer.passedMillise(nextDelay)) {
            this.click(45);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            organizeTimer.reset();
        }

        if (this.offhandItems.is(OffhandItemMode.GoldenApple)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            Item offHandItem = offHand.getItem();

            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);

            if (offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                if (offHand.getCount() < offHand.getMaxStackSize() && egapSlot != -1) {
                    if (organizeTimer.passedMillise(nextDelay)) {
                        int targetSlot = egapSlot;
                        if (targetSlot < 9)
                            this.click(targetSlot + 36);
                        else
                            this.click(targetSlot);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        organizeTimer.reset();
                    }
                }
            } else if (offHandItem == Items.GOLDEN_APPLE) {
                if (egapSlot != -1) {
                    if (organizeTimer.passedMillise(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        organizeTimer.reset();
                    }
                } else if (offHand.getCount() < offHand.getMaxStackSize() && gapSlot != -1) {
                    if (organizeTimer.passedMillise(nextDelay)) {
                        int targetSlot = gapSlot;
                        if (targetSlot < 9)
                            this.click(targetSlot + 36);
                        else
                            this.click(targetSlot);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        organizeTimer.reset();
                    }
                }
            } else {
                if (egapSlot != -1) {
                    if (organizeTimer.passedMillise(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        organizeTimer.reset();
                    }
                } else if (gapSlot != -1) {
                    if (organizeTimer.passedMillise(nextDelay)) {
                        this.swapOffHand(gapSlot);
                        organizeTimer.reset();
                    }
                }
            }
        } else if (this.offhandItems.is(OffhandItemMode.Projectile)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestProjectile = InvHelper.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InvHelper.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && organizeTimer.passedMillise(nextDelay)) this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is(OffhandItemMode.FishingRod)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            int slotx = InvHelper.getItemSlot(Items.FISHING_ROD);
            if (slotx != -1 && organizeTimer.passedMillise(nextDelay) && offHand.getItem() != Items.FISHING_ROD)
                this.swapOffHand(slotx);
        } else if (this.offhandItems.is(OffhandItemMode.Block)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null) {
                int slotx = InvHelper.getItemStackSlot(bestBlock);
                boolean shouldSwapx = !InvHelper.isValidStack(offHand) || offHand.getCount() < bestBlock.getCount();
                if (shouldSwapx && slotx != -1 && organizeTimer.passedMillise(nextDelay)) this.swapOffHand(slotx);
            }
        }

        if (this.switchGoldenApple.getValue() && !this.offhandItems.is(OffhandItemMode.GoldenApple)) {
            int targetSlotIdx = this.goldenAppleSlot.getValue() - 1;
            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
            int bestGapSlot = (egapSlot != -1) ? egapSlot : gapSlot;
            if (bestGapSlot != -1) {
                ItemStack currentInSlot = InvHelper.getInventoryStack(targetSlotIdx);
                ItemStack bestGapItem = InvHelper.getInventoryStack(bestGapSlot);
                if (currentInSlot.getItem() != bestGapItem.getItem() || (currentInSlot.getCount() < bestGapItem.getCount() && currentInSlot.getItem() == bestGapItem.getItem())) {
                    this.swapItem(targetSlotIdx, bestGapItem);
                }
            }
        }

        if (this.switchBlock.getValue()) {
            int blockSlotIndex = this.blockSlot.getValue() - 1;
            ItemStack currentBlock = InvHelper.getInventoryStack(blockSlotIndex);
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || !InvHelper.isValidStack(currentBlock)) && !this.offhandItems.is(OffhandItemMode.Block)) {
                this.swapItem(blockSlotIndex, bestBlock);
            }
            if ((float) InvHelper.getBlockCountInInventory() > this.maxBlockSize.getValue()) {
                this.throwItem(InvHelper.getWorstBlock());
            }
        }

        if (this.switchSword.getValue()) {
            int slotIndex = this.swordSlot.getValue() - 1;
            ItemStack currentSword = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestSword = InvHelper.getBestSword();
            ItemStack bestShapeAxe = InvHelper.getBestShapeAxe();
            if (InvHelper.getAxeDamage(bestShapeAxe) > InvHelper.getSwordDamage(bestSword))
                bestSword = bestShapeAxe;
            if (bestSword != null) {
                float currentDamage = InvHelper.isSword(currentSword) ? InvHelper.getSwordDamage(currentSword) : InvHelper.getAxeDamage(currentSword);
                float bestWeaponDamage = InvHelper.isSword(bestSword) ? InvHelper.getSwordDamage(bestSword) : InvHelper.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) this.swapItem(slotIndex, bestSword);
            }
        }

        if (this.switchPickaxe.getValue()) {
            int slotIndex = this.pickaxeSlot.getValue() - 1;
            ItemStack bestPickaxe = InvHelper.getBestPickaxe();
            ItemStack currentPickaxe = InvHelper.getInventoryStack(slotIndex);
            if (InvHelper.isPickaxe(bestPickaxe) && (InvHelper.getToolScore(bestPickaxe) > InvHelper.getToolScore(currentPickaxe) || !InvHelper.isPickaxe(currentPickaxe)))
                this.swapItem(slotIndex, bestPickaxe);
        }

        if (this.switchAxe.getValue()) {
            int slotIndex = this.axeSlot.getValue() - 1;
            ItemStack bestAxe = InvHelper.getBestAxe();
            ItemStack currentAxe = InvHelper.getInventoryStack(slotIndex);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InvHelper.getToolScore(bestAxe) > InvHelper.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem)))
                this.swapItem(slotIndex, bestAxe);
        }

        if (this.switchRod.getValue() && !this.offhandItems.is(OffhandItemMode.FishingRod)) {
            int slotIndex = this.rodSlot.getValue() - 1;
            ItemStack bestRod = InvHelper.getFishingRod();
            ItemStack currentRod = InvHelper.getInventoryStack(slotIndex);
            if (!(currentRod.getItem() instanceof FishingRodItem)) this.swapItem(slotIndex, bestRod);
        }

        if (this.switchBow.getValue()) {
            int slotIndex = this.bowSlot.getValue() - 1;
            ItemStack currentBow = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestBow;
            float bestScore, currentScore;
            if (this.preferBow.is(BowPriorityMode.Crossbow)) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            } else if (this.preferBow.is(BowPriorityMode.PowerBow)) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            } else {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestScore > currentScore) this.swapItem(slotIndex, bestBow);
            if ((float) InvHelper.getItemCount(Items.ARROW) > this.maxArrowSize.getValue())
                this.throwItem(InvHelper.getWorstArrow());
        }

        if (this.switchEnderPearl.getValue())
            this.swapItem(this.enderPearlSlot.getValue() - 1, Items.ENDER_PEARL);
        if (this.switchWaterBucket.getValue())
            this.swapItem(this.waterBucketSlot.getValue() - 1, Items.WATER_BUCKET);
        if (this.switchFireball.getValue())
            this.swapItem(this.fireballSlot.getValue() - 1, Items.FIRE_CHARGE);

        if (this.keepProjectile.getValue()) {
            if ((float) (InvHelper.getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL)) > this.maxProjectileSize.getValue())
                this.throwItem(InvHelper.getWorstProjectile());
            if (this.switchProjectile.getValue() && !this.offhandItems.is(OffhandItemMode.Projectile)) {
                int pSlot = this.projectileSlot.getValue() - 1;
                if (InvHelper.getItemCount(Items.EGG) > 0) this.swapItem(pSlot, Items.EGG);
                else if (InvHelper.getItemCount(Items.SNOWBALL) > 0) this.swapItem(pSlot, Items.SNOWBALL);
            }
        }

        if (this.throwItems.getValue()) {
            List<Integer> slots = IntStream.range(0, mc.player.getInventory().getNonEquipmentItems().size()).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slotIdx : slots) {
                ItemStack stack = InvHelper.getInventoryStack(slotIdx);
                if (!stack.isEmpty() && !this.isItemUseful(stack) && throwTimer.passedMillise(throwDelay)) {
                    this.throwItem(stack);
                    throwTimer.reset();
                    return;
                }
            }
        }

    }

    private void swapOffHand(int slot) {
        if (slot < 9) {
            this.swap(slot + 36, 40);
        } else {
            this.swap(slot, 40);
        }
        this.inventoryOpen = true;
        organizeTimer.reset();
    }

    private boolean advanceActionState() {
        if (this.actionPhase == ActionPhase.INPUT_NEUTRAL) {
            if (this.tickCounter <= this.actionTick) return true;
            Packet<?> click = this.pendingClick;
            this.pendingClick = null;
            if (click != null) PacketUtils.sendSilently(click);
            this.clickReleasedTick = this.tickCounter;
            this.actionPhase = ActionPhase.CLICK_RELEASED;
            return true;
        }
        if (this.actionPhase == ActionPhase.CLICK_RELEASED) {
            if (this.tickCounter <= this.clickReleasedTick) return true;
            if (mc.getConnection() != null && mc.player != null) {
                PacketUtils.sendSilently(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }
            this.closeReleasedTick = this.tickCounter;
            this.actionPhase = ActionPhase.CLOSE_RELEASED;
            return true;
        }
        if (this.actionPhase == ActionPhase.CLOSE_RELEASED) {
            if (this.tickCounter <= this.closeReleasedTick) return true;
            clearActionState();
            return true;
        }
        return false;
    }

    private void clearActionState() {
        this.pendingClick = null;
        this.actionPhase = ActionPhase.IDLE;
        this.inventoryOpen = false;
        this.automationCapture = false;
        OpenZenInputGate.restoreAll();
    }

    private void runAutomationClick(Runnable action) {
        if (this.actionPhase != ActionPhase.IDLE) return;
        this.automationCapture = true;
        try {
            action.run();
        } finally {
            this.automationCapture = false;
        }
    }

    private void click(int slot) {
        runAutomationClick(() -> ClickSlotUtils.click(slot));
    }

    private void shiftClick(int slot) {
        runAutomationClick(() -> ClickSlotUtils.shiftClick(slot));
    }

    private void dropAll(int slot) {
        runAutomationClick(() -> ClickSlotUtils.dropAll(slot));
    }

    private void swap(int slot, int hotbarSlot) {
        runAutomationClick(() -> ClickSlotUtils.swap(slot, hotbarSlot));
    }

    private void throwItem(ItemStack item) {
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(item) && throwTimer.passedMillise(nextDelay)) {
            int itemSlot = InvHelper.getItemStackSlot(item);
            if (itemSlot != -1) {
                if (itemSlot < 9) {
                    this.dropAll(itemSlot + 36);
                } else {
                    this.dropAll(itemSlot);
                }
                this.inventoryOpen = true;
                throwTimer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(currentSlot) && bestItem != currentSlot && organizeTimer.passedMillise(nextDelay)) {
            int bestItemSlot = InvHelper.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    this.swap(bestItemSlot + 36, targetSlot);
                } else {
                    this.swap(bestItemSlot, targetSlot);
                }
                this.inventoryOpen = true;
                organizeTimer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(currentSlot) && organizeTimer.passedMillise(nextDelay)) {
            int bestItemSlot = InvHelper.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = InvHelper.getInventoryStack(bestItemSlot);
                if (currentSlot.getItem() != item || currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount()) {
                    if (bestItemSlot < 9) {
                        this.swap(bestItemSlot + 36, targetSlot);
                    } else {
                        this.swap(bestItemSlot, targetSlot);
                    }
                    this.inventoryOpen = true;
                    organizeTimer.reset();
                }
            }
        }
    }

}
