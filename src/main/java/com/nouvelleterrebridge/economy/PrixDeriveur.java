package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calcule le prix de référence des objets à partir des recettes du jeu.
 *
 * <p><b>Le principe.</b> Un objet doit coûter au moins la somme de ce qu'il
 * contient. Sans cette règle, la table de prix ne couvrait que ~60 matériaux
 * raffinés et tout le reste retombait sur la rareté vanilla — {@code COMMON}
 * pour presque tout, soit 1 ou 2 ◆. Un minerai de diamant valait donc 2 ◆ alors
 * qu'il renferme jusqu'à 4 diamants, soit 480 ◆ : acheter du minerai, le miner
 * et revendre les diamants a créé 51 millions de shards.
 *
 * <p><b>Pourquoi les recettes plutôt qu'une table écrite à la main.</b> Le
 * serveur compte près de 1 400 objets, mods inclus. Les lister un par un serait
 * interminable et laisserait passer chaque ajout futur. Les recettes, elles,
 * décrivent déjà les transformations que les joueurs peuvent faire — c'est
 * exactement l'information qu'il faut pour qu'aucune transformation ne soit
 * rentable.
 *
 * <p><b>Ce que les recettes ne couvrent pas.</b> Miner n'est pas un craft :
 * aucune recette ne relie le minerai à la gemme. Ces cas passent par
 * {@link #CONTENU_MINERAIS}, avec le rendement <i>maximal</i> sous Fortune III —
 * un prix calculé sur le rendement moyen resterait exploitable les jours de
 * chance. Tout identifiant en {@code *_ore} inconnu reçoit un prix plancher, ce
 * qui protège d'avance les minerais des mods.
 */
public final class PrixDeriveur {

    /**
     * Marge appliquée au prix dérivé. Transformer coûte un peu plus cher que la
     * somme des morceaux : sans elle, un aller-retour craft/décraft serait
     * exactement neutre, et la moindre imprécision d'arrondi le rendrait gagnant.
     */
    private static final double MARGE = 1.10;

    /** Nombre de passes : une recette peut dépendre d'une autre encore non calculée. */
    private static final int PASSES = 6;

    /** Prix plancher d'un minerai non répertorié — couvre les mods. */
    private static final int PLANCHER_MINERAI = 150;

    /**
     * Minerais → ce qu'ils lâchent, en quantité <b>maximale sous Fortune III</b>.
     *
     * Les valeurs vanilla : diamant/émeraude/charbon/quartz vont jusqu'à 4,
     * le lapis jusqu'à 36, la redstone jusqu'à 20, le cuivre jusqu'à 20.
     */
    private static final Map<String, Rendement> CONTENU_MINERAIS = new HashMap<>();

    /**
     * @param quantiteMin rendement garanti, sans Fortune — sert au <b>rachat</b>
     * @param quantiteMax rendement maximal sous Fortune III — sert à la <b>vente</b>
     */
    private record Rendement(String item, int quantiteMin, int quantiteMax) {}

    static {
        //       minerai                contenu          min  max
        minerai("diamond_ore",        "diamond",        1,   4);
        minerai("emerald_ore",        "emerald",        1,   4);
        minerai("coal_ore",           "coal",           1,   4);
        minerai("nether_quartz_ore",  "quartz",         1,   4);
        minerai("lapis_ore",          "lapis_lazuli",   4,  36);
        minerai("redstone_ore",       "redstone",       4,  20);
        minerai("copper_ore",         "raw_copper",     2,  20);
        minerai("iron_ore",           "raw_iron",       1,   4);
        minerai("gold_ore",           "raw_gold",       1,   4);
        minerai("nether_gold_ore",    "raw_gold",       1,   3);
    }

    /** Enregistre un minerai et sa variante deepslate, qui lâche la même chose. */
    private static void minerai(String nom, String contenu, int min, int max) {
        Rendement r = new Rendement("minecraft:" + contenu, min, max);
        CONTENU_MINERAIS.put("minecraft:" + nom, r);
        CONTENU_MINERAIS.put("minecraft:deepslate_" + nom, r);
    }

    private PrixDeriveur() {}

    /**
     * Calcule les prix dérivés à partir des prix de base connus.
     *
     * @param prixConnus matières premières (table de référence écrite à la main)
     * @return la table complétée : entrées d'origine + tout ce qui a pu être dérivé
     */
    public static Map<String, Integer> deriver(MinecraftServer server, Map<String, Integer> prixConnus) {
        Map<String, Integer> prix = new HashMap<>(prixConnus);

        // Prix qui font autorité : jamais recalculés par une recette.
        // Sans ce verrou, les recettes réversibles s'auto-alimentent : le bloc de
        // diamant vaut 9 diamants × 1,10, et la recette inverse redonne un diamant
        // à bloc × 1,10 / 9 — soit +21 % par passe, six fois de suite. Le diamant
        // est monté à 1 296 ◆ et un sac à dos en bout de chaîne à 2,5 millions.
        Set<String> figes = new HashSet<>(prixConnus.keySet());

        // ── Minerais : leur contenu ne vient d'aucune recette ──
        for (Map.Entry<String, Rendement> e : CONTENU_MINERAIS.entrySet()) {
            Integer p = prix.get(e.getValue().item());
            if (p == null) continue;
            int calcule = (int) Math.ceil(p * e.getValue().quantiteMax() * MARGE);
            // Ne jamais baisser un prix déjà fixé à la main (ancient_debris, par ex.)
            prix.merge(e.getKey(), calcule, Math::max);
            // Ce prix est un garde-fou anti-exploit : une recette ne doit pas le réduire.
            figes.add(e.getKey());
        }

        // ── Crafts : plusieurs passes, une recette pouvant dépendre d'une autre ──
        List<Recipe<?>> recettes = List.copyOf(server.getRecipeManager().values());
        for (int passe = 0; passe < PASSES; passe++) {
            boolean change = false;
            for (Recipe<?> r : recettes) {
                try {
                    if (appliquer(r, prix, figes, server)) change = true;
                } catch (Exception ignored) {
                    // Une recette exotique (mod) ne doit pas interrompre le calcul
                }
            }
            if (!change) break;   // point fixe atteint
        }

        NouvelleTerreBridge.LOGGER.info("[PrixDeriveur] {} prix connus après dérivation ({} recettes lues).",
            prix.size(), recettes.size());
        return prix;
    }

    /**
     * Déduit le prix du résultat d'une recette depuis celui de ses ingrédients.
     *
     * <p>On retient le <b>chemin de fabrication le moins cher</b>, pas le plus cher :
     * c'est celui que le joueur empruntera. Le rachat étant à 55 %, fabriquer pour
     * revendre reste alors toujours perdant (0,55 × 1,10 = 0,605 fois le coût), alors
     * qu'aligner sur le chemin le plus coûteux ouvrirait l'écart inverse.
     *
     * @return true si un prix a été posé ou abaissé
     */
    private static boolean appliquer(Recipe<?> recette, Map<String, Integer> prix,
                                     Set<String> figes, MinecraftServer server) {
        ItemStack sortie = recette.getOutput(server.getRegistryManager());
        if (sortie == null || sortie.isEmpty()) return false;

        String idSortie = Registries.ITEM.getId(sortie.getItem()).toString();
        if (figes.contains(idSortie)) return false;   // valeur de référence : intouchable
        int nbSortie = Math.max(1, sortie.getCount());

        int total = 0;
        for (Ingredient ing : recette.getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] options = ing.getMatchingStacks();
            if (options.length == 0) continue;

            // Un ingrédient peut accepter plusieurs objets (tag « planches », par
            // exemple) : on retient le moins cher, celui que le joueur utilisera.
            Integer moinsCher = null;
            for (ItemStack opt : options) {
                Integer p = prix.get(Registries.ITEM.getId(opt.getItem()).toString());
                if (p != null && (moinsCher == null || p < moinsCher)) moinsCher = p;
            }
            if (moinsCher == null) return false;   // ingrédient non tarifé : on repassera
            total += moinsCher;
        }
        if (total <= 0) return false;

        int calcule = Math.max(1, (int) Math.ceil(total * MARGE / nbSortie));
        Integer actuel = prix.get(idSortie);
        if (actuel != null && actuel <= calcule) return false;
        prix.put(idSortie, calcule);
        return true;
    }

    /** Prix plancher d'un minerai inconnu, pour ne jamais brader un contenant. */
    public static Integer plancherMinerai(String itemId) {
        return itemId.endsWith("_ore") ? PLANCHER_MINERAI : null;
    }

    /**
     * Valeur d'un minerai au <b>rendement garanti</b>, sans Fortune — ou null si
     * l'item n'est pas un minerai répertorié.
     *
     * <p>Sert à plafonner le <b>rachat</b>. La vente est calée sur le rendement
     * maximal pour qu'acheter du minerai ne soit jamais rentable ; appliquer la
     * marge de 55 % à ce même prix ferait payer au serveur bien plus que le contenu
     * réel — un minerai de diamant serait racheté 290 ◆ alors qu'il rend en moyenne
     * 145 ◆ de diamants, et la Silk Touch deviendrait deux fois plus payante que la
     * Fortune. Le serveur achète donc sur ce qu'il est <b>sûr</b> d'obtenir.
     *
     * @param prixDe accès au prix unitaire du contenu
     */
    public static Integer valeurGarantie(String itemId, java.util.function.Function<String, Integer> prixDe) {
        Rendement r = CONTENU_MINERAIS.get(itemId);
        if (r == null) return null;
        Integer p = prixDe.apply(r.item());
        return p == null ? null : p * r.quantiteMin();
    }
}
