package com.luochun.kusopenguin.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Adds {@code /kusopenguin dances} in a separate command event. The existing root is merged
 * instead of being replaced, so the flying-kick, disc and vocal commands keep their nodes.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DanceCommands {
    private DanceCommands() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> dances = Commands.literal("dances")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                DanceServer.open(player);
                context.getSource().sendSuccess(
                    () -> Component.translatable("commands.kuso_penguin.dances.opened"), false);
                return 1;
            });
        CommandNode<CommandSourceStack> root =
            event.getDispatcher().getRoot().getChild("kusopenguin");
        if (root != null) {
            root.addChild(dances.build());
        } else {
            event.getDispatcher().register(Commands.literal("kusopenguin").then(dances));
        }
    }
}
