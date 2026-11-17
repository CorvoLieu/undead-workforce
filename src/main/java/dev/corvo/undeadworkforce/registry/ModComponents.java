package dev.corvo.undeadworkforce.registry;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, UndeadWorkforce.MOD_ID);

    /** Full saved NBT of a picked-up minion, carried by a Carried Zombie item. */
    public static final Supplier<DataComponentType<CustomData>> CARRIED_ENTITY = COMPONENTS.registerComponentType(
            "carried_entity",
            builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    private ModComponents() {}
}
