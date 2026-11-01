package osrsps.game.command

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.Party
import osrsps.game.raid.PartyResult
import osrsps.game.raid.PartyScaling

/**
 * `::raidparty`, standing in for the raid lobby's Grouping Board until a recording shows how it works (raid step 11), and
 * `::raidnpc`, which puts in a raid NPC with its hitpoints for a party size, to check the scaling against the Wiki.
 * Their words are this server's own, not live's.
 */
fun CommandRegistry.registerPartyCommands(world: World) {
    val parties = world.parties
    val usage = "Usage: ::raidparty | ::raidparty invite|accept|kick name | ::raidparty leave"

    register("raidparty", "::raidparty [invite|accept|kick name | leave]", admin = true) {
        fun named(): Player {
            val name = args.drop(1).joinToString(" ").ifEmpty { fail(usage) }
            return world.playerNamed(name) ?: fail("No one called $name is online")
        }
        when (args.firstOrNull()?.lowercase()) {
            null -> reply(describe(parties.of(player), player))
            "invite" -> {
                val target = named()
                refuse(parties.invite(player, target), target)
                val party = parties.of(player)!!
                reply("Invited ${target.username} to your party (${party.size} of ${PartyScaling.MAX_PARTY}).")
                target.message("${player.username} invited you to their party: ::raidparty accept ${player.username}")
            }
            "accept" -> {
                val leader = named()
                refuse(parties.accept(player, leader), leader)
                val party = parties.of(player)!!
                reply("You joined ${leader.username}'s party (${party.size} of ${PartyScaling.MAX_PARTY}).")
                for (member in party.members) if (member != player) member.message("${player.username} joined the party.")
            }
            "kick" -> {
                val target = named()
                parties.of(player) ?: fail("You're not in a party.")
                refuse(parties.kick(player, target), target)
                reply("Put ${target.username} out of the party.")
                target.message("${player.username} put you out of their party.")
                for (member in parties.of(player)!!.members) if (member != player) member.message("${target.username} was put out of the party.")
            }
            "leave" -> {
                if (args.size != 1) fail(usage)
                val party = parties.leave(player) ?: fail("You're not in a party.")
                reply("You left the party.")
                val leads = party.members.firstOrNull()?.let { "; ${it.username} leads it now" }.orEmpty()
                for (member in party.members) member.message("${player.username} left the party$leads.")
            }
            else -> fail(usage)
        }
    }

    register("raidnpc", "::raidnpc id|name [party size]", admin = true) {
        val raidUsage = "Usage: ::raidnpc id|name [party size]"
        if (args.isEmpty()) fail(raidUsage)
        val size = args.takeIf { it.size > 1 }?.last()?.toIntOrNull()
        val text = (if (size != null) args.dropLast(1) else args).joinToString(" ")
        if (size != null && size !in 1..PartyScaling.MAX_PARTY) fail("The party size must be 1 to ${PartyScaling.MAX_PARTY}")
        val type = world.npcTypes.lookup(text) ?: fail("No NPC '$text'. Try its id, its name, or its gameval name like tfa_guardian")
        if (type !in world.partyScaling) fail("${type.name} (${type.id}) doesn't scale with party size")
        // Without a size, the party you are in, or one of you alone.
        val players = size ?: parties.of(player)?.size ?: 1
        val spawn = NpcSpawn(type, player.x, player.z, player.level, source = spawnSource(player.username), respawns = false)
        val npc = world.spawnNpc(spawn) ?: fail("No free NPC slot")
        val hitpoints = world.partyScaling.scale(npc, players)!!
        val counted = if (npc.scaledFor != players) " (counted as ${npc.scaledFor})" else ""
        reply("Spawned ${type.name} (${type.id}) as #${npc.index} at ${npc.x}, ${npc.z} for a party of $players$counted: $hitpoints hp")
    }
}

private fun CommandContext.refuse(result: PartyResult, other: Player) {
    when (result) {
        PartyResult.DONE -> return
        PartyResult.NOT_LEADER -> fail("Only your party's leader can do that.")
        PartyResult.IN_A_PARTY -> fail("You're in a party already: ::raidparty leave first.")
        PartyResult.ALREADY_MEMBER -> fail("${other.username} is in your party already.")
        PartyResult.FULL -> fail("The party is full: ${PartyScaling.MAX_PARTY} is the most.")
        PartyResult.NOT_INVITED -> fail("${other.username} hasn't invited you.")
        PartyResult.NO_PARTY -> fail("${other.username} doesn't lead a party.")
        PartyResult.NOT_A_MEMBER -> fail("${other.username} isn't in your party.")
        PartyResult.SELF -> fail("Not yourself.")
    }
}

private fun describe(party: Party?, player: Player): String {
    if (party == null) return "You're not in a party."
    val names = party.members.joinToString { it.username }
    val whose = if (party.leader == player) "You lead a party" else "You're in ${party.leader.username}'s party"
    return "$whose of ${party.size}: $names"
}
