"""
Migration Sophisticated Backpacks : format legacy (1.20.1, tag NBT) -> format
moderne (1.21.1, data components).

Le contenu des sacs (data/sophisticatedbackpacks.dat) n'a pas besoin d'etre
touche : le mod le normalise deja correctement tout seul au chargement.
Seul l'ITEM du sac (tag.contentsUuid -> components."sophisticatedcore:storage_uuid")
doit etre reecrit, sinon le lien vers le contenu ne se fait que si le joueur
rouvre le sac (bug connu, ne se declenche pas tout seul).

⚠ A faire tourner sur les donnees AVANT tout premier chargement sous les mods
1.21.1 — un monde deja charge une fois peut avoir carrement perdu les items
sac (pas seulement leur contenu), voir CLAUDE.md section « Migration vers la
production ».

Usage:
    python migrate_backpacks.py <fichier.dat> [--apply] [--out SORTIE.dat]

Sans --apply : dry-run, affiche ce qui serait change, n'ecrit rien.
Avec --apply : ecrit le resultat dans --out (par defaut: <fichier>.migrated.dat),
               ne touche JAMAIS le fichier source.

Ne couvre pour l'instant que les playerdata (Inventory/EnderItems, recursif —
gere les sacs imbriques via l'upgrade Inception). nbtlib n'a pas de support
Anvil/region : les sacs poses dans des coffres du monde ne sont pas encore
couverts, a etendre le moment venu (meme logique, juste appliquee aux
block-entities des fichiers de region).

Nécessite : pip install nbtlib
"""
import argparse

import nbtlib
from nbtlib.tag import Compound, Int, IntArray, List, String

LEGACY_KEYS = {
    "clothColor", "borderColor", "openTabId", "sortBy", "contentsUuid",
    "inventorySlots", "upgradeSlots", "lootTableName", "lootPercentage",
    "columnsTaken", "templateName", "renderInfo",
}


def with_opaque_alpha(color: int) -> int:
    # Sophisticated*.LegacyBackpackDataMigration.withOpaqueAlpha
    if (color & 0xFF000000) == 0:
        color |= 0xFF000000
        # ramener dans la plage int32 signee (NBT Int = i32)
        if color >= 0x80000000:
            color -= 0x100000000
    return color


def migrate_itemstack(item: Compound, log: list, path: str) -> bool:
    """Migre un ItemStack en place si c'est un item legacy avec tag.contentsUuid.
    Retourne True si une modification a ete faite."""
    changed = False

    tag = item.get("tag")
    if tag is not None and isinstance(tag, Compound) and "contentsUuid" in tag:
        components = item.get("components")
        if components is None:
            components = Compound({})
            item["components"] = components

        uuid_val = tag["contentsUuid"]
        components["sophisticatedcore:storage_uuid"] = IntArray(list(uuid_val))

        if "inventorySlots" in tag:
            components["sophisticatedcore:number_of_inventory_slots"] = Int(int(tag["inventorySlots"]))
        if "upgradeSlots" in tag:
            components["sophisticatedcore:number_of_upgrade_slots"] = Int(int(tag["upgradeSlots"]))
        if "clothColor" in tag:
            components["sophisticatedcore:main_color"] = Int(with_opaque_alpha(int(tag["clothColor"])))
        if "borderColor" in tag:
            components["sophisticatedcore:accent_color"] = Int(with_opaque_alpha(int(tag["borderColor"])))
        if "openTabId" in tag:
            components["sophisticatedcore:open_tab_id"] = Int(int(tag["openTabId"]))
        if "sortBy" in tag:
            components["sophisticatedcore:sort_by"] = String(str(tag["sortBy"]))
        # renderInfo n'est PAS copie : c'est un cache cosmetique (quelles upgrades
        # s'affichent sur l'exterieur du sac), recalcule automatiquement par le mod
        # depuis le contenu reel des slots d'upgrade. Le copier tel quel causait
        # "Tried to load invalid item: Item must not be minecraft:air" au chargement
        # (les marqueurs de slot vide de l'ancien format ne sont plus valides tels
        # quels dans le nouveau parseur d'ItemStack).

        # Champs vanilla generiques (pas specifiques a Sophisticated*) qui ont
        # eux aussi leur propre composant en 1.21.1 — un sac renomme a
        # l'enclume en porte, sinon le nom/le cout de reparation se perdrait.
        VANILLA_KEYS = {"RepairCost", "display"}
        if "RepairCost" in tag:
            components["minecraft:repair_cost"] = Int(int(tag["RepairCost"]))
        display = tag.get("display")
        if isinstance(display, Compound):
            if "Name" in display:
                components["minecraft:custom_name"] = display["Name"]
            if "Lore" in display:
                components["minecraft:lore"] = display["Lore"]

        # Retire les cles legacy + vanilla connues ; garde le reste s'il y en a
        remaining = Compound({k: v for k, v in tag.items() if k not in LEGACY_KEYS and k not in VANILLA_KEYS})
        if remaining:
            components["minecraft:custom_data"] = remaining
        del item["tag"]

        log.append(f"  [{path}] {item.get('id')} -> composants (uuid conserve)")
        changed = True

    # Count (byte) -> count (int), format d'item moderne
    if "Count" in item and "count" not in item:
        item["count"] = Int(int(item["Count"]))
        del item["Count"]
        changed = True

    return changed


def walk(node, log: list, path: str):
    """Parcourt recursivement le NBT ; migre tout ItemStack rencontre
    (repere par la presence d'une cle 'id'), et descend dans toute liste
    'Items' (inventaires, y compris imbriques : sac dans sac, disque de
    jukebox, etc.)."""
    if isinstance(node, Compound):
        if "id" in node and ("tag" in node or "Count" in node or "components" in node):
            migrate_itemstack(node, log, path)
        for key, value in node.items():
            walk(value, log, f"{path}.{key}")
    elif isinstance(node, List):
        for i, item in enumerate(node):
            walk(item, log, f"{path}[{i}]")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", help="Fichier playerdata .dat source (1.20.1)")
    parser.add_argument("--apply", action="store_true", help="Ecrit le resultat (sinon dry-run)")
    parser.add_argument("--out", help="Fichier de sortie (defaut: <source>.migrated.dat)")
    args = parser.parse_args()

    nbt_file = nbtlib.load(args.source)
    root = nbt_file.root if hasattr(nbt_file, "root") else nbt_file

    log = []
    for key in ("Inventory", "EnderItems"):
        if key in root:
            walk(root[key], log, key)

    print(f"{len(log)} item(s) migre(s) :")
    for line in log:
        print(line)

    if args.apply:
        out_path = args.out or (args.source + ".migrated.dat")
        nbt_file.save(out_path)
        print(f"\nEcrit dans : {out_path}")
    else:
        print("\n(dry-run, rien ecrit — relancer avec --apply pour sauvegarder)")


if __name__ == "__main__":
    main()
