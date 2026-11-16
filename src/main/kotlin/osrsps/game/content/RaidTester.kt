package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.World
import osrsps.game.command.CommandFailure
import osrsps.game.command.resetScenario
import osrsps.game.command.startScenario
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * The raid tester's lines: ours, not Jagex's, as the NPC is a testing tool with no live counterpart. Their words are in
 * the dialogue file like every other line, so the server's text all lives in one place.
 */
object RaidTesterLines : Lines("raid_tester", "ours") {
    val GREETING = line("greeting", "ours: Talk-to, his first line: he kits playtesters out for The Fractured Archive and sends them to its door, and he puts them back")
    val MELEE = line("melee", "ours: his menu, option 1: the melee loadout")
    val RANGED = line("ranged", "ours: his menu, option 2: the ranged loadout")
    val MAGIC = line("magic", "ours: his menu, option 3: the magic loadout")
    val PUT_BACK = line("put_back", "ours: his menu, option 4: back to how the player was before the loadout")
    val NOTHING = line("nothing", "ours: his menu, option 5: nothing")
}

/**
 * The raid tester: a playtesting helper for The Fractured Archive. **Testing only, not live content**: live has no such
 * NPC, so nothing here is 1:1 and none of it should be taken for live behaviour. Sam asked for it on 16 November 2026,
 * to get playtesters to the raid's start with what it needs.
 *
 * His Talk-to offers three loadouts, each a testing scenario ([osrsps.game.Scenario], `tfa-melee`, `tfa-ranged` and
 * `tfa-magic` in the resources' scenarios): 99 in every combat skill, the named style worn and a hybrid inventory for
 * the whole raid, in the lobby in front of the Archive Door ([osrsps.game.raid.ArchiveRaids.LOBBY_X]), from where the
 * Door goes in and `::raidparty` makes a party first. He starts it as `::scenario` does ([startScenario]), for any
 * player, admin or not: `::scenario` itself stays an admin's, so he offers only these three. The player as they were is
 * kept the first time, kept in the save, and put back by his fourth option (or an admin's `::reset`). Like any
 * scenario, it replaces the bank and the skills it doesn't name until then. What a scenario hands out can be traded or
 * dropped to someone outside it, as with `::scenario`: ours, accepted on a private test server.
 *
 * He is the cache's Leagues Tutor (`league_tutor_normal`, 316), which no live world shows outside a league, so he can't
 * stand in for anyone live; his Trade and Claim-trophies do nothing. He stands in Lumbridge's courtyard by the spawn,
 * and in the raid's lobby to kit out again after a wipe (npc-spawns/raid-tester.json). Ours: every line, the tiles, the
 * type and the loadouts.
 */
object RaidTester {
    const val NPC = 316
    const val TALK_TO = 1

    /** The scenarios his menu's first three options start, in order. */
    val LOADOUTS = listOf("tfa-melee", "tfa-ranged", "tfa-magic")

    /** What the player picked: a loadout's scenario name, [PUT_BACK_PICKED], or null for nothing. */
    internal suspend fun Dialogue.talk(): String? = with(RaidTesterLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (val picked = choice(MELEE, RANGED, MAGIC, PUT_BACK, NOTHING)) {
            in 1..LOADOUTS.size -> LOADOUTS[picked - 1]
            LOADOUTS.size + 1 -> PUT_BACK_PICKED
            else -> null
        }
    }

    internal const val PUT_BACK_PICKED = "put back"
}

fun registerRaidTester(world: World) {
    world.handlers.onNpc(RaidTester.NPC, RaidTester.TALK_TO) { op ->
        var picked: String? = null
        world.dialogues.start(this, op.npc) { picked = with(RaidTester) { talk() } }
        val name = picked ?: return@onNpc
        // After the dialogue has closed, as the rotten potato's Slice runs its commands. The reply is the command's:
        // "Started scenario ...", "Back to how you were ...", or why it can't.
        val reply = try {
            if (name == RaidTester.PUT_BACK_PICKED) {
                resetScenario(world, player)
            } else {
                val scenario = world.scenarios[name] ?: throw CommandFailure("No scenario '$name'.")
                startScenario(world, player, scenario)
            }
        } catch (e: CommandFailure) {
            e.message.orEmpty()
        }
        logger.info { "${player.username} asked the raid tester for $name: $reply" }
        player.message(reply)
    }
}
