package dev.corvo.undeadworkforce.client;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.registry.ModMenus;
import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = UndeadWorkforce.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    /** Vanilla humanoid zombies we draw the collar on. Keep in sync with the collarable tag. */
    private static final List<EntityType<?>> COLLAR_RENDERED = List.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER);

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MINION.get(), MinionScreen::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CollarLayer.LAYER, CollarLayer::createLayer);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : COLLAR_RENDERED) {
            if (event.getRenderer((EntityType) type) instanceof LivingEntityRenderer renderer) {
                renderer.addLayer(new CollarLayer(renderer, event.getEntityModels()));
            }
        }
    }
}
