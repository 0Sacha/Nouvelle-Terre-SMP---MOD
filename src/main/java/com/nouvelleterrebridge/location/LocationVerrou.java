package com.nouvelleterrebridge.location;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Le verrou des objets loués.
 *
 * <p>Un objet dont le contrat n'est plus à jour ne doit plus servir à rien :
 * chaque action faite en le tenant est annulée, et une armure verrouillée est
 * retirée de son emplacement (elle protégerait sinon sans qu'aucun clic ne
 * passe par ici).
 *
 * <p>Deux interdictions valent pour <b>tout</b> objet loué, même à jour :
 * l'enclume et la meule. L'objet rendu étant l'objet réel, le locataire ne doit
 * pas pouvoir le désenchanter ou le fondre dans un autre avant de le rendre.
 */
public final class LocationVerrou {

    private LocationVerrou() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LocationVerrou::clicDroitObjet);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::clicDroitBloc);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::clicGaucheBloc);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::clicEntite);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::attaque);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::casse);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::enclume);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::meule);
        NeoForge.EVENT_BUS.addListener(LocationVerrou::tick);
    }

    private static boolean bloque(Player joueur, ItemStack stack) {
        if (!LocationManager.get().estVerrouille(stack)) return false;
        if (joueur instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal(
                "§cObjet loué verrouillé — règle le loyer ou rends-le (Parchemin → Location)"), true);
        return true;
    }

    private static void clicDroitObjet(PlayerInteractEvent.RightClickItem e) {
        if (bloque(e.getEntity(), e.getItemStack())) e.setCanceled(true);
    }

    private static void clicDroitBloc(PlayerInteractEvent.RightClickBlock e) {
        if (bloque(e.getEntity(), e.getItemStack())) e.setCanceled(true);
    }

    private static void clicGaucheBloc(PlayerInteractEvent.LeftClickBlock e) {
        if (bloque(e.getEntity(), e.getItemStack())) e.setCanceled(true);
    }

    private static void clicEntite(PlayerInteractEvent.EntityInteract e) {
        if (bloque(e.getEntity(), e.getItemStack())) e.setCanceled(true);
    }

    private static void attaque(AttackEntityEvent e) {
        if (bloque(e.getEntity(), e.getEntity().getMainHandItem())) e.setCanceled(true);
    }

    private static void casse(BlockEvent.BreakEvent e) {
        if (bloque(e.getPlayer(), e.getPlayer().getMainHandItem())) e.setCanceled(true);
    }

    private static void enclume(AnvilUpdateEvent e) {
        if (LocationManager.contratDe(e.getLeft()) != 0 || LocationManager.contratDe(e.getRight()) != 0)
            e.setCanceled(true);
    }

    private static void meule(GrindstoneEvent.OnPlaceItem e) {
        if (LocationManager.contratDe(e.getTopItem()) != 0 || LocationManager.contratDe(e.getBottomItem()) != 0)
            e.setCanceled(true);
    }

    private static final EquipmentSlot[] PORTES = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    private static void tick(ServerTickEvent.Post e) {
        int t = e.getServer().getTickCount();

        // Loyers, reprises et abandons : une fois par minute
        if (t % 1200 == 0) LocationManager.get().tick(e.getServer());

        // Armures et main secondaire verrouillées : toutes les deux secondes,
        // et seulement s'il existe au moins un contrat bloqué
        if (t % 40 != 0 || !LocationManager.get().aDesContratsBloques()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            for (EquipmentSlot slot : PORTES) {
                ItemStack s = p.getItemBySlot(slot);
                if (!LocationManager.get().estVerrouille(s)) continue;
                p.setItemSlot(slot, ItemStack.EMPTY);
                if (!p.getInventory().add(s)) p.drop(s, false);
                p.displayClientMessage(Component.literal(
                    "§cObjet loué verrouillé — il ne peut plus être porté"), true);
            }
        }
    }
}
