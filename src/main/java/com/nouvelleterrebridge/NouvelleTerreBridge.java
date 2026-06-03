package com.nouvelleterrebridge;

import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.commands.MarcheCommand;
import com.nouvelleterrebridge.commands.PayerCommand;
import com.nouvelleterrebridge.commands.ShardsCommand;
import com.nouvelleterrebridge.commands.SoldeCommand;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.events.PlayerEvents;
import com.nouvelleterrebridge.events.ServerEvents;
import com.nouvelleterrebridge.events.TerritoryEvents;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.http.EventQueue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée du mod NouvelleTerreBridge.
 * Initialise les événements, commandes et le dispatcher HTTP.
 */
public class NouvelleTerreBridge implements ModInitializer {

    public static final String MOD_ID = "nouvelle-terre-bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Configuration chargée depuis le fichier JSON
    public static ModConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("[NouvelleTerreBridge] Initialisation du mod...");

        // Chargement de la configuration
        config = ModConfig.charger();
        LOGGER.info("[NouvelleTerreBridge] Configuration chargée : url={}", config.getBotUrl());

        // Initialisation de la file d'attente des événements
        EventQueue.getInstance().charger();

        // Initialisation du dispatcher HTTP
        EventDispatcher.init(config);

        // Enregistrement des écouteurs d'événements
        ServerEvents.register();
        PlayerEvents.register();
        TerritoryEvents.register();
        KillRewards.register();
        PlaytimeTracker.register();

        // Enregistrement des commandes Minecraft
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MarcheCommand.register(dispatcher);
            LierCommand.register(dispatcher);
            ConflitCommand.register(dispatcher);
            EventNarratifCommand.register(dispatcher);
            SoldeCommand.register(dispatcher);
            PayerCommand.register(dispatcher);
            ShardsCommand.register(dispatcher);
        });

        LOGGER.info("[NouvelleTerreBridge] Mod initialisé avec succès.");
    }
}
