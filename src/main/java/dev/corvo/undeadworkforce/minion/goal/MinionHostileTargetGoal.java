package dev.corvo.undeadworkforce.minion.goal;

import dev.corvo.undeadworkforce.minion.Minions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Fighting minions pick fights with nearby hostiles on their own. Creepers and neutral mobs
 * (endermen, zombified piglins, ...) are left alone unless the owner starts it.
 */
public class MinionHostileTargetGoal extends NearestAttackableTargetGoal<Mob> {
    public MinionHostileTargetGoal(Mob mob) {
        super(mob, Mob.class, 10, true, false,
                target -> Minions.isAutoTarget(target) && Minions.canTarget(mob, target)
                        && Minions.inEngageRange(mob, target, 0.0));
    }

    @Override
    protected double getFollowDistance() {
        return Minions.ENGAGE_RANGE; // search box; the predicate narrows it further in STAY mode
    }

    @Override
    public boolean canUse() {
        return Minions.isFighting(mob) && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return Minions.isFighting(mob) && Minions.canTarget(mob, mob.getTarget())
                && Minions.inEngageRange(mob, mob.getTarget(), Minions.ENGAGE_SLACK) && super.canContinueToUse();
    }
}
