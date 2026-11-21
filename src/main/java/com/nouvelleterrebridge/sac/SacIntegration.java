package com.nouvelleterrebridge.sac;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Porte d'entrée de l'intégration Sophisticated Backpacks.
 *
 * Cette classe ne cite aucun type de Sophisticated Core dans ses signatures : le
 * reste du mod peut la référencer sans risque. Seul {@link #enregistrer}, appelé
 * uniquement quand le mod est présent, charge {@link ModuleVenteItem}.
 */
public final class SacIntegration {

    private SacIntegration() {}

    /** Le module de vente, ou {@code null} si Sophisticated Core n'est pas installé. */
    public static DeferredItem<Item> module;

    public static void enregistrer(DeferredRegister.Items items) {
        module = items.register("module_vente", ModuleVenteItem::new);
    }
}
