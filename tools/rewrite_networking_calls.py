# -*- coding: utf-8 -*-
"""
Reecrit tous les points d'appel de ServerPlayNetworking/ClientPlayNetworking
vers l'adaptateur NtNet (voir network/NtNet.java, network/NtPayload.java).

Ne touche pas network/NtNet.java lui-meme : c'est la ou vivent les vrais
appels a l'API Fabric, tout le reste du mod doit passer par lui.

Quatre transformations :
  1. ServerPlayNetworking.registerGlobalReceiver(CANAL, (server, player, handler, buf, responseSender) -> ...)
     -> NtNet.surServeur(CANAL, (server, player, buf) -> ...)
  2. ClientPlayNetworking.registerGlobalReceiver(CANAL, (client, handler, buf, responseSender) -> ...)
     -> NtNet.surClient(CANAL, (client, buf) -> ...)
  3. ServerPlayNetworking.send(joueur, canal, tampon)  -> NtNet.versClient(joueur, canal, tampon)
  4. ClientPlayNetworking.send(canal, tampon)          -> NtNet.versServeur(canal, tampon)

(3) et (4) utilisent un decoupage d'arguments par comptage de parentheses,
pas une regex sur la virgule : l'expression du tampon peut elle-meme etre
un appel de fonction avec ses propres arguments
(`buildHdvOpenPacket(player, server)`), et l'appel peut s'etaler sur
plusieurs lignes.
"""
import io
import os
import re

EXCLUS = {os.path.normpath("src/main/java/com/nouvelleterrebridge/network/NtNet.java")}

RE_REGISTER_SERVEUR = re.compile(
    r"ServerPlayNetworking\.registerGlobalReceiver\(\s*([\w.]+)\s*,\s*"
    r"\(server,\s*player,\s*handler,\s*buf,\s*responseSender\)\s*->"
)
RE_REGISTER_CLIENT = re.compile(
    r"ClientPlayNetworking\.registerGlobalReceiver\(\s*([\w.]+)\s*,\s*"
    r"\(client,\s*handler,\s*buf,\s*responseSender\)\s*->"
)


def args_de(src, debut):
    """Decoupe les arguments d'un appel dont la parenthese ouvrante est a `debut`."""
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


def reecrire_appels_send(src, marqueur, nb_args, gabarit):
    sortie = []
    i = 0
    while True:
        j = src.find(marqueur, i)
        if j == -1:
            sortie.append(src[i:])
            break
        sortie.append(src[i:j])
        args, fin = args_de(src, j + len(marqueur) - 1)
        if len(args) != nb_args:
            raise ValueError(
                "arite inattendue pour " + marqueur + " : " + repr(args)
            )
        sortie.append(gabarit(args))
        i = fin
    return "".join(sortie)


def convertir(src):
    src = RE_REGISTER_SERVEUR.sub(r"NtNet.surServeur(\1, (server, player, buf) ->", src)
    src = RE_REGISTER_CLIENT.sub(r"NtNet.surClient(\1, (client, buf) ->", src)

    src = reecrire_appels_send(
        src, "ServerPlayNetworking.send(", 3,
        lambda a: "NtNet.versClient(%s, %s, %s)" % (a[0], a[1], a[2]),
    )
    src = reecrire_appels_send(
        src, "ClientPlayNetworking.send(", 2,
        lambda a: "NtNet.versServeur(%s, %s)" % (a[0], a[1]),
    )
    return src


def corriger_imports(src):
    a_ntnet = "NtNet." in src
    src = re.sub(r"\nimport net\.fabricmc\.fabric\.api\.networking\.v1\.ServerPlayNetworking;", "", src)
    src = re.sub(r"\nimport net\.fabricmc\.fabric\.api\.client\.networking\.v1\.ClientPlayNetworking;", "", src)
    if a_ntnet and "import com.nouvelleterrebridge.network.NtNet;" not in src:
        # Inseree juste apres la declaration de package.
        src = re.sub(r"(package [\w.]+;\n)", r"\1\nimport com.nouvelleterrebridge.network.NtNet;\n", src, count=1)
    return src


def main():
    touches = 0
    for dossier, _, fichiers in os.walk("src/main/java"):
        for nom in fichiers:
            if not nom.endswith(".java"):
                continue
            chemin = os.path.join(dossier, nom)
            if os.path.normpath(chemin) in EXCLUS:
                continue
            with io.open(chemin, encoding="utf-8") as f:
                avant = f.read()
            if "ServerPlayNetworking" not in avant and "ClientPlayNetworking" not in avant:
                continue
            apres = convertir(avant)
            apres = corriger_imports(apres)
            if apres != avant:
                with io.open(chemin, "w", encoding="utf-8", newline="") as f:
                    f.write(apres)
                touches += 1
                print("modifie: " + chemin)
    print(str(touches) + " fichiers modifies")


if __name__ == "__main__":
    main()
