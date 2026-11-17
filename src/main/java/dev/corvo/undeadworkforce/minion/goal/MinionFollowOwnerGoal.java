package dev.corvo.undeadworkforce.minion.goal;

import dev.corvo.undeadworkforce.minion.MinionMode;
import dev.corvo.undeadworkforce.minion.Minions;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Pet-style following: walk after the owner, teleport next to them if left far behind. */
public class MinionFollowOwnerGoal extends Goal {
    private static final double START_DISTANCE = 6.0;
    private static final double STOP_DISTANCE = 2.5;
    private static final double TELEPORT_DISTANCE = 20.0;
    /** While fighting, the minion lets the owner get this far before breaking off to follow. */
    private static final double COMBAT_LEASH = 12.0;

    private final Mob mob;
    private final double speed;
    private Player owner;
    private int recalcPathIn;

    public MinionFollowOwnerGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (Minions.mode(mob) != MinionMode.FOLLOW) {
            return false;
        }
        Player found = Minions.onlineOwner(mob);
        double start = mob.getTarget() != null ? COMBAT_LEASH : START_DISTANCE;
        if (found == null || found.isSpectator() || mob.distanceToSqr(found) < start * start) {
            return false;
        }
        owner = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return Minions.mode(mob) == MinionMode.FOLLOW
                && owner.isAlive() && owner.level() == mob.level() && !owner.isSpectator()
                && mob.distanceToSqr(owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start() {
        recalcPathIn = 0;
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (--recalcPathIn > 0) {
            return;
        }
        recalcPathIn = adjustedTickDelay(10);
        if (mob.distanceToSqr(owner) >= TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            tryTeleportToOwner();
        } else {
            mob.getNavigation().moveTo(owner, speed);
        }
    }

    private void tryTeleportToOwner() {
        BlockPos base = owner.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = mob.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = mob.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue; // not on top of the owner
            }
            int dy = mob.getRandom().nextIntBetweenInclusive(-1, 1);
            BlockPos target = base.offset(dx, dy, dz);
            if (canTeleportTo(target)) {
                mob.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
                return;
            }
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) != PathType.WALKABLE) {
            return false;
        }
        BlockPos delta = pos.subtract(mob.blockPosition());
        return mob.level().noCollision(mob, mob.getBoundingBox().move(delta));
    }
}
