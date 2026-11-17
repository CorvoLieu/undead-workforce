package dev.corvo.undeadworkforce.minion.goal;

import dev.corvo.undeadworkforce.minion.MinionMode;
import dev.corvo.undeadworkforce.minion.Minions;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Fights the minion's current target with whatever it holds: melee with a weapon or bare hands,
 * or arrows from a bow/crossbow. Two instances are installed: one that chases (FOLLOW / WANDER)
 * and one that holds position (STAY), which only claims LOOK so the stay goal keeps it in place.
 */
public class MinionCombatGoal extends Goal {
    private static final double CHASE_SPEED = 1.2;
    /** Archers close in to this distance, then stand and shoot. */
    private static final double PREFERRED_SHOT_DISTANCE = 12.0;
    private static final double MAX_SHOT_DISTANCE = 20.0;
    private static final int MELEE_COOLDOWN = 20;
    private static final int BOW_COOLDOWN = 25;
    private static final int CROSSBOW_COOLDOWN = 40;
    /** Player full-draw arrow speeds; with the default 2.0 arrow damage this matches a player's shot. */
    private static final float BOW_VELOCITY = 3.0F;
    private static final float CROSSBOW_VELOCITY = 3.15F;

    private final Mob mob;
    private final boolean holdPosition;
    private int attackCooldown;
    private int repathIn;

    public MinionCombatGoal(Mob mob, boolean holdPosition) {
        this.mob = mob;
        this.holdPosition = holdPosition;
        setFlags(holdPosition ? EnumSet.of(Flag.LOOK) : EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return Minions.isFighting(mob)
                && (Minions.mode(mob) == MinionMode.STAY) == holdPosition
                && Minions.canTarget(mob, mob.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        repathIn = 0;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        if (!holdPosition) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean canSee = mob.getSensing().hasLineOfSight(target);
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        ItemStack weapon = mob.getMainHandItem();
        if (Minions.isRangedWeapon(weapon)) {
            double distSq = mob.distanceToSqr(target);
            if (!holdPosition) {
                if (!canSee || distSq > PREFERRED_SHOT_DISTANCE * PREFERRED_SHOT_DISTANCE) {
                    chase(target);
                } else {
                    mob.getNavigation().stop();
                }
            }
            if (canSee && attackCooldown <= 0 && distSq <= MAX_SHOT_DISTANCE * MAX_SHOT_DISTANCE) {
                boolean crossbow = weapon.getItem() instanceof CrossbowItem;
                shoot(target, weapon, crossbow ? CROSSBOW_VELOCITY : BOW_VELOCITY);
                attackCooldown = crossbow ? CROSSBOW_COOLDOWN : BOW_COOLDOWN;
            }
        } else {
            if (!holdPosition) {
                chase(target);
            }
            if (canSee && attackCooldown <= 0 && mob.isWithinMeleeAttackRange(target)) {
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target); // damage = ATTACK_DAMAGE (player base 1.0) + weapon modifiers
                attackCooldown = MELEE_COOLDOWN;
            }
        }
    }

    private void chase(LivingEntity target) {
        if (--repathIn <= 0) {
            repathIn = 10;
            mob.getNavigation().moveTo(target, CHASE_SPEED);
        }
    }

    /** Arrows are free (like a skeleton's) and can't be picked up. Weapon enchantments apply. */
    private void shoot(LivingEntity target, ItemStack weapon, float velocity) {
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = new Arrow(mob.level(), mob, ammo, weapon);
        if (weapon.getItem() instanceof ProjectileWeaponItem weaponItem) {
            arrow = weaponItem.customArrow(arrow, ammo, weapon);
        }
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - mob.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.1, dz, velocity, 1.0F);
        mob.playSound(weapon.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT,
                1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
        mob.level().addFreshEntity(arrow);
    }
}
