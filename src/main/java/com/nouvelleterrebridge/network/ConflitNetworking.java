package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public final class ConflitNetworking {

    public static final Identifier CONFLIT_OPEN   = new Identifier("nouvelle-terre-bridge", "conflit_open");   // S→C : liste joueurs en ligne
    public static final Identifier CONFLIT_ACTION = new Identifier("nouvelle-terre-bridge", "conflit_action"); // C→S : cible + raison
    public static final Identifier CONFLIT_RESULT = new Identifier("nouvelle-terre-bridge", "conflit_result"); // S→C : ok + message (toast)

    private ConflitNetworking() {}
}
