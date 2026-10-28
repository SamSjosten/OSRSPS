package osrsps.game.command

import osrsps.game.IpBans
import osrsps.game.World

/** Who connects from where, and the bans by address that last across restarts; see [IpBans]. */
fun CommandRegistry.registerIpCommands(world: World) {
    val bans = world.ipBans

    register("ip", "::ip [name]", admin = true) {
        if (args.size > 1) fail("Usage: ::ip [name]")
        if (args.isEmpty()) {
            reply(world.onlinePlayers.joinToString { "${it.username} ${it.address}" })
            return@register
        }
        val target = world.playerNamed(args[0]) ?: fail("Nobody called ${args[0]} is in the world")
        reply("${target.username} is on ${target.address}")
    }

    register("ipban", "::ipban name|address [note]", admin = true) {
        if (args.isEmpty()) fail("Usage: ::ipban name|address [note]")
        val target = world.playerNamed(args[0])
        val address = target?.address ?: args[0]
        val note = (listOf("by ${player.username}") + listOfNotNull(target?.let { "(${it.username})" }) + args.drop(1)).joinToString(" ")
        when (bans.ban(address, note)) {
            IpBans.Result.NOT_AN_ADDRESS -> fail("${args[0]} is neither a player in the world nor an IP address")
            IpBans.Result.RELAY -> fail("$address is the relay every internet player comes through; ban the player once they connect with their own address")
            IpBans.Result.ALREADY_BANNED -> reply("$address is already banned")
            else -> {
                val kicked = world.onlinePlayers.filter { bans.isBanned(it.address) }
                kicked.forEach { it.logoutRequested = true }
                reply("Banned $address" + if (kicked.isEmpty()) "" else "; logging out ${kicked.joinToString { it.username }}")
            }
        }
    }

    register("ipunban", "::ipunban address", admin = true) {
        if (args.size != 1) fail("Usage: ::ipunban address")
        when (bans.unban(args[0])) {
            IpBans.Result.NOT_AN_ADDRESS -> fail("${args[0]} isn't an IP address")
            IpBans.Result.NOT_BANNED -> reply("${args[0]} isn't banned")
            else -> reply("Unbanned ${args[0]}")
        }
    }

    register("ipbans", "::ipbans", admin = true) {
        val all = bans.all
        reply(if (all.isEmpty()) "No addresses are banned" else all.entries.joinToString("; ") { (address, note) -> "$address $note".trim() })
    }
}
