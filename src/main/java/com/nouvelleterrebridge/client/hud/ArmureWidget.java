package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Pièces d'armure portées et objet en main, avec leur durabilité restante. */
public class ArmureWidget extends HudWidget {

    private static final int RANG = 17;

    public ArmureWidget() {
        super("armure", "Armure", 0.99f, 0.50f, false);
        option("main", "Objet en main", true);
    }

    private List<ItemStack> pieces(Minecraft mc) {
        List<ItemStack> out = new ArrayList<>();
        if (mc.player != null) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = mc.player.getItemBySlot(slot);
                if (!s.isEmpty()) out.add(s);
            }
            // En main : seulement ce qui s'use, sinon la liste suivrait chaque bloc tenu
            ItemStack main = mc.player.getMainHandItem();
            if (opt("main") && main.isDamageableItem()) out.add(main);
        }
        if (out.isEmpty() && enEdition) {
            out.add(new ItemStack(Items.IRON_HELMET));
            out.add(new ItemStack(Items.IRON_CHESTPLATE));
            out.add(new ItemStack(Items.IRON_LEGGINGS));
            out.add(new ItemStack(Items.IRON_BOOTS));
        }
        return out;
    }

    @Override
    public boolean aAfficher(Minecraft mc) {
        return !pieces(mc).isEmpty();
    }

    @Override protected int contentWidth(Minecraft mc)  { return 16 + 4 + mc.font.width("100 %"); }
    @Override protected int contentHeight(Minecraft mc) { return Math.max(1, pieces(mc).size()) * RANG - 1; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        List<ItemStack> ps = pieces(mc);
        for (int i = 0; i < ps.size(); i++) {
            ItemStack s = ps.get(i);
            int y = i * RANG;
            ctx.renderItem(s, 0, y);
            if (!s.isDamageableItem()) continue;
            int pct = Math.round((s.getMaxDamage() - s.getDamageValue()) * 100f / s.getMaxDamage());
            texte(ctx, mc, pct + " %", 20, y + 4, pct > 50 ? C_GREEN : pct > 20 ? C_ACCENT : C_RED);
        }
    }
}
