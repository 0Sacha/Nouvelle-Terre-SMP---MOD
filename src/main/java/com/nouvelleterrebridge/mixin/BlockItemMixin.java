package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.economy.PlacedBlockTracker;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marque les blocs posés par un joueur : les recasser ne doit pas compter comme
 * de la production naturelle (voir {@link PlacedBlockTracker}).
 */
@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void nt$marquerBlocPose(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) return;
        if (context.getWorld().isClient()) return;
        if (!(context.getPlayer() instanceof ServerPlayerEntity)) return;
        // getBlockPos() d'un ItemPlacementContext = la position réellement occupée
        // par le bloc posé, pas la face visée.
        PlacedBlockTracker.marquer(context.getWorld(), context.getBlockPos());
    }
}
