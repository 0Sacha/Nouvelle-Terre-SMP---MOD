# -*- coding: utf-8 -*-
"""
Reecrit les classes de canaux (network/*.java) : chaque constante
`Identifier` devient un `CustomPayload.Id<NtPayload>` declare via
`NtNet.canal(...)`, la charge de l'enregistrement PayloadTypeRegistry.

Ne touche pas HdvNetworking.java, deja fait a la main comme reference.
"""
import io
import os
import re

FICHIERS = [
    "BankNetworking.java", "ConflitNetworking.java", "HubNetworking.java",
    "ProductionNetworking.java", "QuestNetworking.java", "RegistreNetworking.java",
    "ServiceNetworking.java", "ShopNetworking.java", "WikiNetworking.java",
]

MOTIF_CONST = re.compile(
    r'public static final Identifier (\w+)\s*=\s*Identifier\.of\("nouvelle-terre-bridge",\s*"([a-z_]+)"\);'
)


def convertir(src):
    src = src.replace(
        "import net.minecraft.util.Identifier;",
        "import net.minecraft.network.packet.CustomPayload;",
    )
    src = MOTIF_CONST.sub(
        lambda m: 'public static final CustomPayload.Id<NtPayload> %s = NtNet.canal("%s");'
        % (m.group(1), m.group(2)),
        src,
    )
    return src


def main():
    racine = "src/main/java/com/nouvelleterrebridge/network"
    touches = 0
    for nom in FICHIERS:
        chemin = os.path.join(racine, nom)
        with io.open(chemin, encoding="utf-8") as f:
            avant = f.read()
        apres = convertir(avant)
        if "Identifier" in apres:
            print("ATTENTION reliquat dans " + nom)
        if apres != avant:
            with io.open(chemin, "w", encoding="utf-8", newline="") as f:
                f.write(apres)
            touches += 1
    print(str(touches) + " fichiers modifies")


if __name__ == "__main__":
    main()
