package dev.corvo.undeadworkforce.registry;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.menu.MinionMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, UndeadWorkforce.MOD_ID);

    /** Opened with the minion's entity id as extra data. */
    public static final Supplier<MenuType<MinionMenu>> MINION =
            MENUS.register("minion", () -> IMenuTypeExtension.create(MinionMenu::fromNetwork));

    private ModMenus() {}
}
