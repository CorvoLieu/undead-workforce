package dev.corvo.undeadworkforce.menu;

import dev.corvo.undeadworkforce.minion.Minions;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Server-side view of a minion's gear as one container: the 4 equipment slots (which live on the
 * mob itself, so vanilla renders, syncs and saves them) followed by the 5-slot pack attachment.
 */
public class MinionContainer implements Container {
    private final Mob mob;
    private final ItemStackHandler pack;

    public MinionContainer(Mob mob) {
        this.mob = mob;
        this.pack = Minions.pack(mob);
    }

    @Override
    public int getContainerSize() {
        return MinionMenu.MINION_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        EquipmentSlot slot = MinionMenu.equipmentSlot(index);
        return slot != null ? mob.getItemBySlot(slot) : pack.getStackInSlot(index - MinionMenu.EQUIPMENT_SLOTS);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        EquipmentSlot slot = MinionMenu.equipmentSlot(index);
        if (slot != null) {
            Minions.equip(mob, slot, stack);
        } else {
            pack.setStackInSlot(index - MinionMenu.EQUIPMENT_SLOTS, stack);
        }
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack stack = getItem(index);
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(count, stack.getCount());
        ItemStack result = stack.copyWithCount(taken);
        setItem(index, taken == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - taken));
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = getItem(index);
        setItem(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setChanged() {
        // Both the mob's equipment and the pack attachment are saved with the entity.
    }

    @Override
    public boolean stillValid(Player player) {
        return Minions.canManage(mob, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (i != MinionMenu.COLLAR) {
                setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
