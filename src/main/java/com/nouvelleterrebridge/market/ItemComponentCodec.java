package com.nouvelleterrebridge.market;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.HolderLookup;

/**
 * Capture et restitue ce qui distingue une pile de sa version « vierge » — en
 * pratique, les enchantements — pour les annonces du HDV.
 *
 * <p>Avant la 1.20.5, {@code MarketListing.itemNBT} stockait la sortie brute de
 * {@code stack.getNbt().asString()} : tout ce qui dépassait l'état par défaut de
 * l'item (enchantements compris) vivait dans une seule balise NBT. Les enchantements
 * sont désormais un <em>composant de données</em> parmi d'autres, plus une balise NBT
 * annexe — mais {@link DataComponentPatch} reste exactement l'équivalent conceptuel de
 * l'ancien NBT d'item : la différence entre une pile et l'état par défaut de son item.
 * Le schéma de {@code marche.json} ne change donc pas — {@code itemNBT} reste une
 * chaîne SNBT, seul ce qu'elle encode change.
 *
 * <p>Encoder ou décoder un {@link DataComponentPatch} exige un {@link HolderLookup.Provider}
 * (les enchantements sont des entrées de registre) — absent du monde 1.20.1. Client et
 * serveur ont chacun le leur ({@code MinecraftClient.world} / {@code MinecraftServer}) ;
 * les deux doivent produire la même chaîne pour la même pile, c'est ce qui permet à
 * {@code MarketActions.matchesListing} de comparer une pile en inventaire au NBT stocké
 * dans une annonce.
 */
public final class ItemComponentCodec {

    private ItemComponentCodec() {}

    /**
     * Capture ce qui distingue {@code stack} de l'état par défaut de son item.
     * @return "" si la pile est parfaitement vierge (rien à stocker)
     */
    public static String capturer(ItemStack stack, HolderLookup.Provider registries) {
        DataComponentPatch changements = stack.getComponentsPatch();
        if (changements.isEmpty()) return "";
        return DataComponentPatch.CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), changements)
            .result()
            .map(Object::toString)
            .orElse("");
    }

    /**
     * Applique à {@code stack} les changements capturés par {@link #capturer}.
     *
     * <p>Ne fait jamais planter l'appelant : une annonce au format 1.20.1 (créée
     * avant la migration) ne sera pas relisible — le SNBT qu'elle contient décrit un
     * NBT d'item brut, pas un {@link DataComponentPatch}. Dans ce cas, la pile reste
     * vierge et l'appelant doit être prévenu par la valeur de retour plutôt que de
     * livrer silencieusement un item sans ses enchantements à l'acheteur.
     *
     * @return false si {@code snbt} n'était pas vide et n'a pas pu être appliqué
     */
    public static boolean appliquer(ItemStack stack, String snbt, HolderLookup.Provider registries) {
        if (snbt == null || snbt.isEmpty()) return true;
        try {
            Tag brut = TagParser.parseTag(snbt);
            return DataComponentPatch.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), brut)
                .result()
                .map(changements -> { stack.applyComponentsAndValidate(changements); return true; })
                .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}
