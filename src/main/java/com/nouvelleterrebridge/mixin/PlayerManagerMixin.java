package com.nouvelleterrebridge.mixin;

import com.mojang.authlib.GameProfile;
import com.nouvelleterrebridge.MaintenanceMode;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * Refuse la connexion des non-op quand le mode maintenance est actif.
 *
 * <p>On se greffe sur {@code checkCanJoin}, le point de contrôle que le jeu utilise
 * déjà pour la whitelist et les bannissements : renvoyer un texte refuse l'entrée.
 * Le joueur est donc écarté <b>avant</b> d'entrer dans le monde — aucun chargement
 * de chunk, aucun message « a rejoint la partie », aucune donnée touchée. Un kick
 * après connexion l'aurait fait charger le monde pour rien.
 */
@Mixin(PlayerList.class)
public abstract class PlayerManagerMixin {

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void nt$refuserPendantMaintenance(SocketAddress address, GameProfile profile,
                                              CallbackInfoReturnable<Component> cir) {
        if (!MaintenanceMode.estActif()) return;

        PlayerList self = (PlayerList) (Object) this;
        if (self.isOp(profile)) return;   // les op gardent l'accès

        cir.setReturnValue(Component.literal("§6⚠ " + MaintenanceMode.getMessage()));
    }
}
