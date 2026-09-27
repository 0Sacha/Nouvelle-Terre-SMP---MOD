package com.nouvelleterrebridge.item;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.TransactionLog;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

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

    public ShardItem(Properties settings, int valeur) {
        super(settings);
        this.valeur = valeur;
    }

    public int getValeur() {
        return valeur;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!world.isClientSide && user instanceof ServerPlayer sp) {
            int montant = stack.getCount() * valeur;
            String pseudo = sp.getName().getString();
            LocalEconomy.getInstance().depositShards(pseudo, montant);
            TransactionLog.log(pseudo, TransactionLog.TYPE_TRANSFER_IN,
                "Dépôt de Shards physiques", montant);
            user.setItemInHand(hand, ItemStack.EMPTY);
            sp.displayClientMessage(Component.literal("§a+" + montant + " ◆ §fdéposés sur ton compte §7— solde : §e"
                + LocalEconomy.getInstance().getBalance(pseudo) + " ◆"), true);
            NouvelleTerreBridge.sendBalanceToPlayer(sp);
        }
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("§7Monnaie physique de Nouvelle Terre — §e" + valeur + " ◆ §7l'unité"));
        if (stack.getCount() > 1)
            tooltip.add(Component.literal("§7Cette pile vaut §e" + (stack.getCount() * valeur) + " ◆"));
        tooltip.add(Component.literal("§6Clic droit §7pour déposer la pile sur ton compte"));
    }
}
