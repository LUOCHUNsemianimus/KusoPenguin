package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.vocals.VocalServer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Attaches the normal-player disc command below the existing /kusopenguin root. */
public final class DiscCommands {
    private DiscCommands() {
    }

    /**
     * The shared root deliberately has no permission requirement; only the flying-kick child
     * keeps its original OP2 restriction.
     */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("disc").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DiscServer.open(player);
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.kuso_penguin.disc.opened"), false);
            return 1;
        }));
        root.then(Commands.literal("vocals").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            VocalServer.open(player);
            return 1;
        }));
    }
}
