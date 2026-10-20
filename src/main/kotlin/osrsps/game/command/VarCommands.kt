package osrsps.game.command

/** Read and set the typing player's varps and varbits. The client is sent any change at the end of the tick. */
fun CommandRegistry.registerVarCommands() {
    register("varp", "::varp id [value]") {
        if (args.size !in 1..2) fail("Usage: ::varp id [value]")
        val vars = player.vars
        val id = number(args[0], "::varp id [value]")
        if (id !in 0 until vars.types.varpCount) fail("Varps are 0 to ${vars.types.varpCount - 1}")
        val old = vars[id]
        if (args.size == 1) {
            reply("Varp $id = $old")
            return@register
        }
        val value = number(args[1], "::varp id [value]")
        vars[id] = value
        reply("Varp $id: $old -> $value")
    }

    register("varbit", "::varbit id [value]") {
        if (args.size !in 1..2) fail("Usage: ::varbit id [value]")
        val vars = player.vars
        val id = number(args[0], "::varbit id [value]")
        val type = vars.types.varbit(id) ?: fail("No varbit $id in the cache")
        val where = "varp ${type.baseVar} bits ${type.startBit}-${type.endBit}"
        val old = vars.getVarbit(id)
        if (args.size == 1) {
            reply("Varbit $id ($where) = $old")
            return@register
        }
        val value = number(args[1], "::varbit id [value]")
        if (type.mask != -1 && value !in 0..type.mask) fail("Varbit $id holds 0 to ${type.mask}")
        vars.setVarbit(id, value)
        reply("Varbit $id ($where): $old -> $value, varp ${type.baseVar} is now ${vars[type.baseVar]}")
    }
}

private fun CommandContext.number(text: String, usage: String): Int =
    text.toIntOrNull() ?: fail("Usage: $usage ('$text' is not a number)")
