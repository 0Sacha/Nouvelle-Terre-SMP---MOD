# -*- coding: utf-8 -*-
"""
Minecraft 1.21 ajoute le defilement horizontal : `mouseScrolled` prend
desormais un parametre `horizontalAmount` avant l'ancien montant vertical.
Purement un changement vanilla, independant du loader.

Chaque site du mod ignore le defilement horizontal (aucune UI ici ne s'en
sert) : le parametre est ajoute mais jamais lu dans le corps.
"""
import io
import os
import re

RE_SIGNATURE = re.compile(
    r"public boolean mouseScrolled\(double (\w+), double (\w+), double (\w+)\)"
)
RE_SUPER_CALL = re.compile(
    r"super\.mouseScrolled\((\w+), (\w+), (\w+)\)"
)


def convertir(src):
    src = RE_SIGNATURE.sub(
        r"public boolean mouseScrolled(double \1, double \2, double horizontalAmount, double \3)",
        src,
    )
    src = RE_SUPER_CALL.sub(
        r"super.mouseScrolled(\1, \2, horizontalAmount, \3)",
        src,
    )
    return src


def main():
    touches = 0
    for dossier, _, fichiers in os.walk("src/main/java"):
        for nom in fichiers:
            if not nom.endswith(".java"):
                continue
            chemin = os.path.join(dossier, nom)
            with io.open(chemin, encoding="utf-8") as f:
                avant = f.read()
            if "mouseScrolled" not in avant:
                continue
            apres = convertir(avant)
            if apres != avant:
                with io.open(chemin, "w", encoding="utf-8", newline="") as f:
                    f.write(apres)
                touches += 1
    print(str(touches) + " fichiers modifies")


if __name__ == "__main__":
    main()
