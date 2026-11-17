package dev.corvo.undeadworkforce.item;

import dev.corvo.undeadworkforce.minion.Minions;
import dev.corvo.undeadworkforce.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * A picked-up minion. Place it on a block to set it back down; if tossed (or dropped on death),
 * the minion climbs out as soon as the item lands, so carried minions are never lost to despawning.
 */
public class CarriedZombieItem extends Item {
    public CarriedZombieItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() + 180.0F : 0.0F;
        if (Minions.release(level, context.getItemInHand(), pos, yaw).isEmpty()) {
            return InteractionResult.FAIL;
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.9F);
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity itemEntity) {
        if (itemEntity.level() instanceof ServerLevel level && itemEntity.onGround()
                && Minions.release(level, stack, itemEntity.position(), itemEntity.getYRot()).isPresent()) {
            itemEntity.discard();
            return true;
        }
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        return storedType(stack)
                .map(type -> (Component) Component.translatable("item.undeadworkforce.carried_zombie.of", type.getDescription()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.undeadworkforce.carried_zombie.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.undeadworkforce.carried_zombie.weight", Minions.MAX_CARRIED)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Optional<EntityType<?>> storedType(ItemStack stack) {
        CustomData data = stack.get(ModComponents.CARRIED_ENTITY);
        return data == null ? Optional.empty() : EntityType.by(data.copyTag());
    }
}
