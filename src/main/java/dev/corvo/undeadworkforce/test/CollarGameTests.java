package dev.corvo.undeadworkforce.test;

import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.menu.MinionMenu;
import dev.corvo.undeadworkforce.minion.MinionCommand;
import dev.corvo.undeadworkforce.minion.MinionCommands;
import dev.corvo.undeadworkforce.minion.MinionMode;
import dev.corvo.undeadworkforce.minion.Minions;
import dev.corvo.undeadworkforce.registry.ModItems;
import java.lang.reflect.Method;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dev-only checks; run with `./gradlew runGameTestServer`. Not loaded in normal play. */
@GameTestHolder(UndeadWorkforce.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollarGameTests {
    private CollarGameTests() {}

    @GameTest(template = "test_arena")
    public static void collarCapturesZombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ZOMBIE_COLLAR.get(), 2));

        InteractionResult result = player.interactOn(zombie, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(), "interaction should succeed, got " + result);
        helper.assertTrue(Minions.isMinion(zombie), "zombie should be a minion");
        helper.assertTrue(Minions.isWearingCollar(zombie), "zombie should wear the collar");
        helper.assertTrue(Minions.owner(zombie).orElseThrow().equals(player.getUUID()), "owner should be the player");
        helper.assertTrue(zombie.isPersistenceRequired(), "minion should never despawn");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "one collar should be consumed");

        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() == null, "minion must refuse to target anyone");

        InteractionResult again = player.interactOn(zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(!again.consumesAction(), "re-collaring should fail");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "failed re-collar must not consume");
        helper.succeed();
    }

    /** Gametest arenas don't get reliable sky light, so check the patched method directly. */
    @GameTest(template = "test_arena")
    public static void minionIsNotSunSensitive(GameTestHelper helper) throws ReflectiveOperationException {
        Zombie control = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        Zombie minion = helper.spawn(EntityType.ZOMBIE, 3, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ZOMBIE_COLLAR.get()));
        player.interactOn(minion, InteractionHand.MAIN_HAND);

        Method isSunSensitive = Zombie.class.getDeclaredMethod("isSunSensitive");
        isSunSensitive.setAccessible(true);
        helper.assertTrue((boolean) isSunSensitive.invoke(control), "plain zombie should burn in sunlight");
        helper.assertTrue(!(boolean) isSunSensitive.invoke(minion), "collared zombie should not burn in sunlight");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void ordersOnlyFromOwnerInRange(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Player stranger = playerAt(helper, 1, 1, 3);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);

        helper.assertTrue(Minions.mode(minion) == MinionMode.FOLLOW, "new minions should follow");
        helper.assertTrue(!MinionCommands.apply(stranger, minion, MinionCommand.STAY), "stranger must not command");
        helper.assertTrue(Minions.mode(minion) == MinionMode.FOLLOW, "stranger's order must not apply");
        helper.assertTrue(MinionCommands.apply(owner, minion, MinionCommand.STAY), "owner should command");
        helper.assertTrue(Minions.mode(minion) == MinionMode.STAY, "mode should now be STAY");

        owner.moveTo(owner.getX() + 100, owner.getY(), owner.getZ());
        helper.assertTrue(!MinionCommands.apply(owner, minion, MinionCommand.WANDER), "out-of-range order must fail");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void pickUpAndReleaseRoundTrip(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);
        MinionCommands.apply(owner, minion, MinionCommand.STAY);

        helper.assertTrue(MinionCommands.apply(owner, minion, MinionCommand.PICK_UP), "pick up should succeed");
        helper.assertTrue(minion.isRemoved(), "picked-up zombie should leave the world");
        helper.assertTrue(Minions.countCarried(owner) == 1, "player should carry one minion");

        ItemStack carried = owner.getInventory().items.stream()
                .filter(s -> s.is(ModItems.CARRIED_ZOMBIE)).findFirst().orElseThrow();
        Entity released = Minions.release(helper.getLevel(), carried, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0)
                .orElseThrow();
        helper.assertTrue(released instanceof Zombie, "released entity should be a zombie");
        helper.assertTrue(Minions.isOwnedBy(released, owner), "owner should survive the round trip");
        helper.assertTrue(Minions.isWearingCollar((Zombie) released), "collar should survive the round trip");
        helper.assertTrue(Minions.mode(released) == MinionMode.STAY, "order should survive the round trip");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void carryLimitIsFour(GameTestHelper helper) {
        Player owner = playerAt(helper, 2, 1, 2);
        for (int i = 0; i < Minions.MAX_CARRIED; i++) {
            Zombie z = collaredZombie(helper, owner, 1 + i % 3, 1, 1 + i / 3);
            helper.assertTrue(MinionCommands.apply(owner, z, MinionCommand.PICK_UP), "pick up #" + (i + 1));
        }
        Zombie fifth = collaredZombie(helper, owner, 3, 1, 3);
        helper.assertTrue(!MinionCommands.apply(owner, fifth, MinionCommand.PICK_UP), "fifth pick up must fail");
        helper.assertTrue(fifth.isAlive() && !fifth.isRemoved(), "fifth zombie must stay in the world");
        helper.assertTrue(Minions.countCarried(owner) == Minions.MAX_CARRIED, "still carrying exactly four");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void gearSlotsOnlyAcceptMatchingItems(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);
        MinionMenu menu = new MinionMenu(0, owner.getInventory(), minion);

        helper.assertTrue(menu.getSlot(MinionMenu.WEAPON).mayPlace(new ItemStack(Items.IRON_SWORD)), "sword is a weapon");
        helper.assertTrue(!menu.getSlot(MinionMenu.WEAPON).mayPlace(new ItemStack(Items.DIRT)), "dirt is not a weapon");
        helper.assertTrue(menu.getSlot(MinionMenu.WEAPON).mayPlace(new ItemStack(Items.BOW)), "bow is a weapon");
        helper.assertTrue(menu.getSlot(MinionMenu.WEAPON).mayPlace(new ItemStack(Items.CROSSBOW)), "crossbow is a weapon");
        helper.assertTrue(!menu.getSlot(MinionMenu.WEAPON).mayPlace(new ItemStack(Items.TRIDENT)), "trident is not a weapon");
        helper.assertTrue(menu.getSlot(MinionMenu.TOOL).mayPlace(new ItemStack(Items.IRON_PICKAXE)), "pickaxe is a tool");
        helper.assertTrue(!menu.getSlot(MinionMenu.TOOL).mayPlace(new ItemStack(Items.IRON_SWORD)), "sword is not a tool");
        helper.assertTrue(menu.getSlot(MinionMenu.ARMOR).mayPlace(new ItemStack(Items.IRON_CHESTPLATE)), "chestplate is armor");
        helper.assertTrue(!menu.getSlot(MinionMenu.ARMOR).mayPlace(new ItemStack(Items.IRON_HELMET)), "only chest armor fits");
        helper.assertTrue(menu.getSlot(MinionMenu.COLLAR).hasItem(), "collar slot shows the worn collar");
        helper.assertTrue(!menu.getSlot(MinionMenu.COLLAR).mayPickup(owner), "collar can't be taken off");
        helper.assertTrue(menu.stillValid(owner), "owner in range may use the menu");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void shiftClickEquipsGearThenFillsPack(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);
        MinionMenu menu = new MinionMenu(0, owner.getInventory(), minion);
        int hotbar0 = MinionMenu.MINION_SLOTS + 27; // menu index of hotbar slot 0

        owner.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        menu.quickMoveStack(owner, hotbar0);
        helper.assertTrue(minion.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "sword should be wielded");

        owner.getInventory().setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
        menu.quickMoveStack(owner, hotbar0);
        helper.assertTrue(minion.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "chestplate should be worn");

        owner.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        menu.quickMoveStack(owner, hotbar0);
        helper.assertTrue(Minions.pack(minion).getStackInSlot(0).is(Items.IRON_SWORD), "second sword goes to the pack");

        owner.getInventory().setItem(0, new ItemStack(Items.DIRT, 32));
        menu.quickMoveStack(owner, hotbar0);
        helper.assertTrue(Minions.pack(minion).getStackInSlot(1).getCount() == 32, "dirt stack goes to the pack");
        helper.assertTrue(owner.getInventory().getItem(0).isEmpty(), "player's slot should be emptied");

        menu.quickMoveStack(owner, MinionMenu.WEAPON);
        helper.assertTrue(minion.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "sword should be unequipped");
        helper.assertTrue(owner.getInventory().countItem(Items.IRON_SWORD) == 1, "sword should be back with the player");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void menuButtonsGiveOrders(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);
        MinionMenu menu = new MinionMenu(0, owner.getInventory(), minion);

        helper.assertTrue(menu.clickMenuButton(owner, MinionCommand.STAY.ordinal()), "stay button should work");
        helper.assertTrue(menu.mode() == MinionMode.STAY, "menu should report the new order");
        helper.assertTrue(!menu.clickMenuButton(owner, 99), "unknown button must be ignored");
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void gearAndPackSurvivePickUpAndDropOnDeath(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 3, 1, 3);
        MinionMenu menu = new MinionMenu(0, owner.getInventory(), minion);
        menu.getSlot(MinionMenu.TOOL).set(new ItemStack(Items.IRON_SHOVEL));
        menu.getSlot(MinionMenu.EQUIPMENT_SLOTS).set(new ItemStack(Items.BONE, 7));

        helper.assertTrue(MinionCommands.apply(owner, minion, MinionCommand.PICK_UP), "pick up should succeed");
        helper.assertTrue(!menu.stillValid(owner), "menu must close once the minion is picked up");
        ItemStack carried = owner.getInventory().items.stream()
                .filter(s -> s.is(ModItems.CARRIED_ZOMBIE)).findFirst().orElseThrow();
        Zombie released = (Zombie) Minions.release(helper.getLevel(), carried, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0)
                .orElseThrow();
        helper.assertTrue(released.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.IRON_SHOVEL), "tool survives the trip");
        helper.assertTrue(Minions.pack(released).getStackInSlot(0).getCount() == 7, "pack survives the trip");

        released.kill();
        helper.assertItemEntityPresent(Items.BONE, new BlockPos(2, 1, 2), 3.0);
        helper.assertItemEntityPresent(Items.IRON_SHOVEL, new BlockPos(2, 1, 2), 3.0);
        helper.assertItemEntityPresent(ModItems.ZOMBIE_COLLAR.get(), new BlockPos(2, 1, 2), 3.0);
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void unarmedMinionHitsLikeAPlayer(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 2, 1, 2);
        Pig pig = helper.spawn(EntityType.PIG, 3, 1, 2);
        pig.setNoAi(true);

        helper.assertTrue(minion.getAttributeValue(Attributes.ATTACK_DAMAGE) == Minions.UNARMED_DAMAGE,
                "bare-hand damage should be a player's, got " + minion.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float before = pig.getHealth();
        minion.doHurtTarget(pig);
        helper.assertTrue(before - pig.getHealth() == 1.0F, "a punch should deal 1 damage, dealt " + (before - pig.getHealth()));
        helper.succeed();
    }

    @GameTest(template = "test_arena")
    public static void peacefulMinionIgnoresHostiles(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 2, 1, 2);
        MinionCommands.apply(owner, minion, MinionCommand.STAY);
        sturdyDummy(helper, EntityType.HUSK, 4, 1, 4);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(minion.getTarget() == null, "fighting is off: no target expected");
            helper.succeed();
        });
    }

    @GameTest(template = "test_arena")
    public static void fightingMinionAttacksHostilesButNotCreepers(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 2, 1, 2);
        MinionCommands.apply(owner, minion, MinionCommand.WANDER);
        helper.assertTrue(MinionCommands.apply(owner, minion, MinionCommand.TOGGLE_FIGHT), "toggle should work");
        helper.assertTrue(Minions.isFighting(minion), "fighting should be on");
        Creeper creeper = sturdyDummy(helper, EntityType.CREEPER, 3, 1, 2); // closer than the husk
        Mob husk = sturdyDummy(helper, EntityType.HUSK, 4, 1, 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(minion.getTarget() != creeper, "must not pick a fight with a creeper");
            helper.assertTrue(minion.getTarget() == husk, "should target the husk");
        });
    }

    @GameTest(template = "test_arena")
    public static void fightingMinionHelpsOwnerAgainstCreeper(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        owner.moveTo(pos.x, pos.y, pos.z);
        Zombie minion = collaredZombie(helper, owner, 2, 1, 2);
        MinionCommands.apply(owner, minion, MinionCommand.TOGGLE_FIGHT);
        Creeper creeper = sturdyDummy(helper, EntityType.CREEPER, 4, 1, 4);

        owner.setLastHurtMob(creeper);
        helper.succeedWhen(() -> helper.assertTrue(minion.getTarget() == creeper, "should join the owner's fight"));
    }

    @GameTest(template = "test_arena")
    public static void fightButtonTogglesAndOffDropsTarget(GameTestHelper helper) {
        Player owner = playerAt(helper, 1, 1, 1);
        Zombie minion = collaredZombie(helper, owner, 2, 1, 2);
        MinionMenu menu = new MinionMenu(0, owner.getInventory(), minion);
        Mob husk = sturdyDummy(helper, EntityType.HUSK, 4, 1, 4);

        helper.assertTrue(menu.clickMenuButton(owner, MinionCommand.TOGGLE_FIGHT.ordinal()), "button should work");
        helper.assertTrue(menu.isFighting(), "menu should report fighting");
        minion.setTarget(husk);
        helper.assertTrue(minion.getTarget() == husk, "fighting minion may target a hostile");
        menu.clickMenuButton(owner, MinionCommand.TOGGLE_FIGHT.ordinal());
        helper.assertTrue(!menu.isFighting() && minion.getTarget() == null, "turning it off drops the target");
        minion.setTarget(husk);
        helper.assertTrue(minion.getTarget() == null, "non-fighting minion must refuse targets");
        helper.succeed();
    }

    /** A hostile that stands still and survives a beating, so tests can watch targeting. */
    private static <T extends Mob> T sturdyDummy(GameTestHelper helper, EntityType<T> type, int x, int y, int z) {
        T mob = helper.spawn(type, x, y, z);
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        mob.setHealth(1000.0F);
        return mob;
    }

    private static Player playerAt(GameTestHelper helper, int x, int y, int z) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
        player.moveTo(pos.x, pos.y, pos.z);
        return player;
    }

    private static Zombie collaredZombie(GameTestHelper helper, Player owner, int x, int y, int z) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, y, z);
        Minions.collar(zombie, owner, new ItemStack(ModItems.ZOMBIE_COLLAR.get()));
        return zombie;
    }
}
