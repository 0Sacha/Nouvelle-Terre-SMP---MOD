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
 * Monnaie physique de Nouvelle Terre, déclinée en coupures de 1 à 100 ◆.
 *
 * Clic droit avec la pile en main = tout le stack est redéposé sur le compte,
 * à hauteur de sa valeur réelle (une pile de 12 billets de 20 dépose 240 ◆).
 */
public class ShardItem extends Item {

    /** Valeur d'un exemplaire, en ◆. */
    private final int valeur;

    public ShardItem(Settings settings, int valeur) {
        super(settings);
        this.valeur = valeur;
    }

    public int getValeur() {
        return valeur;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            int montant = stack.getCount() * valeur;
            String pseudo = sp.getName().getString();
            LocalEconomy.getInstance().depositShards(pseudo, montant);
            user.setStackInHand(hand, ItemStack.EMPTY);
            sp.sendMessage(Text.literal("§a+" + montant + " ◆ §fdéposés sur ton compte §7— solde : §e"
                + LocalEconomy.getInstance().getBalance(pseudo) + " ◆"), true);
            NouvelleTerreBridge.sendBalanceToPlayer(sp);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Monnaie physique de Nouvelle Terre — §e" + valeur + " ◆ §7l'unité"));
        if (stack.getCount() > 1)
            tooltip.add(Text.literal("§7Cette pile vaut §e" + (stack.getCount() * valeur) + " ◆"));
        tooltip.add(Text.literal("§6Clic droit §7pour déposer la pile sur ton compte"));
    }
}
