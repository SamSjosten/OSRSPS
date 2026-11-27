package osrsps.game.command

import osrsps.game.Experience
import osrsps.game.Player
import osrsps.game.PlayerSave
import osrsps.game.PlayerSkills
import osrsps.game.Scenario
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.combat.Teleblock
import osrsps.game.tzhaar.FightCaveWaves

/**
 * Jump into a testing scenario and back out. `::scenario name` remembers the player as they were the first time, so a
 * second scenario still resets to before the first; `::reset` puts that back and forgets it. Both are kept in the save,
 * so a logout in the middle loses nothing. Both end every fight the player is in, so a test script starts and leaves
 * with no NPC still hitting them, and clear their skull, PJ timer and Tele Block.
 */
fun CommandRegistry.registerScenarioCommands(world: World) {
    register("scenario", "::scenario [name]") {
        if (args.size > 1) fail("Usage: ::scenario [name]")
        val names = world.scenarios.names.joinToString().ifEmpty { "none" }
        if (args.isEmpty()) {
            val current = player.scenario?.let { " You're in $it; ::reset puts you back." }.orEmpty()
            reply("Scenarios: $names.$current")
            return@register
        }
        val scenario = world.scenarios[args[0]] ?: fail("No scenario '${args[0]}'. Scenarios: $names")
        reply(startScenario(world, player, scenario))
    }

    register("reset", "::reset") {
        if (args.isNotEmpty()) fail("Usage: ::reset")
        reply(resetScenario(world, player))
    }
}

/**
 * Starts [scenario] for [player], as `::scenario name` does, and returns its reply. Throws [CommandFailure] with the
 * reason, leaving the player as they were, when it can't start. For the commands and for testing NPCs that start a
 * fixed scenario for anyone, such as the raid tester ([osrsps.game.content.RaidTester]).
 */
internal fun startScenario(world: World, player: Player, scenario: Scenario): String {
    start(world, player, scenario)
    return "Started scenario ${scenario.name}.${scenario.about?.let { " $it" }.orEmpty()} ::reset puts you back."
}

/**
 * Puts [player] back as they were before their first scenario, as `::reset` does, and returns its reply. Throws
 * [CommandFailure] when there is nothing to put back.
 */
internal fun resetScenario(world: World, player: Player): String {
    val before = player.beforeScenario ?: refuse("Nothing to reset: you haven't started a ::scenario since the last ::reset.")
    val name = player.scenario
    // An open trade ends first, its offer back in the backpack, so nothing is restored under it: the raid tester puts
    // back without ::reset's own interrupt (World.onCommand). Exploit review 40, R40C-5.
    if (player.trade != null) world.trades.interrupt(player)
    world.scripts.clearAll(player)
    world.meleeCombat.endFights(player)
    // A stopped Hunter run's traps come down too, so none stands for the next script.
    world.hunter.traps.takeDown(player)
    player.testCheatsOff()
    // A Fight Cave or Inferno run of the setup's ends first, keeping the run saved from before: ended later, by the
    // process seeing them leave, it would forget the run restoreTo just put back (exploit review R45B-5).
    world.fightCaves.endRun(player)
    world.inferno.endRun(player)
    // The account's random event timer and its ::randomevent off stay as they are now, as starting the setup kept
    // them: the snapshot's would wind the timer back, so an event could be had per setup (exploit review R50A-1). An
    // event NPC out goes, so none crosses into the account.
    val kept = osrsps.game.content.randomevents.RandomEvents.ACCOUNT_CHARGES.associateWith { player.charges[it] }
    world.randomEvents.end(player)
    // The pet cat out now goes, and the one the snapshot's follower varps hold comes back beside them.
    world.cats.beforeRestore(player)
    before.restoreTo(player)
    world.cats.afterRestore(player)
    for ((name, value) in kept) player.charges[name] = value
    // Poison, venom and an antipoison's immunity from before the setup don't come back with its varp: put back, an
    // account poisoned once outside a setup carried it into every later ::reset, its timer wound back each time,
    // until the poison killed it in the next script (nightly 2026-11-26, settings-dropdowns after sepulchre-of-death).
    world.poison.clear(player)
    // A trail being followed ends, and the restored varbits show none of its footprints.
    world.hunter.tracking.forget(player)
    // After the snapshot, which holds the skull, whom they attacked first and the Tele Block's varbit from before the
    // scenario: ::reset clears those too (Codex review on #723). Teleblock.clear skips a player with no block, so its
    // varbit is cleared here, or RuneLite would show a timer for a block that's gone.
    world.pvpCombat.clear(player)
    player.vars.setVarbitIfKnown(Teleblock.VARBIT, 0)
    // Varp 83 came back with the rest; the overhead icon and the drain go with the prayer book, so it starts afresh.
    world.prayers.deactivateAll(player)
    // A stall owner's grudge from a test theft goes too, so the next script can trade with them.
    world.thieving.forgive(player)
    world.pickpocketing.forgive(player)
    // The NPCs the player put in with ::npc or ::raid <boss> go too, a test fight ending with its boss as ::raid stop
    // ends it: a script that failed before its own clean-up left them for the next to meet (nightly 2026-11-08:
    // aggro-leash's spider beside aggro-sight's, and a Guardian fight on the login tile killing test2 on each respawn).
    for (npc in world.npcs.all.filter { it.spawn.source == spawnSource(player.username) }.toList()) {
        world.encounters.all.firstOrNull { it.boss === npc }?.let(world.encounters::stop)
        world.removeNpc(npc)
    }
    // The doors and gates the player moved and nobody has moved since shut or open again as the cache has them, so a
    // script that stopped part way leaves none for the next (nightly 2026-11-08, Edgeville's).
    world.locs.restoreOwnedBy(player.saveKey)
    // A grave the scenario raised goes too, its items being the scenario's.
    world.deaths.dropScenarioGrave(player)
    world.teleport(player, before.x, before.z, before.level)
    player.scenario = null
    player.beforeScenario = null
    return "Back to how you were before ::scenario${name?.let { " $it" }.orEmpty()}."
}

private fun refuse(message: String): Nothing = throw CommandFailure(message)

/** Checks everything first, so a scenario that can't start leaves the player as they were. */
private fun start(world: World, player: Player, scenario: Scenario) {
    val loadout = try {
        scenario.loadout(world.objTypes)
    } catch (e: IllegalArgumentException) {
        refuse("Scenario ${scenario.name} can't start: ${e.message}")
    }
    val vars = player.vars
    for (varp in scenario.varps.keys) {
        if (varp >= vars.types.varpCount) refuse("Scenario ${scenario.name} can't start: the cache has no varp $varp")
    }
    for ((id, value) in scenario.varbits) {
        val type = vars.types.varbit(id) ?: refuse("Scenario ${scenario.name} can't start: the cache has no varbit $id")
        if (type.mask != -1 && value !in 0..type.mask) refuse("Scenario ${scenario.name} can't start: varbit $id holds 0 to ${type.mask}, not $value")
    }
    if (!world.hasMap(scenario.x, scenario.z)) refuse("Scenario ${scenario.name} can't start: no map at ${scenario.x}, ${scenario.z}")

    // Traps laid before a first scenario come back to the backpack first, so its snapshot (and ::reset) keeps them;
    // an earlier scenario's own traps just come down.
    // An open trade ends first, its offer back in the backpack, so the snapshot holds it (R40C-5).
    if (player.trade != null) world.trades.interrupt(player)
    world.hunter.traps.takeDown(player, refund = player.beforeScenario == null)
    if (player.beforeScenario == null) player.beforeScenario = world.snapshot(player)
    world.cats.startScenario(player)
    world.hunter.tracking.forget(player)
    // Unclaimed raid loot is in the snapshot, which puts it back: claimed during the scenario as well, it would come
    // twice (exploit review 40, R40C-2). A scenario starts with the chest empty, as with the bank.
    player.archiveChest.clear()
    // Nor Treasure Trails: STASH contents, casket counts, scroll cases and trails are in the snapshot, which puts them
    // back, so a STASH emptied during the scenario would come back full (exploit review 49, R49A-1 and R49A-3).
    player.trails.replaceWith(osrsps.game.trail.TrailProgress())
    // The lobby's Loot Wheelbarrow reads its varbit, which goes with the chest (Codex review on #783).
    world.archiveChestWindow.showClaimable(player)
    // Nor a grave from an earlier scenario whose script died before its ::reset: the scenario's death would merge into it.
    world.deaths.dropScenarioGrave(player)
    // The snapshot holds the items in the guide prices screen; the scenario's inventory replaces them.
    player.priceChecker.clear()
    world.scripts.clearAll(player)
    world.meleeCombat.endFights(player)
    // Nor a skull, a PJ timer or a Tele Block from a fight before.
    world.pvpCombat.clear(player)
    // A divine potion drunk before would hold the scenario's boosts and then drop them; ::reset puts its varbits back.
    world.potions.clearDivine(player)
    // Nor poison, venom or an antipoison's immunity from before; ::reset doesn't put them back either.
    world.poison.clear(player)
    world.statusEffects.clear(player)
    player.testCheatsOff()
    // Full special attack energy, as the run energy is full; a scenario's varps can set it otherwise.
    world.specialAttacks.refill(player)
    for (skill in Skill.entries) {
        val set = scenario.skills[skill]
        val level = set?.level ?: PlayerSkills.newLevel(skill)
        player.skills.setTenths(skill, Experience.xpForLevel(level) * 10)
        player.skills.setCurrentLevel(skill, set?.current ?: level)
    }
    player.runEnergy = scenario.runEnergy
    scenario.run?.let { player.runEnabled = it }
    player.inventory.clear()
    for ((slot, obj) in loadout.inventory) player.inventory[slot] = obj
    player.equipment.clear()
    for ((slot, obj) in loadout.equipment) player.equipment[slot] = obj
    // A scenario's charged items start empty, and its essence pouches too.
    // The account's random event timer and its ::randomevent off aren't the scenario's to reset.
    val kept = osrsps.game.content.randomevents.RandomEvents.ACCOUNT_CHARGES.associateWith { player.charges[it] }
    // An event NPC out goes too, so none from the account pays into the setup (R50A-1).
    world.randomEvents.end(player)
    player.charges.clear()
    for ((name, value) in kept) player.charges[name] = value
    world.essencePouches.emptyAll(player)
    world.bankScreen.clear(player)
    for ((slot, obj) in loadout.bank) player.bank[slot] = obj
    // A scenario starts with no Grand Exchange offers or buy limits used; ::reset puts them back.
    player.ge.clear()
    // Nor any farming: every patch overgrown and the clock running, so a script finds what it expects; ::reset puts
    // the player's own patches back.
    player.farming.replaceWith(osrsps.game.skill.farming.FarmingState())
    val tabs = world.bankScreen.tabs(player)
    loadout.bankTabs.forEachIndexed { i, size -> tabs[i + 1] = size }
    for ((varp, value) in scenario.varps) vars[varp] = value
    for ((id, value) in scenario.varbits) vars.setVarbit(id, value)
    // The scenario's levels may not allow the prayers that were on.
    world.prayers.deactivateAll(player)
    world.teleport(player, scenario.x, scenario.z, scenario.level)
    player.scenario = scenario.name
    scenario.minigame?.let { startMinigame(world, player, scenario, it) }
}

/** Puts [player] in [scenario]'s minigame; the rest of the scenario stands if it can't start. */
private fun startMinigame(world: World, player: Player, scenario: Scenario, minigame: Scenario.ScenarioMinigame) {
    when (minigame.name) {
        Scenario.ScenarioMinigame.FIGHT_CAVE -> {
            val wave = minigame.wave.takeIf { it in 1..FightCaveWaves.WAVES } ?: refuse("Scenario ${scenario.name}: the fight cave has no wave ${minigame.wave}")
            val rotation = minigame.rotation ?: world.fightCaves.randomRotation()
            if (rotation !in 1..FightCaveWaves.ROTATIONS) refuse("Scenario ${scenario.name}: the fight cave has no rotation $rotation")
            if (!world.fightCaves.start(player, wave, rotation)) refuse("Scenario ${scenario.name}: no room for another fight cave")
        }
        Scenario.ScenarioMinigame.INFERNO -> {
            val wave = minigame.wave.takeIf { it in 1..osrsps.game.tzhaar.InfernoWaves.WAVES } ?: refuse("Scenario ${scenario.name}: the Inferno has no wave ${minigame.wave}")
            world.inferno.sacrifice(player)
            if (!world.inferno.start(player, wave, fresh = true)) refuse("Scenario ${scenario.name}: no room for another Inferno")
        }
    }
}

/**
 * `::god` and `::sure` off: a scenario starts, and `::reset` ends, without them, so a test script that failed with them
 * on doesn't leave the next one hurt-proof or never missing.
 */
private fun Player.testCheatsOff() {
    god = false
    sureHits = false
    sureMaxHits = false
}
