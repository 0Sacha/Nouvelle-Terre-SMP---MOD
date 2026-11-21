package com.nouvelleterrebridge.sac;

import com.nouvelleterrebridge.economy.ServerShopActions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Module de vente — amélioration de sac à dos (Sophisticated Backpacks).
 *
 * Un sac qui le porte revend tout seul au Shop Serveur ce qu'il contient de
 * rachetable, au profit du joueur qui le porte. Il se fabrique et se pose comme
 * les autres modules, et s'éteint par le même interrupteur.
 *
 * ⚠ Seul un sac porté par un joueur vend : posé au sol ou dans un coffre, le
 * module reçoit un porteur nul et ne fait rien. C'est voulu — un sac posé sous
 * une ferme, alimenté par des entonnoirs, imprimerait des shards sans personne.
 */
public class ModuleVenteItem extends UpgradeItemBase<ModuleVenteItem.Wrapper> {

    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);

    /** Délai entre deux ventes d'un même sac, en ticks (5 secondes). */
    private static final int DELAI = 100;

    /** Aucune limite de nombre par type de rangement ; l'unicité passe par le conflit ci-dessous. */
    private static final IUpgradeCountLimitConfig SANS_LIMITE = new IUpgradeCountLimitConfig() {
        @Override public int getMaxUpgradesPerStorage(String storageType, ResourceLocation upgrade) { return Integer.MAX_VALUE; }
        @Override public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup group) { return Integer.MAX_VALUE; }
    };

    private static final List<UpgradeConflictDefinition> CONFLITS = List.of(
        new UpgradeConflictDefinition(ModuleVenteItem.class::isInstance, 0,
            Component.literal("Ce sac a déjà un module de vente")));

    public ModuleVenteItem() {
        super(SANS_LIMITE);
    }

    @Override public UpgradeType<Wrapper> getType() { return TYPE; }

    @Override public List<UpgradeConflictDefinition> getUpgradeConflicts() { return CONFLITS; }

    public static class Wrapper extends UpgradeWrapperBase<Wrapper, ModuleVenteItem> implements ITickableUpgrade {

        public Wrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(storageWrapper, upgrade, upgradeSaveHandler);
        }

        /** Rien à régler : pas d'onglet, seulement l'interrupteur du module. */
        @Override public boolean hideSettingsTab() { return true; }

        @Override
        public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
            if (!(entity instanceof ServerPlayer joueur) || !isEnabled() || isInCooldown(level)) return;
            setCooldown(level, DELAI);

            InventoryHandler inv = storageWrapper.getInventoryHandler();
            Map<String, Integer> lot = new LinkedHashMap<>();
            for (int i = 0; i < inv.getSlots(); i++) {
                String id = ServerShopActions.idVendable(inv.getStackInSlot(i));
                if (id == null) continue;
                // Une extraction rend au plus une pile : un emplacement agrandi par un
                // module d'empilement se vide en plusieurs fois, le reste au passage suivant.
                for (int n = 0; n < 64; n++) {
                    ItemStack pris = inv.extractItem(i, 64, false);
                    if (pris.isEmpty()) break;
                    lot.merge(id, pris.getCount(), Integer::sum);
                }
            }
            if (!lot.isEmpty()) ServerShopActions.venteAutomatique(joueur, lot);
        }
    }
}
