package osrsps.testclient

import osrsps.game.content.MUSEUM_VARBITS

/** A museum varbit's value from the varps the client holds, by [MUSEUM_VARBITS]. */
internal fun HeadlessClient.varbit(id: Int): Int {
    val type = MUSEUM_VARBITS.getValue(id)
    return varp(type.baseVar) shr type.startBit and ((1 shl (type.endBit - type.startBit + 1)) - 1)
}

internal fun HeadlessClient.awaitVarbit(id: Int, value: Int) = await("varbit $id = $value") { if (varbit(id) == value) Unit else null }

/** Sets a museum varbit with `::varbit`, waiting until the client has it. */
internal fun HeadlessClient.setVarbit(id: Int, value: Int) {
    command("varbit $id $value")
    awaitVarbit(id, value)
}
