package dev.autocrossbow;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * After you jump, places ice under you, lets you land on it briefly, then mines it
 * back so the item returns to your inventory.
 *
 * Steps: wait for the jump to rise one block, select ice (blue ice preferred), place it on
 * the block you jumped from, wait STAY_TICKS, select a pickaxe if you have one in the hotbar,
 * break the ice, restore your hotbar slot.
 *
 * The real server decides everything, so this is an approximation of the technique.
 */
public final class IceGhost {
    public static final IceGhost INSTANCE = new IceGhost();

    private static final int STAY_TICKS = 6;
    private static final int APEX_TIMEOUT = 14;
    private static final int BREAK_TIMEOUT = 30;

    private enum State { IDLE, RISING, SELECTED, STAY, BREAKING }

    private State state = State.IDLE;
    private boolean wasOnGround = true;
    private double takeoffY;
    private BlockPos groundPos;
    private BlockPos icePos;
    private int prevSlot;
    private int timer;

    public void tick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || mc.world == null) {
            state = State.IDLE;
            return;
        }
        boolean onGround = player.isOnGround();

        if (state != State.IDLE && mc.currentScreen != null) {
            finish(player);
            return;
        }

        switch (state) {
            case IDLE -> {
                if (Features.iceGhost && mc.currentScreen == null
                        && wasOnGround && !onGround && player.getVelocity().y > 0.1
                        && findIce(player) >= 0) {
                    takeoffY = player.getY();
                    groundPos = BlockPos.ofFloored(player.getX(), takeoffY - 0.5, player.getZ());
                    prevSlot = player.getInventory().getSelectedSlot();
                    timer = 0;
                    state = State.RISING;
                }
            }
            case RISING -> {
                timer++;
                // Once the feet are a full block up, the block you jumped from is free to place on.
                if (player.getY() - takeoffY >= 1.0) {
                    int ice = findIce(player);
                    if (ice < 0) { finish(player); break; }
                    player.getInventory().setSelectedSlot(ice);
                    state = State.SELECTED;
                } else if (timer > APEX_TIMEOUT) {
                    finish(player);
                }
            }
            case SELECTED -> {
                // One tick has passed so the server knows the selected slot.
                BlockPos at = groundPos.up();
                if (!mc.world.getBlockState(at).isAir()) { finish(player); break; }
                BlockHitResult hit = new BlockHitResult(
                        Vec3d.ofCenter(groundPos).add(0, 0.5, 0), Direction.UP, groundPos, false);
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                player.swingHand(Hand.MAIN_HAND);
                icePos = at;
                timer = 0;
                state = State.STAY;
            }
            case STAY -> {
                timer++;
                if (timer >= STAY_TICKS) {
                    int pick = findPickaxe(player);
                    if (pick >= 0) {
                        player.getInventory().setSelectedSlot(pick);
                        timer = 0;
                        state = State.BREAKING;
                    } else {
                        finish(player); // no pickaxe: leave the ice rather than breaking it slowly
                    }
                }
            }
            case BREAKING -> {
                timer++;
                if (mc.world.getBlockState(icePos).isAir() || timer > BREAK_TIMEOUT) {
                    finish(player);
                    break;
                }
                if (timer == 1) {
                    mc.interactionManager.attackBlock(icePos, Direction.UP);
                } else {
                    mc.interactionManager.updateBlockBreakingProgress(icePos, Direction.UP);
                }
                player.swingHand(Hand.MAIN_HAND);
            }
        }
        wasOnGround = onGround;
    }

    private void finish(ClientPlayerEntity player) {
        player.getInventory().setSelectedSlot(prevSlot);
        state = State.IDLE;
    }

    /** Blue ice first, then packed ice, then ice. */
    private static int findIce(ClientPlayerEntity player) {
        int packed = -1;
        int ice = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(Items.BLUE_ICE)) return i;
            if (s.isOf(Items.PACKED_ICE) && packed < 0) packed = i;
            if (s.isOf(Items.ICE) && ice < 0) ice = i;
        }
        return packed >= 0 ? packed : ice;
    }

    private static int findPickaxe(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isIn(ItemTags.PICKAXES)) return i;
        }
        return -1;
    }
}
