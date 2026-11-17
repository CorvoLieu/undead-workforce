package dev.corvo.undeadworkforce.minion;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.menu.MinionMenu;
import dev.corvo.undeadworkforce.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

@EventBusSubscriber(modid = UndeadWorkforce.MOD_ID)
public final class MinionEvents {
    private MinionEvents() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob mob)) {
            return;
        }
        if (event.getItemStack().is(ModItems.ZOMBIE_COLLAR) && Minions.canBeCollared(mob)) {
            tryCollar(event, mob);
        } else if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().isEmpty()
                && Minions.isWearingCollar(mob)) {
            openMenu(event, mob);
        }
    }

    /** Holding a Zombie Collar: right-click (sneaking or not) a collarable zombie to capture it. */
    private static void tryCollar(PlayerInteractEvent.EntityInteract event, Mob zombie) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();

        event.setCanceled(true);
        if (!zombie.isAlive() || Minions.isWearingCollar(zombie) || Minions.isMinion(zombie)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.undeadworkforce.already_collared"), true);
            }
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return; // client only swings the arm; server does the work
        }

        Minions.collar(zombie, player, stack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        ServerLevel server = (ServerLevel) level;
        double x = zombie.getX(), y = zombie.getY() + zombie.getBbHeight() * 0.75, z = zombie.getZ();
        server.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.NEUTRAL, 1.0F, 0.8F);
        server.playSound(null, x, y, z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.4F, 1.6F);
        server.sendParticles(ParticleTypes.SOUL, x, y, z, 12, 0.3, 0.4, 0.3, 0.02);
        server.sendParticles(ParticleTypes.ENCHANT, x, y, z, 20, 0.4, 0.4, 0.4, 0.5);
        player.displayClientMessage(Component.translatable("message.undeadworkforce.collared"), true);
    }

    /** Empty hand on a collared zombie: the owner gets the gear/order menu; anyone else gets told off. */
    private static void openMenu(PlayerInteractEvent.EntityInteract event, Mob mob) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // the server decides whether the menu opens
        }
        if (Minions.isOwnedBy(mob, player)) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MinionMenu(id, inventory, mob),
                    mob.getDisplayName()), buf -> buf.writeVarInt(mob.getId()));
        } else {
            player.displayClientMessage(Component.translatable("message.undeadworkforce.not_your_minion"), true);
        }
    }

    /** The pack spills out when a minion dies. (Gear drops by itself: its drop chance is guaranteed.) */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !Minions.isMinion(entity)) {
            return;
        }
        ItemStackHandler pack = Minions.pack(entity);
        for (int i = 0; i < pack.getSlots(); i++) {
            ItemStack stack = pack.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), stack.copy());
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
                pack.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** AI goals aren't persisted: set minion AI up again whenever one is loaded into the world. */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob && Minions.isMinion(mob)) {
            Minions.setUpAi(mob);
        }
    }

    /**
     * Gatekeeper for every target change, including vanilla code that calls setTarget directly:
     * <ul>
     *   <li>A minion may only target something while fighting is on, and never a player, minion or
     *       its owner's pet ({@link Minions#canTarget}).</li>
     *   <li>Other mobs (golems, hostiles) leave minions alone, except to fight back against a minion
     *       that is attacking them.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity source = event.getEntity();
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) {
            return;
        }
        if (Minions.isMinion(source)) {
            if (!(source instanceof Mob minion) || !Minions.isFighting(minion) || !Minions.canTarget(minion, newTarget)) {
                event.setCanceled(true);
            }
        } else if (Minions.isMinion(newTarget) && !(source instanceof Player)) {
            boolean fightingBack = newTarget instanceof Mob minion
                    && (minion.getTarget() == source || minion.getLastHurtMob() == source);
            if (!fightingBack) {
                event.setCanceled(true);
            }
        }
    }

    /** Minion arrows pass through players and other minions: no friendly fire. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && event.getProjectile().getOwner() instanceof Mob shooter && Minions.isMinion(shooter)
                && (hit.getEntity() instanceof Player || Minions.isMinion(hit.getEntity()))) {
            event.setCanceled(true);
        }
    }

    /** Carried minions are heavy: Slowness I for 1–2, Slowness II for 3–4 (more, if hoarded via chests). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        int carried = Minions.countCarried(player);
        if (carried > 0) {
            int amplifier = (carried - 1) / 2;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, amplifier, false, false, true));
        }
    }
}
