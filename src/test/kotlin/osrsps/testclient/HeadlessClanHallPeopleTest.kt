package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.ClanHall
import osrsps.game.content.ClanHallPeople
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clan hall's people through the headless client: a new hall has its bankers, Raqueel and Gem in the clan's colour,
 * Gardener Jay Jr. and Hannibal; Raqueel sells a beer for 5 coins, Gem's talk is cut off by Raqueel, Hannibal purrs
 * when petted, and a banker's Bank opens the bank. The rl-client script test-runner/examples/clan-hall-people.json
 * does the same on the real map.
 */
class HeadlessClanHallPeopleTest {
    private val types = (ClanHallPeople.BANKERS.map { NpcType(it, "Banker", ops = listOf("Talk-to", null, "Bank", "Collect", null)) } +
        (ClanHallPeople.RAQUEEL).map { NpcType(it, "Raqueel", ops = listOf("Talk-to", null, null, null, null)) } +
        (ClanHallPeople.GEM).map { NpcType(it, "Gem", ops = listOf("Talk-to", null, null, null, null)) } +
        NpcType(ClanHallPeople.GARDENER, "Gardener Jay Jr.", ops = listOf("Talk-to", null, null, null, null)) +
        NpcType(ClanHallPeople.HANNIBAL, "Hannibal", ops = listOf("Pet", null, "Feed", "Talk", null)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ClanHall.TEMPLATE_X shr 6, ClanHall.TEMPLATE_Z shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
            },
            objTypes = ObjTypes.of(ObjType(COINS, "Coins", stackable = true), ObjType(ClanHallPeople.BEER, "Beer")),
            npcTypes = NpcTypes.of(*types.toTypedArray()),
            locTypes = LocTypes(
                listOf(
                    LocType(ClanHall.PORTAL, "Clan hall portal", ops = listOf("Enter", "Enter-member", "Enter-guest", null, null), collision = LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(ClanHall.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0)) },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[clan_hall_people.$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.anyChat(prefix: String) {
        awaitDialogue(prefix) { it is DialogueView.Chat && it.text.startsWith("[clan_hall_people.$prefix") }
        continueDialogue()
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `a new hall has its people, and Raqueel sells beer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                alice.commandReply("item $COINS 10") { true }
                alice.command("tele ${PORTAL_X - 1} $PORTAL_Z 0")
                alice.await("beside the portal") { alice.position?.takeIf { it == Coord(PORTAL_X - 1, PORTAL_Z, 0) } }
                alice.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER)
                val at = alice.await("the hall") { alice.position?.takeIf { it.x >= 6400 } }
                val hall = server.world.instances.at(at.x, at.z)!!
                fun x(template: Int) = hall.baseX + template - ClanHall.TEMPLATE_X
                fun z(template: Int) = hall.baseZ + template - ClanHall.TEMPLATE_Z

                // Blue, the colour of a clan that never picked one.
                val raqueel = ClanHallPeople.RAQUEEL[2]
                val gem = ClanHallPeople.GEM[2]
                val inHall = server.world.npcs.all.filter { hall.contains(it.x, it.z) }.map { it.type.id }.toSet()
                assertEquals(ClanHallPeople.BANKERS.toSet() + setOf(raqueel, gem, ClanHallPeople.GARDENER, ClanHallPeople.HANNIBAL), inHall)

                // Raqueel: a beer for 5 coins.
                alice.command("tele ${x(1758)} ${z(5486)} 0")
                alice.await("by the bar") { alice.position?.takeIf { it == Coord(x(1758), z(5486), 0) } }
                val her = alice.await("Raqueel in view") { alice.npcs().firstOrNull { it.id == raqueel } }
                alice.npcOp(her.index, ClanHallPeople.TALK_TO)
                alice.chat("raqueel_hi_again")
                alice.anyChat("raqueel_greeting_")
                alice.awaitDialogue("the menu") { it is DialogueView.Menu }
                alice.chooseOption(1)
                alice.chat("raqueel_buy")
                alice.chat("raqueel_price")
                alice.awaitDialogue("buy or not") { it is DialogueView.Menu }
                alice.chooseOption(1)
                alice.await("the beer") { Unit.takeIf { alice.count(ClanHallPeople.BEER) == 1 && alice.count(COINS) == 5 } }

                // Gem, whom Raqueel calls away.
                val gemNpc = alice.await("Gem in view") { alice.npcs().firstOrNull { it.id == gem } }
                alice.npcOp(gemNpc.index, ClanHallPeople.TALK_TO)
                alice.chat("gem_who")
                alice.chat("gem_name")
                val missed = alice.awaitDialogue("Raqueel calls") { it is DialogueView.Chat && it.text == "[clan_hall_people.gem_missed]" }
                assertEquals("Raqueel", (missed as DialogueView.Chat).name)
                alice.continueDialogue()
                alice.chat("gem_go")

                // Hannibal purrs.
                alice.command("tele ${x(1748)} ${z(5465)} 0")
                alice.await("by Hannibal") { alice.position?.takeIf { it == Coord(x(1748), z(5465), 0) } }
                val cat = alice.await("Hannibal in view") { alice.npcs().firstOrNull { it.id == ClanHallPeople.HANNIBAL } }
                alice.npcOp(cat.index, ClanHallPeople.PET)
                alice.await("a purr") { alice.messages().firstOrNull { it.text.startsWith("[clan_hall_people.hannibal_pet_") && it.text.endsWith("b]") } }

                // A banker's Bank.
                alice.command("tele ${x(1745)} ${z(5479)} 0")
                alice.await("by the bankers") { alice.position?.takeIf { it == Coord(x(1745), z(5479), 0) } }
                val banker = alice.await("a banker in view") { alice.npcs().firstOrNull { it.id == ClanHallPeople.BANKERS[0] } }
                alice.npcOp(banker.index, osrsps.game.content.Bankers.BANK)
                alice.await("the bank") { if (BankScreen.MAIN in alice.openInterfaces()) Unit else null }
            }
        }
    }

    private companion object {
        const val PORTAL_X = 3186
        const val PORTAL_Z = 3471
        const val COINS = 995
    }
}
