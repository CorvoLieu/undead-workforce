package dev.corvo.undeadworkforce.registry;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.menu.MinionMenu;
import dev.corvo.undeadworkforce.minion.MinionMode;
import com.mojang.serialization.Codec;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, UndeadWorkforce.MOD_ID);

    /**
     * The UUID of the player who collared this zombie. Presence of this attachment is what makes
     * a zombie a minion (server side). Saved with the entity, so it survives chunk unloads/restarts.
     */
    public static final Supplier<AttachmentType<UUID>> MINION_OWNER = ATTACHMENT_TYPES.register(
            "minion_owner",
            () -> AttachmentType.builder(() -> Util.NIL_UUID).serialize(UUIDUtil.CODEC).build());

    /** The minion's standing order (follow / stay / wander). */
    public static final Supplier<AttachmentType<MinionMode>> MINION_MODE = ATTACHMENT_TYPES.register(
            "minion_mode",
            () -> AttachmentType.builder(() -> MinionMode.FOLLOW).serialize(MinionMode.CODEC).build());

    /** Whether the minion fights hostiles and defends its owner. Off by default. */
    public static final Supplier<AttachmentType<Boolean>> MINION_FIGHTING = ATTACHMENT_TYPES.register(
            "minion_fighting",
            () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).build());

    /** The minion's 5-slot pack for carrying anything. Gear lives in the mob's equipment slots instead. */
    public static final Supplier<AttachmentType<ItemStackHandler>> MINION_PACK = ATTACHMENT_TYPES.register(
            "minion_pack",
            () -> AttachmentType.serializable(() -> new ItemStackHandler(MinionMenu.PACK_SLOTS)).build());

    private ModAttachments() {}
}
