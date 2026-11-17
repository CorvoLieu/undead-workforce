package dev.corvo.undeadworkforce.menu;

import dev.corvo.undeadworkforce.minion.MinionCommand;
import dev.corvo.undeadworkforce.minion.MinionCommands;
import dev.corvo.undeadworkforce.minion.MinionMode;
import dev.corvo.undeadworkforce.minion.Minions;
import dev.corvo.undeadworkforce.registry.ModMenus;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The minion's gear menu: weapon / tool / armor / collar slots, a 5-slot pack, and the player's
 * inventory. Orders (Follow / Stay / Wander / Pick Up) are sent as vanilla menu button clicks,
 * so they go through {@link #clickMenuButton} on the server with no custom packets.
 */
public class MinionMenu extends AbstractContainerMenu {
    public static final int WEAPON = 0;
    public static final int TOOL = 1;
    public static final int ARMOR = 2;
    public static final int COLLAR = 3;
    public static final int EQUIPMENT_SLOTS = 4;
    public static final int PACK_SLOTS = 5;
    public static final int MINION_SLOTS = EQUIPMENT_SLOTS + PACK_SLOTS;
    private static final int PLAYER_START = MINION_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    /** Which mob equipment slot backs each gear slot. */
    private static final EquipmentSlot[] GEAR = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST, EquipmentSlot.BODY};
    private static final String[] GEAR_NAMES = {"weapon", "tool", "armor", "collar"};

    // Layout, shared with MinionScreen. Coordinates are the slot's item position (frame is 1px out).
    public static final int GEAR_X = 8;
    public static final int GEAR_Y = 18;
    public static final int PACK_X = 80;
    public static final int PACK_Y = 72;
    public static final int PLAYER_INV_Y = 102;

    @Nullable
    private final Mob minion;
    private final Container container;
    private final DataSlot mode;
    private final DataSlot fighting;

    /** Server side: backed by the live minion. */
    public MinionMenu(int containerId, Inventory playerInventory, Mob minion) {
        this(containerId, playerInventory, minion, new MinionContainer(minion),
                readOnly(() -> Minions.mode(minion).ordinal()),
                readOnly(() -> Minions.isFighting(minion) ? 1 : 0));
    }

    private static DataSlot readOnly(java.util.function.IntSupplier value) {
        return new DataSlot() {
            @Override
            public int get() {
                return value.getAsInt();
            }

            @Override
            public void set(int ignored) {}
        };
    }

    /** Client side: slot contents and the current order arrive through normal menu syncing. */
    public static MinionMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        Entity entity = playerInventory.player.level().getEntity(buf.readVarInt());
        return new MinionMenu(containerId, playerInventory, entity instanceof Mob mob ? mob : null,
                new SimpleContainer(MINION_SLOTS), DataSlot.standalone(), DataSlot.standalone());
    }

    private MinionMenu(int containerId, Inventory playerInventory, @Nullable Mob minion, Container container,
                       DataSlot mode, DataSlot fighting) {
        super(ModMenus.MINION.get(), containerId);
        this.minion = minion;
        this.container = container;
        this.mode = mode;
        this.fighting = fighting;

        addSlot(new GearSlot(container, WEAPON, GEAR_X, GEAR_Y,
                stack -> stack.is(Minions.MINION_WEAPONS), icon("item/empty_slot_sword")));
        addSlot(new GearSlot(container, TOOL, GEAR_X, GEAR_Y + 18,
                stack -> stack.is(Minions.MINION_TOOLS), icon("item/empty_slot_pickaxe")));
        addSlot(new GearSlot(container, ARMOR, GEAR_X, GEAR_Y + 36,
                stack -> stack.is(Minions.MINION_ARMOR), icon("item/empty_armor_slot_chestplate")));
        addSlot(new CollarSlot(container, COLLAR, GEAR_X, GEAR_Y + 54));
        for (int i = 0; i < PACK_SLOTS; i++) {
            addSlot(new Slot(container, EQUIPMENT_SLOTS + i, PACK_X + i * 18, PACK_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
        addDataSlot(mode);
        addDataSlot(fighting);
    }

    /** The mob equipment slot behind a gear slot index, or null for pack slots. */
    @Nullable
    public static EquipmentSlot equipmentSlot(int index) {
        return index >= 0 && index < EQUIPMENT_SLOTS ? GEAR[index] : null;
    }

    public static Component gearSlotName(int index) {
        return Component.translatable("screen.undeadworkforce.slot." + GEAR_NAMES[index]);
    }

    /** The minion this menu belongs to (on the client: its client-side copy, if loaded). */
    @Nullable
    public Mob minion() {
        return minion;
    }

    public MinionMode mode() {
        MinionMode[] modes = MinionMode.values();
        int i = mode.get();
        return i >= 0 && i < modes.length ? modes[i] : MinionMode.FOLLOW;
    }

    public boolean isFighting() {
        return fighting.get() != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        MinionCommand[] commands = MinionCommand.values();
        if (minion == null || id < 0 || id >= commands.length) {
            return false;
        }
        return MinionCommands.apply(player, minion, commands[id]);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MINION_SLOTS) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, WEAPON, COLLAR, false)
                && !moveItemStackTo(stack, EQUIPMENT_SLOTS, MINION_SLOTS, false)) {
            return ItemStack.EMPTY; // gear first (only matching, empty slots accept it), then the pack
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    private static ResourceLocation icon(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** Holds one matching item; shows a vanilla outline icon while empty. */
    private static class GearSlot extends Slot {
        private final Predicate<ItemStack> accepts;

        GearSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts, ResourceLocation emptyIcon) {
            super(container, index, x, y);
            this.accepts = accepts;
            setBackground(InventoryMenu.BLOCK_ATLAS, emptyIcon);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Shows the collar the minion wears. Locked for now: taking it off would free the minion. */
    private static class CollarSlot extends Slot {
        CollarSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
