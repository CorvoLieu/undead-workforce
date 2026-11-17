package dev.corvo.undeadworkforce.minion.goal;

import dev.corvo.undeadworkforce.minion.Minions;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Takes its cue from the owner (fighting minions only): attack whatever just hurt the owner, or any
 * hostile the owner just hit. This is the only way a minion goes after a creeper.
 */
public class MinionOwnerTargetGoal extends TargetGoal {
    /** Owner events older than this are ignored (e.g. a hit from before the minion was told to fight). */
    private static final int MAX_EVENT_AGE = 100;

    private LivingEntity candidate;
    private int lastDefendTimestamp = -1;
    private int lastAssistTimestamp = -1;

    public MinionOwnerTargetGoal(Mob mob) {
        super(mob, false);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!Minions.isFighting(mob)) {
            return false;
        }
        Player owner = Minions.onlineOwner(mob);
        if (owner == null) {
            return false;
        }
        int defendTs = owner.getLastHurtByMobTimestamp();
        if (defendTs != lastDefendTimestamp && isRecent(owner, defendTs)
                && isValid(owner.getLastHurtByMob())) {
            candidate = owner.getLastHurtByMob();
            lastDefendTimestamp = defendTs;
            return true;
        }
        int assistTs = owner.getLastHurtMobTimestamp();
        if (assistTs != lastAssistTimestamp && isRecent(owner, assistTs)
                && owner.getLastHurtMob() instanceof Enemy && isValid(owner.getLastHurtMob())) {
            candidate = owner.getLastHurtMob();
            lastAssistTimestamp = assistTs;
            return true;
        }
        return false;
    }

    private static boolean isRecent(Player owner, int timestamp) {
        return owner.tickCount - timestamp <= MAX_EVENT_AGE;
    }

    private boolean isValid(LivingEntity target) {
        return Minions.canTarget(mob, target) && Minions.inEngageRange(mob, target, 0.0)
                && canAttack(target, TargetingConditions.DEFAULT);
    }

    @Override
    public boolean canContinueToUse() {
        return Minions.isFighting(mob) && Minions.canTarget(mob, mob.getTarget())
                && Minions.inEngageRange(mob, mob.getTarget(), Minions.ENGAGE_SLACK) && super.canContinueToUse();
    }

    @Override
    public void start() {
        mob.setTarget(candidate);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        candidate = null;
    }
}
