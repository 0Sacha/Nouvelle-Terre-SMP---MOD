package com.nouvelleterrebridge.economy;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class KillRewards {

    private static final Map<Class<?>, Integer> RECOMPENSES = new HashMap<>();
    static {
        RECOMPENSES.put(Zombie.class, 1);
        RECOMPENSES.put(Skeleton.class, 1);
        RECOMPENSES.put(Spider.class, 1);
        RECOMPENSES.put(CaveSpider.class, 1);
        RECOMPENSES.put(Slime.class, 1);
        RECOMPENSES.put(Drowned.class, 1);
        RECOMPENSES.put(Husk.class, 1);
        RECOMPENSES.put(Stray.class, 1);
        RECOMPENSES.put(Creeper.class, 2);
        RECOMPENSES.put(Witch.class, 2);
        RECOMPENSES.put(Phantom.class, 2);
        RECOMPENSES.put(Pillager.class, 2);
        RECOMPENSES.put(Vindicator.class, 3);
        RECOMPENSES.put(Evoker.class, 5);
        RECOMPENSES.put(Blaze.class, 3);
        RECOMPENSES.put(Ghast.class, 3);
        RECOMPENSES.put(EnderMan.class, 3);
        RECOMPENSES.put(WitherSkeleton.class, 4);
        RECOMPENSES.put(WitherBoss.class, 100);
        RECOMPENSES.put(EnderDragon.class, 200);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (!(event.getSource().getEntity() instanceof ServerPlayer joueur)) return;
            LivingEntity killedEntity = event.getEntity();

            int shards = getRecompense(killedEntity.getClass());
            if (shards <= 0) return;

            String pseudo = joueur.getName().getString();
            LocalEconomy.getInstance().addShards(pseudo, shards,
                "Kill : " + killedEntity.getType().getDescription().getString());
            com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(joueur);

            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                String.format("§6+%d💎§e pour avoir tué §f%s§e !",
                    shards, killedEntity.getType().getDescription().getString())
            ));
        });
    }

    private static int getRecompense(Class<?> mobClass) {
        for (Map.Entry<Class<?>, Integer> entry : RECOMPENSES.entrySet()) {
            if (entry.getKey().isAssignableFrom(mobClass)) return entry.getValue();
        }
        return 0;
    }
}
