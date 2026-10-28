package osrsps.game.command

import osrsps.game.Experience
import osrsps.game.PlayerSave
import osrsps.game.PlayerSkills
import osrsps.game.Scenario
import osrsps.game.Skill
import osrsps.game.World

/**
 * Jump into a testing scenario and back out. `::scenario name` remembers the player as they were the first time, so a
 * second scenario still resets to before the first; `::reset` puts that back and forgets it. Both are kept in the save,
 * so a logout in the middle loses nothing. Both end every fight the player is in, so a test script starts and leaves
 * with no NPC still hitting them.
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
        start(world, scenario)
        reply("Started scenario ${scenario.name}.${scenario.about?.let { " $it" }.orEmpty()} ::reset puts you back.")
    }

    register("reset", "::reset") {
        if (args.isNotEmpty()) fail("Usage: ::reset")
        val before = player.beforeScenario ?: fail("Nothing to reset: you haven't started a ::scenario since the last ::reset.")
        val name = player.scenario
        world.scripts.clearAll(player)
        world.meleeCombat.endFights(player)
        before.restoreTo(player)
        world.teleport(player, before.x, before.z, before.level)
        player.scenario = null
        player.beforeScenario = null
        reply("Back to how you were before ::scenario${name?.let { " $it" }.orEmpty()}.")
    }
}

/** Checks everything first, so a scenario that can't start leaves the player as they were. */
private fun CommandContext.start(world: World, scenario: Scenario) {
    val loadout = try {
        scenario.loadout(world.objTypes)
    } catch (e: IllegalArgumentException) {
        fail("Scenario ${scenario.name} can't start: ${e.message}")
    }
    val vars = player.vars
    for (varp in scenario.varps.keys) {
        if (varp >= vars.types.varpCount) fail("Scenario ${scenario.name} can't start: the cache has no varp $varp")
    }
    for ((id, value) in scenario.varbits) {
        val type = vars.types.varbit(id) ?: fail("Scenario ${scenario.name} can't start: the cache has no varbit $id")
        if (type.mask != -1 && value !in 0..type.mask) fail("Scenario ${scenario.name} can't start: varbit $id holds 0 to ${type.mask}, not $value")
    }
    if (!world.hasMap(scenario.x, scenario.z)) fail("Scenario ${scenario.name} can't start: no map at ${scenario.x}, ${scenario.z}")

    if (player.beforeScenario == null) player.beforeScenario = PlayerSave.of(player)
    world.scripts.clearAll(player)
    world.meleeCombat.endFights(player)
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
    // A scenario's charged items start empty.
    player.charges.clear()
    for ((varp, value) in scenario.varps) vars[varp] = value
    for ((id, value) in scenario.varbits) vars.setVarbit(id, value)
    world.teleport(player, scenario.x, scenario.z, scenario.level)
    player.scenario = scenario.name
}
