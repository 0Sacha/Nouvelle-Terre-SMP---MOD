package com.nouvelleterrebridge.economy;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class KillRewards {

    private static final Map<Class<?>, Integer> RECOMPENSES = new HashMap<>();
    static {
        RECOMPENSES.put(ZombieEntity.class, 1);
        RECOMPENSES.put(SkeletonEntity.class, 1);
        RECOMPENSES.put(SpiderEntity.class, 1);
        RECOMPENSES.put(CaveSpiderEntity.class, 1);
        RECOMPENSES.put(SlimeEntity.class, 1);
        RECOMPENSES.put(DrownedEntity.class, 1);
        RECOMPENSES.put(HuskEntity.class, 1);
        RECOMPENSES.put(StrayEntity.class, 1);
        RECOMPENSES.put(CreeperEntity.class, 2);
        RECOMPENSES.put(WitchEntity.class, 2);
        RECOMPENSES.put(PhantomEntity.class, 2);
        RECOMPENSES.put(PillagerEntity.class, 2);
        RECOMPENSES.put(VindicatorEntity.class, 3);
        RECOMPENSES.put(EvokerEntity.class, 5);
        RECOMPENSES.put(BlazeEntity.class, 3);
        RECOMPENSES.put(GhastEntity.class, 3);
        RECOMPENSES.put(EndermanEntity.class, 3);
        RECOMPENSES.put(WitherSkeletonEntity.class, 4);
        RECOMPENSES.put(WitherEntity.class, 100);
        RECOMPENSES.put(EnderDragonEntity.class, 200);
    }

    public static void register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity joueur)) return;
            if (!(killedEntity instanceof LivingEntity)) return;

            int shards = getRecompense(killedEntity.getClass());
            if (shards <= 0) return;

            String pseudo = joueur.getName().getString();
            LocalEconomy.getInstance().addShards(pseudo, shards);
            com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(joueur);

            joueur.sendMessage(net.minecraft.text.Text.literal(
                String.format("§6+%d💎§e pour avoir tué §f%s§e !",
                    shards, killedEntity.getType().getName().getString())
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
