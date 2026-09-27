package com.nouvelleterrebridge.item;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.network.HubNetworking;
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
 * Parchemin de Nouvelle Terre — terminal portatif.
 * Clic droit : ouvre le hub donnant accès au marché, à la banque, aux quêtes, etc.
 * sans avoir à taper de commande dans le chat.
 *
 * L'objet est distribué automatiquement à la connexion, conservé à la mort et
 * ne peut pas être jeté (voir PlayerEvents et NouvelleTerreBridge).
 */
public class ParcheminItem extends Item {

    public ParcheminItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide && user instanceof ServerPlayer sp) {
            NtNet.versClient(sp, HubNetworking.HUB_OPEN, NtNet.buffer());
        }
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("§7Terminal portatif de Nouvelle Terre"));
        tooltip.add(Component.literal("§6Clic droit §7pour ouvrir le menu"));
        tooltip.add(Component.literal("§8Rendu automatiquement s'il vient à manquer"));
    }
}
