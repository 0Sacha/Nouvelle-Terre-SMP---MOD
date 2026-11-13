"""
Extension de migrate_backpacks.py aux entites du monde (items droppes au sol,
stockes dans world/entities/*.mca — pas couverts par le playerdata).

Usage:
    python migrate_backpacks_entities.py <dossier_entities> [--apply]

Sans --apply : dry-run, liste les sacs trouves sans rien ecrire.
Avec --apply : reecrit les fichiers .mca en place (fait une copie .mca.bak
               du fichier AVANT toute modification, une seule fois).

Necessite : pip install nbt  (le package "NBT" de twoolie, pas nbtlib —
            nbtlib n'a pas de support des fichiers de region Anvil)
"""
import argparse
import glob
import os
import shutil

from nbt import nbt as nbtmod
from nbt.region import RegionFile

LEGACY_KEYS = {
    "clothColor", "borderColor", "openTabId", "sortBy", "contentsUuid",
    "inventorySlots", "upgradeSlots", "lootTableName", "lootPercentage",
    "columnsTaken", "templateName", "renderInfo",
}
VANILLA_KEYS = {"RepairCost", "display"}


def with_opaque_alpha(color: int) -> int:
    if (color & 0xFF000000) == 0:
        color |= 0xFF000000
        if color >= 0x80000000:
            color -= 0x100000000
    return color


def migrate_itemstack(item, log: list, path: str) -> bool:
    """Meme logique que migrate_backpacks.py, adaptee a l'API du package nbt
    (TAG_Compound utilise item[key] = TAG(...) plutot que des types nbtlib)."""
    changed = False

    if "tag" in item and "contentsUuid" in item["tag"]:
        tag = item["tag"]
        if "components" not in item:
            item["components"] = nbtmod.TAG_Compound(name="components")
        components = item["components"]

        uuid_tag = tag["contentsUuid"]
        new_uuid = nbtmod.TAG_Int_Array(name="sophisticatedcore:storage_uuid")
        new_uuid.value = list(uuid_tag.value)
        components.tags.append(new_uuid)

        def copy_int(src_key, dst_key):
            if src_key in tag:
                t = nbtmod.TAG_Int(name=dst_key)
                t.value = int(tag[src_key].value)
                components.tags.append(t)

        copy_int("inventorySlots", "sophisticatedcore:number_of_inventory_slots")
        copy_int("upgradeSlots", "sophisticatedcore:number_of_upgrade_slots")
        if "clothColor" in tag:
            t = nbtmod.TAG_Int(name="sophisticatedcore:main_color")
            t.value = with_opaque_alpha(int(tag["clothColor"].value))
            components.tags.append(t)
        if "borderColor" in tag:
            t = nbtmod.TAG_Int(name="sophisticatedcore:accent_color")
            t.value = with_opaque_alpha(int(tag["borderColor"].value))
            components.tags.append(t)
        copy_int("openTabId", "sophisticatedcore:open_tab_id")
        if "sortBy" in tag:
            t = nbtmod.TAG_String(name="sophisticatedcore:sort_by")
            t.value = str(tag["sortBy"].value)
            components.tags.append(t)
        # renderInfo n'est PAS copie : cache cosmetique, recalcule par le mod —
        # le copier tel quel causait "Item must not be minecraft:air" au chargement
        # (marqueurs de slot vide invalides dans le nouveau parseur d'ItemStack).
        if "RepairCost" in tag:
            t = nbtmod.TAG_Int(name="minecraft:repair_cost")
            t.value = int(tag["RepairCost"].value)
            components.tags.append(t)
        if "display" in tag:
            display = tag["display"]
            if "Name" in display:
                t = display["Name"]
                t.name = "minecraft:custom_name"
                components.tags.append(t)
            if "Lore" in display:
                t = display["Lore"]
                t.name = "minecraft:lore"
                components.tags.append(t)

        remaining = nbtmod.TAG_Compound(name="minecraft:custom_data")
        for t in tag.tags:
            if t.name not in LEGACY_KEYS and t.name not in VANILLA_KEYS:
                remaining.tags.append(t)
        if remaining.tags:
            components.tags.append(remaining)

        del item["tag"]
        log.append(f"  [{path}] {item['id'].value} -> composants (uuid conserve)")
        changed = True

    if "Count" in item and "count" not in item:
        t = nbtmod.TAG_Int(name="count")
        t.value = int(item["Count"].value)
        item.tags.append(t)
        del item["Count"]
        changed = True

    return changed


def walk(node, log: list, path: str):
    if isinstance(node, nbtmod.TAG_Compound):
        if "id" in node and ("tag" in node or "Count" in node or "components" in node):
            migrate_itemstack(node, log, path)
        for tag in list(node.tags):
            walk(tag, log, f"{path}.{tag.name}")
    elif isinstance(node, nbtmod.TAG_List):
        for i, item in enumerate(node.tags):
            walk(item, log, f"{path}[{i}]")


def process_region_file(path: str, apply_changes: bool, log: list) -> int:
    changed_chunks = 0
    region = RegionFile(path)
    try:
        for chunk_meta in region.get_chunks():
            chunk_x, chunk_z = chunk_meta["x"], chunk_meta["z"]
            try:
                nbt_file = region.get_chunk(chunk_x, chunk_z)
            except Exception as e:
                log.append(f"  [{path} chunk {chunk_x},{chunk_z}] ERREUR lecture: {e}")
                continue

            root = nbt_file
            if "Entities" not in root:
                continue

            chunk_log = []
            for entity in root["Entities"].tags:
                walk(entity, chunk_log, f"{os.path.basename(path)}[{chunk_x},{chunk_z}]")

            if chunk_log:
                log.extend(chunk_log)
                changed_chunks += 1
                if apply_changes:
                    region.write_chunk(chunk_x, chunk_z, nbt_file)
    finally:
        region.close()
    return changed_chunks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("entities_dir", help="Dossier world/entities contenant les .mca")
    parser.add_argument("--apply", action="store_true", help="Ecrit les changements (sinon dry-run)")
    args = parser.parse_args()

    mca_files = sorted(glob.glob(os.path.join(args.entities_dir, "*.mca")))
    print(f"{len(mca_files)} fichier(s) de region a scanner")

    log = []
    total_changed_chunks = 0
    for i, mca_path in enumerate(mca_files):
        if args.apply:
            bak_path = mca_path + ".bak"
            if not os.path.exists(bak_path):
                shutil.copy2(mca_path, bak_path)
        n = process_region_file(mca_path, args.apply, log)
        total_changed_chunks += n
        if (i + 1) % 200 == 0:
            print(f"  ... {i + 1}/{len(mca_files)} fichiers scannes, {total_changed_chunks} chunk(s) avec sac jusqu'ici")

    print(f"\n{len(log)} item(s) migre(s) dans {total_changed_chunks} chunk(s) :")
    for line in log:
        print(line)

    if not args.apply:
        print("\n(dry-run, rien ecrit — relancer avec --apply pour sauvegarder)")


if __name__ == "__main__":
    main()
