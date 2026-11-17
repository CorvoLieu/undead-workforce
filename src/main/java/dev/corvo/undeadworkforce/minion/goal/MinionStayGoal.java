package dev.corvo.undeadworkforce.minion.goal;

import dev.corvo.undeadworkforce.minion.MinionMode;
import dev.corvo.undeadworkforce.minion.Minions;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Holds the MOVE/JUMP flags while in STAY mode, which blocks every lower-priority wandering goal. */
public class MinionStayGoal extends Goal {
    private final Mob mob;

    public MinionStayGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return Minions.mode(mob) == MinionMode.STAY;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }
}
