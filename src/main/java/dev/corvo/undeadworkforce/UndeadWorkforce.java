package dev.corvo.undeadworkforce;

import com.mojang.logging.LogUtils;
import dev.corvo.undeadworkforce.registry.ModAttachments;
import dev.corvo.undeadworkforce.registry.ModComponents;
import dev.corvo.undeadworkforce.registry.ModItems;
import dev.corvo.undeadworkforce.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(UndeadWorkforce.MOD_ID)
public final class UndeadWorkforce {
    public static final String MOD_ID = "undeadworkforce";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UndeadWorkforce(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(UndeadWorkforce::addToCreativeTabs);
        // Gameplay listeners are @EventBusSubscriber classes.
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.ZOMBIE_COLLAR);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
