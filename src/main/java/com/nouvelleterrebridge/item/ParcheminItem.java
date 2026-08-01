package com.nouvelleterrebridge.item;

import com.nouvelleterrebridge.network.HubNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 * Parchemin de Nouvelle Terre — terminal portatif.
 * Clic droit : ouvre le hub donnant accès au marché, à la banque, aux quêtes, etc.
 * sans avoir à taper de commande dans le chat.
 *
 * L'objet est distribué automatiquement à la connexion, conservé à la mort et
 * ne peut pas être jeté (voir PlayerEvents et NouvelleTerreBridge).
 */
public class ParcheminItem extends Item {

    public ParcheminItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, HubNetworking.HUB_OPEN, PacketByteBufs.empty());
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Terminal portatif de Nouvelle Terre"));
        tooltip.add(Text.literal("§6Clic droit §7pour ouvrir le menu"));
        tooltip.add(Text.literal("§8Rendu automatiquement s'il vient à manquer"));
    }
}
