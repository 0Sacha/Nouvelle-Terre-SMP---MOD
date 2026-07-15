package com.nouvelleterrebridge.item;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Shard ◆ — monnaie physique de Nouvelle Terre.
 * 1 item = 1 ◆. Retirable du compte via /bank ("Retirer en Shards"),
 * clic droit avec le stack en main = tout le stack est redéposé sur le compte.
 */
public class ShardItem extends Item {

    public ShardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            int count = stack.getCount();
            String pseudo = sp.getName().getString();
            LocalEconomy.getInstance().depositShards(pseudo, count);
            user.setStackInHand(hand, ItemStack.EMPTY);
            sp.sendMessage(Text.literal("§a+" + count + " ◆ §fdéposés sur ton compte §7— solde : §e"
                + LocalEconomy.getInstance().getBalance(pseudo) + " ◆"), true);
            NouvelleTerreBridge.sendBalanceToPlayer(sp);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Monnaie physique de Nouvelle Terre — §e1 = 1 ◆"));
        tooltip.add(Text.literal("§6Clic droit §7pour déposer le stack sur ton compte"));
    }
}
