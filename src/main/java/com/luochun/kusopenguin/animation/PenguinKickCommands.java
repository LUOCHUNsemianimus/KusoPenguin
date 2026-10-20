package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME)
public final class PenguinKickCommands {
    private PenguinKickCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("flyingkick").executes(c->show(c,List.of(c.getSource().getPlayerOrException())));
        root.then(Commands.literal("get").then(Commands.argument("players",EntityArgument.players())
            .executes(c->show(c,EntityArgument.getPlayers(c,"players")))));
        root.then(Commands.literal("damage").then(Commands.argument("multiplier",DoubleArgumentType.doubleArg(0,PenguinKickSettings.MAX_DAMAGE_MULTIPLIER))
            .executes(c->change(c,List.of(c.getSource().getPlayerOrException()),"damage"))
            .then(Commands.argument("players",EntityArgument.players()).executes(c->change(c,EntityArgument.getPlayers(c,"players"),"damage")))));
        root.then(Commands.literal("knockback").then(Commands.argument("level",IntegerArgumentType.integer(0,PenguinKickSettings.MAX_KNOCKBACK_LEVEL))
            .executes(c->change(c,List.of(c.getSource().getPlayerOrException()),"knockback"))
            .then(Commands.argument("players",EntityArgument.players()).executes(c->change(c,EntityArgument.getPlayers(c,"players"),"knockback")))));
        root.then(Commands.literal("reset").executes(c->change(c,List.of(c.getSource().getPlayerOrException()),"reset"))
            .then(Commands.argument("players",EntityArgument.players()).executes(c->change(c,EntityArgument.getPlayers(c,"players"),"reset"))));
        event.getDispatcher().register(Commands.literal("kusopenguin").requires(s->s.hasPermission(2)).then(root));
    }
    private static int show(CommandContext<CommandSourceStack> c,Collection<ServerPlayer> players){
        var data=PenguinKickSettings.get(c.getSource().getServer());
        for(var p:players){var v=data.values(p.getUUID());c.getSource().sendSuccess(()->Component.translatable(
            "commands.kuso_penguin.flyingkick.values",p.getDisplayName(),v.damageMultiplier(),v.knockbackLevel()),false);}
        return players.size();
    }
    private static int change(CommandContext<CommandSourceStack> c,Collection<ServerPlayer> players,String setting) throws CommandSyntaxException {
        var data=PenguinKickSettings.get(c.getSource().getServer());
        for(var p:players){
            var old=data.values(p.getUUID());
            var next=switch(setting){
                case "damage"->new PenguinKickSettings.Values(DoubleArgumentType.getDouble(c,"multiplier"),old.knockbackLevel());
                case "knockback"->new PenguinKickSettings.Values(old.damageMultiplier(),IntegerArgumentType.getInteger(c,"level"));
                case "reset"->PenguinKickSettings.DEFAULT;
                default->throw new IllegalArgumentException(setting);
            };
            data.set(p.getUUID(),next);
            c.getSource().sendSuccess(()->Component.translatable("commands.kuso_penguin.flyingkick.updated",
                p.getDisplayName(),next.damageMultiplier(),next.knockbackLevel()),true);
        }
        return players.size();
    }
}
