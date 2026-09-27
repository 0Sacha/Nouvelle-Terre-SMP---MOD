package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * /lier — génère un code à 6 chiffres à entrer dans Discord (/link <code>)
 * pour lier son compte Minecraft à son profil Discord.
 */
public class LierCommand {

    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("discord")
                .executes(ctx -> executerLier(ctx.getSource()))
        );
    }

    private static int executerLier(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer joueur)) {
            source.sendFailure(Component.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        Map<String, Object> data = new HashMap<>();
        data.put("pseudo", pseudo);
        data.put("code", code);
        EventDispatcher.envoyer("LINK_REQUEST", data);

        MutableComponent codeCliquable = Component.literal("§f§l" + code)
            .withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("§7Cliquer pour copier")))
            );

        joueur.sendSystemMessage(Component.literal(EconomieCommand.SEP_GOLD));
        joueur.sendSystemMessage(Component.literal("    §6§l🔗 §f§lLiaison Discord"));
        joueur.sendSystemMessage(Component.literal("  §7Ton code : ").append(codeCliquable));
        joueur.sendSystemMessage(Component.literal("  §7Tape §f/link <code> §7sur Discord. §eValide 10 min."));
        joueur.sendSystemMessage(Component.literal(EconomieCommand.SEP_GOLD));
        return 1;
    }
}
