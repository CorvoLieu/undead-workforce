package dev.corvo.undeadworkforce.registry;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.item.CarriedZombieItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UndeadWorkforce.MOD_ID);

    public static final DeferredItem<Item> ZOMBIE_COLLAR = ITEMS.registerSimpleItem(
            "zombie_collar", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    /** Not in any creative tab: only obtainable by picking up a minion. */
    public static final DeferredItem<CarriedZombieItem> CARRIED_ZOMBIE = ITEMS.registerItem(
            "carried_zombie", CarriedZombieItem::new, new Item.Properties().stacksTo(1).fireResistant());

    private ModItems() {}
}
