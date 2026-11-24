package osrsps.game.command

import osrsps.game.PlayerLook
import osrsps.game.World

/** `::look`, a testing tool that shows and sets the player's look ([PlayerLook]) without a makeover screen. */
fun CommandRegistry.registerLookCommands(world: World) {
    val usage = "::look [body a|b] [kit part id] [colour part n] [reset] [design]"
    register("look", usage) {
        val look = player.look
        val parts = PlayerLook.BodyPart.NAMES
        val colourParts = PlayerLook.ColourPart.entries.map { it.name.lowercase() }
        val changed = when (args.getOrNull(0)?.lowercase()) {
            null -> {
                reply(describe(look))
                return@register
            }
            "reset" -> PlayerLook.DEFAULT
            // The Character Creator a new account gets at its first login, for testing it on an old one.
            "design" -> {
                if (!world.designScreen.available()) fail("The cache has no makeover pages for the Character Creator")
                world.designScreen.open(player)
                reply("Opened the Character Creator")
                return@register
            }
            "body" -> {
                val bodyType = when (args.getOrNull(1)?.lowercase()) {
                    "a" -> PlayerLook.BODY_TYPE_A
                    "b" -> PlayerLook.BODY_TYPE_B
                    else -> fail("Usage: ::look body a|b")
                }
                val kits = world.idkTypes.firstKits(bodyType) ?: fail("The cache has no kits for body type ${args[1]}")
                // The pronoun follows the body type, as the makeover screen's does unless they/them is picked.
                look.copy(bodyType = bodyType, kits = kits, pronoun = if (look.pronoun == THEY) THEY else bodyType)
            }
            "kit" -> {
                if (args.size != 3) fail("Usage: ::look kit ${parts.joinToString("|")} id")
                val part = parts.indexOf(args[1].lowercase()).takeIf { it >= 0 } ?: fail("Parts: ${parts.joinToString()}")
                val kit = args[2].toIntOrNull() ?: fail("Usage: ::look kit ${args[1]} id")
                if (!world.idkTypes.fits(kit, part, look.bodyType)) {
                    fail("Kit $kit isn't a ${parts[part]} of body type ${if (look.bodyType == PlayerLook.BODY_TYPE_A) "A" else "B"}")
                }
                look.withKit(part, kit)
            }
            "colour", "color" -> {
                if (args.size != 3) fail("Usage: ::look colour ${colourParts.joinToString("|")} n")
                val part = PlayerLook.ColourPart.entries.getOrNull(colourParts.indexOf(args[1].lowercase()))
                    ?: fail("Colours: ${colourParts.joinToString()}")
                val colour = args[2].toIntOrNull()?.takeIf { it in 0 until part.count }
                    ?: fail("${args[1]} colours are 0 to ${part.count - 1}")
                look.withColour(part, colour)
            }
            else -> fail("Usage: $usage")
        }
        world.setLook(player, changed)
        reply(describe(changed))
    }
}

private const val THEY = 2

private fun describe(look: PlayerLook): String {
    val body = if (look.bodyType == PlayerLook.BODY_TYPE_A) "A" else "B"
    val kits = PlayerLook.BodyPart.NAMES.zip(look.kits).joinToString { (part, kit) -> "$part $kit" }
    val colours = PlayerLook.ColourPart.entries.zip(look.colours).joinToString { (part, colour) -> "${part.name.lowercase()} $colour" }
    val pronoun = listOf("he/him", "she/her", "they/them")[look.pronoun]
    return "Look: body type $body; kits $kits; colours $colours; pronouns $pronoun"
}
