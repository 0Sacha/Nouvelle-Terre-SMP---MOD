# -*- coding: utf-8 -*-
"""
`new Identifier(...)` devient protege en 1.21 (Yarn) : Fabric recommande
`Identifier.of(ns, path)` (deux arguments) ou `Identifier.of(s)` (un seul,
forme "ns:path").

Le decoupage des arguments se fait par comptage de parentheses, pas par
regex sur la virgule : certains appels sont imbriques
(`new Identifier(id.getNamespace(), path)`), un split naif les casserait.
"""
import io
import os

MARQUEUR = "new Identifier("


def args_de(src, debut):
    i = debut
    profondeur = 0
    args = []
    courant = []
    while i < len(src):
        c = src[i]
        if c in "([{":
            profondeur += 1
            if profondeur == 1 and c == "(":
                i += 1
                continue
        elif c in ")]}":
            profondeur -= 1
            if profondeur == 0:
                args.append("".join(courant).strip())
                return args, i + 1
        elif c == "," and profondeur == 1:
            args.append("".join(courant).strip())
            courant = []
            i += 1
            continue
        elif c == '"':
            courant.append(c)
            i += 1
            while i < len(src) and src[i] != '"':
                if src[i] == "\\":
                    courant.append(src[i])
                    i += 1
                courant.append(src[i])
                i += 1
        courant.append(src[i])
        i += 1
    raise ValueError("parenthese non fermee")


def convertir(src):
    sortie = []
    i = 0
    while True:
        j = src.find(MARQUEUR, i)
        if j == -1:
            sortie.append(src[i:])
            break
        sortie.append(src[i:j])
        args, fin = args_de(src, j + len("new Identifier"))
        sortie.append("Identifier.of(" + ", ".join(args) + ")")
        i = fin
    return "".join(sortie)


def main():
    touches = 0
    for dossier, _, fichiers in os.walk("src/main/java"):
        for nom in fichiers:
            if not nom.endswith(".java"):
                continue
            chemin = os.path.join(dossier, nom)
            with io.open(chemin, encoding="utf-8") as f:
                avant = f.read()
            if MARQUEUR not in avant:
                continue
            apres = convertir(avant)
            if apres != avant:
                with io.open(chemin, "w", encoding="utf-8", newline="") as f:
                    f.write(apres)
                touches += 1
    print(str(touches) + " fichiers modifies")


if __name__ == "__main__":
    main()
