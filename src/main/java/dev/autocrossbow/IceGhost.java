package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class IceGhost {
    public static final IceGhost INSTANCE = new IceGhost();

    /** Ticks to wait before swapping the ice back. 20 ticks = 1 second. */
    private static final int RESTORE_TICKS = 8;

    private boolean lastUseDown;
    private int restoreTimer;

    public void tickStart(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.getNetworkHandler() == null) {
            restoreTimer = 0;
            return;
        }

        boolean useDown = mc.options.useKey.isPressed();
        boolean clicked = useDown && !lastUseDown;
        lastUseDown = useDown;

        if (restoreTimer > 0) {
            restoreTimer--;
            if (restoreTimer == 0 && isIce(player.getOffHandStack())
                    && !isIce(player.getMainHandStack()) && mc.currentScreen == null) {
                sendSwap(mc);
            }
        }

        if (!Features.iceGhost || !clicked || mc.currentScreen != null || player.isSpectator()) {
            return;
        }
        if (!isIce(player.getMainHandStack())) {
            return;
        }

        sendSwap(mc);
        restoreTimer = RESTORE_TICKS;
    }

    private static void sendSwap(MinecraftClient mc) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEMS_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    private static boolean isIce(ItemStack s) {
        return s.isOf(Items.BLUE_ICE) || s.isOf(Items.PACKED_ICE) || s.isOf(Items.ICE);
    }
}
