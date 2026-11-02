package osrsps.game.command

import osrsps.game.content.CustomVars

/**
 * Read and set the typing player's varps and varbits. The client is sent any change at the end of the tick. A var of
 * our own ([CustomVars]) goes by its name too (`::varp lodestones`, `::varbit lodestone.varrock`), as its id follows
 * the cache the server loaded and so isn't one a runner script can know.
 */
fun CommandRegistry.registerVarCommands(customVars: CustomVars) {
    val varpUsage = "::varp id|name [value]"
    val varbitUsage = "::varbit id|name [value]"

    register("varp", varpUsage) {
        if (args.size !in 1..2) fail("Usage: $varpUsage")
        val vars = player.vars
        val named = customVars.varp(args[0])
        val id = named ?: number(args[0], varpUsage, customVars.varpNames.keys)
        if (id !in 0 until vars.types.varpCount) fail("Varps are 0 to ${vars.types.varpCount - 1}")
        val label = if (named != null) args[0].lowercase() else "$id"
        val about = if (named != null) " (id $id)" else ""
        val old = vars[id]
        if (args.size == 1) {
            reply("Varp $label = $old$about")
            return@register
        }
        val value = number(args[1], varpUsage)
        vars[id] = value
        reply("Varp $label: $old -> $value$about")
    }

    register("varbit", varbitUsage) {
        if (args.size !in 1..2) fail("Usage: $varbitUsage")
        val vars = player.vars
        val named = customVars.varbit(args[0])
        val id = named ?: number(args[0], varbitUsage, customVars.varbitNames.keys)
        val type = vars.types.varbit(id) ?: fail("No varbit $id in the cache")
        val label = if (named != null) args[0].lowercase() else "$id"
        val where = (if (named != null) "id $id, " else "") + "varp ${type.baseVar} bits ${type.startBit}-${type.endBit}"
        val old = vars.getVarbit(id)
        if (args.size == 1) {
            reply("Varbit $label ($where) = $old")
            return@register
        }
        val value = number(args[1], varbitUsage)
        if (type.mask != -1 && value !in 0..type.mask) fail("Varbit $label holds 0 to ${type.mask}")
        vars.setVarbit(id, value)
        reply("Varbit $label ($where): $old -> $value, varp ${type.baseVar} is now ${vars[type.baseVar]}")
    }
}

private fun CommandContext.number(text: String, usage: String, names: Collection<String> = emptyList()): Int =
    text.toIntOrNull() ?: fail(
        "Usage: $usage ('$text' is not a number" + (if (names.isEmpty()) "" else " or one of ${names.sorted().joinToString()}") + ")",
    )
