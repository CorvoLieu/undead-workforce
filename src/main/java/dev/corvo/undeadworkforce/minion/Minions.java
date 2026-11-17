package dev.corvo.undeadworkforce.minion;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.minion.goal.MinionCombatGoal;
import dev.corvo.undeadworkforce.minion.goal.MinionFollowOwnerGoal;
import dev.corvo.undeadworkforce.minion.goal.MinionHostileTargetGoal;
import dev.corvo.undeadworkforce.minion.goal.MinionOwnerTargetGoal;
import dev.corvo.undeadworkforce.minion.goal.MinionStayGoal;
import dev.corvo.undeadworkforce.registry.ModAttachments;
import dev.corvo.undeadworkforce.registry.ModComponents;
import dev.corvo.undeadworkforce.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Helpers for turning a zombie into a minion and querying/changing minion state. */
public final class Minions {
    /** Entity types that accept a collar. Data-driven so packs can add/remove types. */
    public static final TagKey<EntityType<?>> COLLARABLE =
            TagKey.create(Registries.ENTITY_TYPE, UndeadWorkforce.id("collarable"));

    /** What fits in each gear slot of the minion menu. Data-driven like {@link #COLLARABLE}. */
    public static final TagKey<Item> MINION_WEAPONS = TagKey.create(Registries.ITEM, UndeadWorkforce.id("minion_weapons"));
    public static final TagKey<Item> MINION_TOOLS = TagKey.create(Registries.ITEM, UndeadWorkforce.id("minion_tools"));
    public static final TagKey<Item> MINION_ARMOR = TagKey.create(Registries.ITEM, UndeadWorkforce.id("minion_armor"));

    /** How many minions a player may carry at once (Graveyard Keeper's limit). */
    public static final int MAX_CARRIED = 4;
    /** How close the owner must be to give orders through the menu. */
    public static final double COMMAND_RANGE = 16.0;

    /** Drop chance above 1.0 = always dropped, undamaged, on death. */
    private static final float GUARANTEED_DROP = 2.0F;
    private static final double FOLLOW_SPEED = 1.25;

    /** How far a fighting minion looks for trouble (and, when following, how far from its owner). */
    public static final double ENGAGE_RANGE = 16.0;
    /** A holding (STAY) melee minion only swings at what comes this close. */
    public static final double GUARD_MELEE_RANGE = 3.0;
    /** Extra distance allowed before a minion gives up a fight it already started. */
    public static final double ENGAGE_SLACK = 4.0;
    /** A player's bare-hand damage. Minions use it instead of their own (difficulty-tuned) base. */
    public static final double UNARMED_DAMAGE = 1.0;

    private Minions() {}

    // ---- queries ---------------------------------------------------------------------------

    public static boolean canBeCollared(Entity entity) {
        return entity instanceof Mob && entity.getType().is(COLLARABLE);
    }

    /** Server-side truth: has an owner attachment. */
    public static boolean isMinion(Entity entity) {
        return entity.hasData(ModAttachments.MINION_OWNER);
    }

    /** Client-safe check: the collar sits in the (synced) body slot. Used by the renderer and menu. */
    public static boolean isWearingCollar(Mob mob) {
        return mob.getItemBySlot(EquipmentSlot.BODY).is(ModItems.ZOMBIE_COLLAR);
    }

    public static Optional<UUID> owner(Entity entity) {
        return isMinion(entity) ? Optional.of(entity.getData(ModAttachments.MINION_OWNER)) : Optional.empty();
    }

    public static boolean isOwnedBy(Entity entity, Player player) {
        return owner(entity).map(player.getUUID()::equals).orElse(false);
    }

    /** The owner if they're online and in the same dimension, else null. */
    public static Player onlineOwner(Mob mob) {
        return owner(mob).map(mob.level()::getPlayerByUUID).orElse(null);
    }

    /** Owner, alive, and within command range: may give orders and open the gear menu. */
    public static boolean canManage(Mob mob, Player player) {
        return mob.isAlive() && !mob.isRemoved() && isOwnedBy(mob, player)
                && player.distanceToSqr(mob) <= COMMAND_RANGE * COMMAND_RANGE;
    }

    /** The minion's 5-slot pack (server side; created empty on first access). */
    public static ItemStackHandler pack(Entity entity) {
        return entity.getData(ModAttachments.MINION_PACK);
    }

    public static MinionMode mode(Entity entity) {
        return isMinion(entity) ? entity.getData(ModAttachments.MINION_MODE) : MinionMode.WANDER;
    }

    public static boolean isFighting(Entity entity) {
        return isMinion(entity) && entity.getData(ModAttachments.MINION_FIGHTING);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem;
    }

    /**
     * Who a minion may ever fight: never players, other minions, or its owner's pets.
     * Null-safe so goals can pass {@code mob.getTarget()} straight in.
     */
    public static boolean canTarget(Mob mob, LivingEntity target) {
        if (target == null || target == mob || !target.isAlive() || target instanceof Player || isMinion(target)) {
            return false;
        }
        if (target instanceof OwnableEntity pet && pet.getOwnerUUID() != null
                && owner(mob).map(pet.getOwnerUUID()::equals).orElse(false)) {
            return false;
        }
        return mob.canAttack(target);
    }

    /** Hostiles a fighting minion attacks unprompted: no creepers, nothing neutral (endermen etc.). */
    public static boolean isAutoTarget(LivingEntity target) {
        return target instanceof Enemy && !(target instanceof Creeper) && !(target instanceof NeutralMob);
    }

    /**
     * Range check for picking ({@code slack} 0) or keeping ({@code slack} {@link #ENGAGE_SLACK}) a target.
     * STAY melee minions only fight what's within reach; following minions stay near their owner.
     */
    public static boolean inEngageRange(Mob mob, LivingEntity target, double slack) {
        double range = (mode(mob) == MinionMode.STAY && !isRangedWeapon(mob.getMainHandItem())
                ? GUARD_MELEE_RANGE : ENGAGE_RANGE) + slack;
        if (mob.distanceToSqr(target) > range * range) {
            return false;
        }
        if (mode(mob) == MinionMode.FOLLOW) {
            Player owner = onlineOwner(mob);
            double leash = ENGAGE_RANGE + slack;
            return owner == null || owner.distanceToSqr(target) <= leash * leash;
        }
        return true;
    }

    // ---- capture & orders ------------------------------------------------------------------

    public static void collar(Mob mob, Player owner, ItemStack collar) {
        equip(mob, EquipmentSlot.BODY, collar.copyWithCount(1));
        mob.setData(ModAttachments.MINION_OWNER, owner.getUUID());
        mob.setData(ModAttachments.MINION_MODE, MinionMode.FOLLOW);
        mob.setPersistenceRequired();
        setUpAi(mob);
    }

    /** Puts gear on a minion. Anything the player gives it always drops, undamaged, on death. */
    public static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        if (!stack.isEmpty()) {
            mob.setDropChance(slot, GUARANTEED_DROP);
        }
    }

    /**
     * Replace the mob's own hostility with minion AI. Goals aren't saved, so this also runs every
     * time a minion is loaded into a level. Safe to call more than once.
     */
    public static void setUpAi(Mob mob) {
        // Vanilla targeting (players, villagers, golems, turtles) and attack goals go; ours replace them.
        mob.targetSelector.removeAllGoals(goal ->
                !(goal instanceof MinionOwnerTargetGoal) && !(goal instanceof MinionHostileTargetGoal));
        mob.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal || goal instanceof RangedAttackGoal
                || goal instanceof RangedBowAttackGoal<?> || goal instanceof RangedCrossbowAttackGoal<?>);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setCanPickUpLoot(false); // would swap out the gear the owner gave it

        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(UNARMED_DAMAGE); // weapons then add exactly what they add for a player
        }
        AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcements != null) {
            reinforcements.removeModifiers(); // a hurt minion must not summon hostile zombies
            reinforcements.setBaseValue(0.0);
        }

        boolean installed = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof MinionStayGoal);
        if (!installed) {
            // Priority 0 outranks every vanilla zombie/drowned movement goal.
            mob.goalSelector.addGoal(0, new MinionStayGoal(mob));
            mob.goalSelector.addGoal(0, new MinionFollowOwnerGoal(mob, FOLLOW_SPEED));
            mob.goalSelector.addGoal(1, new MinionCombatGoal(mob, false));
            mob.goalSelector.addGoal(1, new MinionCombatGoal(mob, true));
            mob.targetSelector.addGoal(1, new MinionOwnerTargetGoal(mob));
            mob.targetSelector.addGoal(2, new MinionHostileTargetGoal(mob));
        }
    }

    /** Turns fighting on or off. Off drops the current target immediately. */
    public static void setFighting(Mob mob, boolean fighting) {
        mob.setData(ModAttachments.MINION_FIGHTING, fighting);
        if (!fighting) {
            mob.setTarget(null);
        }
    }

    public static void setMode(Mob mob, MinionMode mode) {
        mob.setData(ModAttachments.MINION_MODE, mode);
        mob.getNavigation().stop();
    }

    // ---- carrying --------------------------------------------------------------------------

    public static int countCarried(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.CARRIED_ZOMBIE)) {
                count++;
            }
        }
        return count;
    }

    public enum PickUpResult { OK, TOO_MANY, INVENTORY_FULL, FAILED }

    /** Turns the minion into a Carried Zombie item in the player's inventory. */
    public static PickUpResult pickUp(Player player, Mob mob) {
        if (countCarried(player) >= MAX_CARRIED) {
            return PickUpResult.TOO_MANY;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            return PickUpResult.INVENTORY_FULL;
        }
        mob.stopRiding();
        mob.ejectPassengers();
        mob.dropLeash(true, true);
        CompoundTag tag = new CompoundTag();
        if (!mob.save(tag)) {
            return PickUpResult.FAILED;
        }
        tag.remove("UUID"); // released copy gets a fresh UUID, so duplicated items can't collide

        ItemStack stack = new ItemStack(ModItems.CARRIED_ZOMBIE.get());
        stack.set(ModComponents.CARRIED_ENTITY, CustomData.of(tag));
        mob.discard();
        player.getInventory().add(stack);
        return PickUpResult.OK;
    }

    /** Spawns the minion stored in a Carried Zombie item. Returns the entity, or empty if the data is bad. */
    public static Optional<Entity> release(ServerLevel level, ItemStack stack, Vec3 pos, float yaw) {
        CustomData data = stack.get(ModComponents.CARRIED_ENTITY);
        if (data == null) {
            return Optional.empty();
        }
        Entity entity = EntityType.loadEntityRecursive(data.copyTag(), level, loaded -> {
            loaded.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            return loaded;
        });
        if (entity == null || !level.tryAddFreshEntityWithPassengers(entity)) {
            UndeadWorkforce.LOGGER.warn("Could not release carried minion from {}", stack);
            return Optional.empty();
        }
        return Optional.of(entity);
    }
}
