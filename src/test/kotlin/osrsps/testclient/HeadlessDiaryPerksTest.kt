package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.content.DIARY_VARBITS
import osrsps.game.content.ZaffBarrel
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.magic.TeleportObjs
import osrsps.game.skill.magic.Teleports
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The Varrock Diary's perks through the headless client: Zaff's barrel refuses a player with no tier done, sells the easy
 * tier's 15 noted battlestaves for 105,000 once a day and then stays shut; Varrock Teleport's Grand Exchange refuses
 * without the medium tier and lands by the Exchange with it, and Configure flips the varbit that makes it the left
 * click.
 */
class HeadlessDiaryPerksTest {
    private val barrel = ZoneLoc(ZaffBarrel.FULL, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objTypes = TeleportObjs.objTypes(
            ObjType(ZaffBarrel.COINS, "Coins", stackable = true),
            ObjType(ZaffBarrel.NOTED_BATTLESTAFF, "Battlestaff", stackable = true),
        )
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3213 shr 6, 3424 shr 6)
                markLoaded(3165 shr 6, 3479 shr 6)
            },
            random = GameRandom(7), objTypes = objTypes,
            spells = Spells.load(objTypes, osrsps.game.SeqNames.NONE, osrsps.game.SpotanimNames.NONE),
            varTypes = VarTypes(6_000, DIARY_VARBITS + (Teleports.VARBIT_VARROCK_GE to VarbitType(3_000, 29, 29))),
            locTypes = LocTypes(
                listOf(
                    LocType(ZaffBarrel.FULL, "Barrel", ops = listOf("Claim-staves", null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(barrel) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int): Int = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.done(command: String) {
        val before = messages().size
        command(command)
        await("'$command' done") { Unit.takeIf { messages().drop(before).any { "task(s) done" in it.text } } }
    }

    @Test
    fun `Zaff's barrel sells the day's staves once, to a player with a Varrock tier done`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(ZaffBarrel.FULL, barrel.x, barrel.z, 1)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[zaff_barrel.not_for_you]" } }

                client.done("diary complete varrock easy all")
                client.command("item ${ZaffBarrel.COINS} 105000")
                client.await("the coins") { Unit.takeIf { client.count(ZaffBarrel.COINS) == 105_000 } }
                client.locOp(ZaffBarrel.FULL, barrel.x, barrel.z, 1)
                client.awaitDialogue("the price") { it is DialogueView.Message && it.text == "[zaff_barrel.buy count=15 total=105,000]" }
                client.continueDialogue()
                client.awaitDialogue("yes or no") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the staves") { Unit.takeIf { client.count(ZaffBarrel.NOTED_BATTLESTAFF) == 15 } }
                assertEquals(0, client.count(ZaffBarrel.COINS))

                // Bought today: the barrel does nothing more.
                client.command("item ${ZaffBarrel.COINS} 105000")
                client.await("more coins") { Unit.takeIf { client.count(ZaffBarrel.COINS) == 105_000 } }
                client.locOp(ZaffBarrel.FULL, barrel.x, barrel.z, 1)
                client.awaitTicks(3)
                assertEquals(null, client.dialogueView)
                assertEquals(15, client.count(ZaffBarrel.NOTED_BATTLESTAFF))
            }
        }
    }

    @Test
    fun `Zaff's barrel takes no coins when the day's staves wouldn't fit on the stack`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.done("diary complete varrock easy all")
                client.command("item ${ZaffBarrel.COINS} 105000")
                client.await("the coins") { Unit.takeIf { client.count(ZaffBarrel.COINS) == 105_000 } }
                // A stack 10 short of full: the day's 15 don't fit, though a slot holds the stack.
                val bob = server.world.playerNamed("bob")!!
                bob.inventory.add(server.world.objTypes[ZaffBarrel.NOTED_BATTLESTAFF]!!, Int.MAX_VALUE - 10)
                client.await("the stack") { Unit.takeIf { client.count(ZaffBarrel.NOTED_BATTLESTAFF) == Int.MAX_VALUE - 10 } }
                client.locOp(ZaffBarrel.FULL, barrel.x, barrel.z, 1)
                client.awaitDialogue("no space") { it is DialogueView.Message && it.text == "[zaff_barrel.need_space]" }
                client.continueDialogue()
                assertEquals(105_000, client.count(ZaffBarrel.COINS))
                assertEquals(Int.MAX_VALUE - 10, client.count(ZaffBarrel.NOTED_BATTLESTAFF))
            }
        }
    }

    @Test
    fun `Varrock Teleport's Grand Exchange needs the medium tier, and Configure flips the left click`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel magic 99") { it.contains("set to level 99") }
                with(TeleportObjs) {
                    client.command("item $LAW 2")
                    client.command("item $AIR 6")
                    client.command("item $FIRE 2")
                    client.await("the runes") { Unit.takeIf { client.count(FIRE) == 2 } }
                    client.ifButton(Spells.SPELLBOOK, VARROCK_COMPONENT, Teleports.GRAND_EXCHANGE)
                    client.await("the refusal") { client.messages().firstOrNull { it.text == "[teleport.ge_locked]" } }
                    assertEquals(2, client.count(LAW))

                    // The medium tier's tasks without the easy tier's: still locked.
                    client.done("diary complete varrock medium all")
                    client.ifButton(Spells.SPELLBOOK, VARROCK_COMPONENT, Teleports.GRAND_EXCHANGE)
                    client.await("the refusal again") { Unit.takeIf { client.messages().count { it.text == "[teleport.ge_locked]" } == 2 } }
                    assertEquals(2, client.count(LAW))
                    client.done("diary complete varrock easy all")
                    client.ifButton(Spells.SPELLBOOK, VARROCK_COMPONENT, Teleports.GRAND_EXCHANGE)
                    client.await("the Exchange") { client.position?.takeIf { it.x in 3163..3167 && it.z in 3477..3481 } }
                    assertEquals(1, client.count(LAW))

                    client.ifButton(Spells.SPELLBOOK, VARROCK_COMPONENT, Teleports.CONFIGURE)
                    client.await("the varbit set") { Unit.takeIf { client.varp(3_000) == 1 shl 29 } }
                    client.await("the message") { client.messages().firstOrNull { it.text == "[teleport.set_ge]" } }
                }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
