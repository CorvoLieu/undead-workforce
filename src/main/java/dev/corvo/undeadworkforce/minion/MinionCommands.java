package dev.corvo.undeadworkforce.minion;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Server-side execution of menu orders. Never trust the client: re-check ownership and range. */
public final class MinionCommands {
    private MinionCommands() {}

    /** Returns true if the order was carried out. */
    public static boolean apply(Player player, Mob mob, MinionCommand command) {
        if (!Minions.canManage(mob, player)) {
            return false;
        }
        return switch (command) {
            case FOLLOW -> order(player, mob, MinionMode.FOLLOW);
            case STAY -> order(player, mob, MinionMode.STAY);
            case WANDER -> order(player, mob, MinionMode.WANDER);
            case PICK_UP -> pickUp(player, mob);
            case TOGGLE_FIGHT -> toggleFight(player, mob);
        };
    }

    private static boolean order(Player player, Mob mob, MinionMode mode) {
        Minions.setMode(mob, mode);
        mob.playSound(SoundEvents.ZOMBIE_AMBIENT, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("message.undeadworkforce.ordered",
                Component.translatable(mode.translationKey())), true);
        return true;
    }

    private static boolean toggleFight(Player player, Mob mob) {
        boolean fighting = !Minions.isFighting(mob);
        Minions.setFighting(mob, fighting);
        mob.playSound(SoundEvents.ZOMBIE_AMBIENT, 0.6F, fighting ? 0.7F : 1.2F);
        player.displayClientMessage(Component.translatable(
                fighting ? "message.undeadworkforce.fighting_on" : "message.undeadworkforce.fighting_off"), true);
        return true;
    }

    private static boolean pickUp(Player player, Mob mob) {
        Minions.PickUpResult result = Minions.pickUp(player, mob);
        switch (result) {
            case OK -> player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.7F);
            case TOO_MANY -> player.displayClientMessage(
                    Component.translatable("message.undeadworkforce.carry_limit", Minions.MAX_CARRIED), true);
            case INVENTORY_FULL -> player.displayClientMessage(
                    Component.translatable("message.undeadworkforce.inventory_full"), true);
            case FAILED -> { }
        }
        return result == Minions.PickUpResult.OK;
    }
}
