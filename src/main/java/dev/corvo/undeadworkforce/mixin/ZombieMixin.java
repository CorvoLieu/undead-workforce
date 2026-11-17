package dev.corvo.undeadworkforce.mixin;

import dev.corvo.undeadworkforce.minion.Minions;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Collared zombies don't burn in daylight — a worker that ignites at dawn isn't much use. */
@Mixin(Zombie.class)
public abstract class ZombieMixin {
    @Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
    private void undeadworkforce$minionsIgnoreSun(CallbackInfoReturnable<Boolean> cir) {
        if (Minions.isMinion((Zombie) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
