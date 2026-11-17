# Undead Workforce

A NeoForge mod inspired by Graveyard Keeper's zombie workers: collar zombies, command them, carry them, and (eventually) put them to work. Built for the owner's modpack at `../corvos-minecraft-modpack` (CreativeWorld), whose `manifest/pack.json` pins the Minecraft/NeoForge versions. Keep `gradle.properties` `neo_version` in step with it.

Don't copy Graveyard Keeper's names, art, or text. Use its mechanics as inspiration only.

## Stack
- Minecraft 1.21.1, NeoForge 21.1.x, ModDevGradle, Java 21 (Gradle provisions the toolchain; system Java is 25)
- Mojang mappings + Parchment. Mixins are enabled (`undeadworkforce.mixins.json`), no refmap needed.

## Commands
- `./gradlew build`: jar in `build/libs/`
- `./gradlew runClient`: dev client for in-game testing
- `./gradlew runGameTestServer`: headless GameTests (`test/CollarGameTests.java`, arena `data/undeadworkforce/structure/test_arena.nbt`). Run after any gameplay change.

## Design (current)
- **Zombie Collar** (gold ring + diamond pendant recipe). Use it on any `#undeadworkforce:collarable` mob → minion.
- Minion state lives in **data attachments**: `minion_owner` (UUID; its presence = "is a minion") and `minion_mode` (FOLLOW/STAY/WANDER). Server-only, not synced.
- The collar item sits in the mob's **BODY equipment slot**. Vanilla syncs and saves it, so clients use it to know "this is a minion" (renderer, menu) with no custom sync. It always drops on death.
- Minion AI is set up in `Minions.setUpAi` on collar **and** on every `EntityJoinLevelEvent`, because goals aren't saved. Target goals are removed. Priority-0 Stay/Follow goals override vanilla movement.
- No sunburn (mixin on `Zombie.isSunSensitive`). Unarmed damage = a player's (ATTACK_DAMAGE base 1.0), so weapons add what they add for a player. Zombie reinforcements are disabled.
- **Fighting** (`minion_fighting` attachment, off by default, Fight toggle in the menu). Vanilla target/attack goals are stripped in `setUpAi`; ours: `MinionOwnerTargetGoal` (defend the owner from anything but players; assist on any `Enemy` the owner hits, the only way creepers get targeted), `MinionHostileTargetGoal` (auto-target `Enemy` except creepers and `NeutralMob`), `MinionCombatGoal` (melee, or free non-pickupable arrows at player full-draw speed with a bow/crossbow; one chasing instance, one LOOK-only instance for STAY). Range: 16 blocks (FOLLOW also within 16 of the owner); STAY melee only within 3. Never targets players, minions, or the owner's pets.
- `LivingChangeTargetEvent` is the gatekeeper: minions can only target via the rules above; other mobs (golems, hostiles) may only target a minion that is fighting them. Minion arrows pass through players and minions (`ProjectileImpactEvent`).
- **Gear menu** (`menu/MinionMenu` + `client/MinionScreen`): empty-hand right-click your minion → server `openMenu` (extra data = entity id). Slots: weapon (MAINHAND; swords, axes, bows, crossbows), tool (OFFHAND), armor (CHEST), collar (BODY, locked: shows the collar, can't be removed), then a 5-slot **pack** (`minion_pack` attachment, an `ItemStackHandler`). What fits each gear slot is data-driven: item tags `minion_weapons` / `minion_tools` / `minion_armor`. Gear given to a minion gets a guaranteed drop chance; the pack spills on death (`LivingDropsEvent`). Gear and pack ride along in the carried item NBT. Minions can't pick up loot.
- **Orders** are buttons inside the gear menu, sent as vanilla menu button clicks (`clickMenuButton`, id = `MinionCommand` ordinal), so there are no custom packets. The current order syncs via a menu `DataSlot`. Everything re-validates owner + 16-block range via `Minions.canManage`.
- The screen is drawn with plain fills in the vanilla palette (no GUI texture yet); empty gear slots use vanilla outline sprites.
- **Carrying**: Pick Up turns the minion into a `carried_zombie` item (full entity NBT in the `carried_entity` component, UUID stripped). Max 4 per player. Carrying gives Slowness I (1–2) / II (3–4). Placing it or letting a tossed item land releases the minion. Storing them in chests is currently allowed (open design question).
- Client-only classes live in `client/`. Common code must never reference them directly.
- Textures are hand-written pixel grids rendered to PNG by small Python scripts (not kept in the repo). Editing the PNGs directly is fine.

## Roadmap / ideas
- Jobs for minions (Graveyard Keeper-style work: hauling, farming, mining, crafting stations)
- Collar slot is the hook for future leveling/upgrades (swap collars). Gear stats aren't used yet beyond vanilla attributes (weapon damage, armor points).
- Maybe a sound/visual tell for the current mode. Maybe make collars obtainable/upgradable differently.
- Add the built jar to the modpack via `../corvos-minecraft-modpack/local-mods/` when ready to ship (it must be on both server and client).
