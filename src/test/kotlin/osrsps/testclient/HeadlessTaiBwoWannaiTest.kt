package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.content.HardwoodGrove
import osrsps.game.content.TaiBwoCleanup
import osrsps.game.content.TaiBwoWannai
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.2 through the headless client: Murcaily shown at login with trading sticks, the jungle refusing a
 * player who hasn't met him, his greeting, a section of light jungle hacked for a thatch spar and favour, a rotten fence
 * repaired with it, his first trading sticks for the favour (and his cool-down after), and the Hardwood Grove's doors
 * taking 100 sticks in and letting the player out for free. test-runner/examples/tai-bwo-wannai.json does the same on
 * the real map.
 */
class HeadlessTaiBwoWannaiTest {
    private val murcailyMulti = NpcType(
        MURCAILY_MULTI, multiVarbit = MURCAILY_VARBIT,
        multiNpcs = intArrayOf(-1, MURCAILY, MURCAILY_NO_STICKS, MURCAILY_NO_STICKS, MURCAILY_NO_STICKS, MURCAILY_NO_STICKS, MURCAILY_NO_STICKS, MURCAILY_NO_STICKS, -1),
        internalName = "tbwcu_murcaily_multinpc",
    )
    private val murcaily = NpcType(MURCAILY, "Murcaily", ops = listOf("Talk-to", null, null, null, null), internalName = "tbwcu_murcaily")
    private val murcailyNoSticks = NpcType(MURCAILY_NO_STICKS, "Murcaily", ops = listOf("Talk-to", null, null, null, null), internalName = "tbwcu_murcaily_nosticks")
    private val safta = TaiBwoWannai.Villager.SAFTA_DOC
    private val saftaMulti = NpcType(
        safta.multi, multiVarbit = safta.varbit,
        multiNpcs = intArrayOf(-1, safta.withSticks, safta.withoutSticks, safta.withoutSticks, safta.withoutSticks, safta.withoutSticks, safta.withoutSticks, safta.withoutSticks, -1),
        internalName = "tbwcu_safta_doc_multinpc",
    )
    private val saftaWith = NpcType(safta.withSticks, "Safta Doc", ops = listOf("Talk-to", null, null, null, null), internalName = "tbwcu_safta_doc")
    private val saftaWithout = NpcType(safta.withoutSticks, "Safta Doc", ops = listOf("Talk-to", null, null, null, null), internalName = "tbwcu_safta_doc_nosticks")

    /** As the 242 map has them (the jungle and fence south-west of the village, the grove's south door). */
    private val locs = listOf(
        ZoneLoc(LIGHT_JUNGLE, 10, 0, 2770, 3061, 0),
        ZoneLoc(TaiBwoCleanup.FENCE_ROTTEN, 9, 3, 2772, 3064, 0),
        ZoneLoc(GROVE_DOOR, 0, 0, HardwoodGrove.INSIDE_X, 3083, 0),
    )

    private fun type(id: Int, op: Int, option: String) =
        LocType(id, "Loc", List(5) { if (it == op - 1) option else null }, LocCollisionType(width = 1, length = 1, interactType = 0))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(2770 shr 6, 3061 shr 6); markLoaded(2770 shr 6, 3083 shr 6); markLoaded(2817 shr 6, 3083 shr 6) },
            objTypes = ObjTypes.of(
                ObjType(MACHETE, "Machete"), ObjType(SPAR_LIGHT, "Thatch spar light"), ObjType(SPAR_MED, "Thatch spar med"),
                ObjType(SPAR_DENSE, "Thatch spar dense"), ObjType(TaiBwoWannai.TRADING_STICKS, "Trading sticks", stackable = true),
                ObjType(TaiBwoWannai.GOUT_TUBER, "Gout tuber"), ObjType(OPAL, "Opal"), ObjType(UNCUT_OPAL, "Uncut opal"),
                ObjType(TaiBwoWannai.GemMachete.OPAL.machete, "Opal machete"),
            ),
            npcTypes = NpcTypes.of(murcailyMulti, murcaily, murcailyNoSticks, saftaMulti, saftaWith, saftaWithout),
            npcSpawns = listOf(NpcSpawn(murcailyMulti, 2816, 3083, wander = 0, source = "test"), NpcSpawn(saftaMulti, 2805, 3083, wander = 0, source = "test")),
            locTypes = LocTypes(
                (LIGHT_JUNGLE..LIGHT_JUNGLE + 4).associateWith { type(it, TaiBwoCleanup.HACK, "Hack") } +
                    TaiBwoCleanup.FENCES.associateWith { type(it, TaiBwoCleanup.REPAIR, "Repair") } +
                    mapOf(GROVE_DOOR to type(GROVE_DOOR, HardwoodGrove.OPEN, "Open")),
            ),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.box(text: String) =
        awaitDialogue(text) { it is DialogueView.Message && it.text == text }.also { continueDialogue() }

    private fun HeadlessClient.menu(option: Int): DialogueView.Menu {
        val menu = awaitDialogue("a menu") { it is DialogueView.Menu } as DialogueView.Menu
        chooseOption(option)
        return menu
    }

    private fun HeadlessClient.closed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    private fun HeadlessClient.favour() = varp(FAVOUR_VARP) shr 2 and 1023

    private fun HeadlessClient.murcailyState() = varp(CHAT_VARP) shr 24 and 7

    private fun HeadlessClient.saftaState() = varp(CHAT_VARP) shr 12 and 7

    private fun HeadlessClient.murcailyIndex() = await("Murcaily in view") { npcs().firstOrNull { it.id == MURCAILY_MULTI } }.index

    @Test
    fun `Murcaily's greeting lets the player hack jungle and mend the fence, and his first sticks pay for the favour`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.ready()
                client.await("every villager shown with sticks") { if (client.varp(CHAT_VARP) == ALL_WITH_STICKS) Unit else null }
                client.command("setlevel woodcutting 99")
                client.command("item $MACHETE 1")
                client.await("the machete") { if (client.count(MACHETE) == 1L) Unit else null }
                client.tele(2770, 3060)
                client.locOp(LIGHT_JUNGLE, 2770, 3061, TaiBwoCleanup.HACK)
                client.message("[tbw_cleanup.not_met]")

                client.tele(2815, 3083)
                client.npcOp(client.murcailyIndex(), TaiBwoWannai.TALK_TO)
                client.chat("tbw_villager.greeting")
                assertEquals(3, client.menu(3).options.size, "no work option before a fence is mended")
                client.chat("tbw_villager.ok_thanks")
                client.closed()

                client.tele(2770, 3060)
                client.locOp(LIGHT_JUNGLE, 2770, 3061, TaiBwoCleanup.HACK)
                client.await("a light thatch spar") { if (client.count(SPAR_LIGHT) == 1L) Unit else null }
                client.await("0.3% favour") { if (client.favour() == 3) Unit else null }

                client.tele(2773, 3064)
                client.locOp(TaiBwoCleanup.FENCE_ROTTEN, 2772, 3064, TaiBwoCleanup.REPAIR)
                client.await("the spar used") { if (client.count(SPAR_LIGHT) == 0L) Unit else null }
                client.await("0.4% favour") { if (client.favour() == 4) Unit else null }

                // A full backpack: nothing taken, the favour kept, and he pays next time.
                client.command("item $MACHETE 27")
                client.await("a full backpack") { if (client.count(MACHETE) == 28L) Unit else null }
                client.tele(2815, 3083)
                client.npcOp(client.murcailyIndex(), TaiBwoWannai.TALK_TO)
                client.chat("murcaily.repaired")
                client.box("[game.no_space]")
                client.closed()
                assertEquals(0L, client.count(TaiBwoWannai.TRADING_STICKS))
                assertEquals(4, client.favour())
                client.command("empty")
                client.await("room again") { if (client.count(MACHETE) == 0L) Unit else null }

                client.npcOp(client.murcailyIndex(), TaiBwoWannai.TALK_TO)
                client.chat("murcaily.repaired")
                client.box("[tbw_villager.got_sticks sticks=4 favour=0]")
                client.closed()
                assertEquals(4L, client.count(TaiBwoWannai.TRADING_STICKS), "a stick for each tenth of a percent")
                assertEquals(0, client.favour())
                assertEquals(TaiBwoWannai.COOLDOWN_START, client.murcailyState())

                client.locOp(GROVE_DOOR, HardwoodGrove.INSIDE_X, 3083, HardwoodGrove.OPEN)
                client.message("[hardwood_grove.no_sticks]")
                client.command("item ${TaiBwoWannai.TRADING_STICKS} 100")
                client.await("104 sticks") { if (client.count(TaiBwoWannai.TRADING_STICKS) == 104L) Unit else null }
                client.locOp(GROVE_DOOR, HardwoodGrove.INSIDE_X, 3083, HardwoodGrove.OPEN)
                client.await("in the grove") { client.position?.takeIf { it == Coord(HardwoodGrove.INSIDE_X, 3083, 0) } }
                assertEquals(4L, client.count(TaiBwoWannai.TRADING_STICKS), "the 100-stick fee")
                client.locOp(GROVE_DOOR, HardwoodGrove.INSIDE_X, 3083, HardwoodGrove.OPEN)
                client.await("out for free") { client.position?.takeIf { it == Coord(HardwoodGrove.INSIDE_X - 1, 3083, 0) } }
                assertEquals(4L, client.count(TaiBwoWannai.TRADING_STICKS))

                // A spar into the last free slot ends the hack without cutting the success animation short.
                client.command("empty")
                client.command("item $MACHETE 27")
                client.await("one slot left") { if (client.count(MACHETE) == 27L && client.count(TaiBwoWannai.TRADING_STICKS) == 0L) Unit else null }
                client.tele(2770, 3060)
                val from = client.actorEvents().size
                client.locOp(LIGHT_JUNGLE + 1, 2770, 3061, TaiBwoCleanup.HACK)
                client.message("[tbw_cleanup.inventory_full]")
                assertEquals(1L, client.count(SPAR_LIGHT))
                client.awaitTicks(4)
                val anims = client.actorEvents(from).filter { !it.npc && it.index == client.index && it.kind == ActorEventKind.ANIMATION }
                assertEquals(TaiBwoCleanup.HACK_SUCCESS, anims.last().id, "the success animation isn't stopped")
            }
        }
    }

    private fun HeadlessClient.objbox() {
        awaitDialogue("the object box") { it == DialogueView.Other(Chatbox.OBJECTBOX) }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.saftaIndex() = await("Safta Doc in view") { npcs().firstOrNull { it.id == safta.multi } }.index

    /** Safta Doc's What do you do here? through to the Opal machete's order. */
    private fun HeadlessClient.orderOpal() {
        npcOp(saftaIndex(), TaiBwoWannai.TALK_TO)
        menu(1)
        chat("tbw_villager.what")
        chat("safta_doc.what")
        chat("safta_doc.deal")
        menu(1)
        chat("safta_doc.yes_machete")
        chat("safta_doc.three_types")
        menu(1)
        objbox()
        menu(1)
        chat("safta_doc.need_opal")
    }

    @Test
    fun `Safta Doc makes an opal machete for three opals, a gout tuber and 300 sticks, cut gems first, and turns away a player short of them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bea").use { client ->
                client.ready()
                client.command("item ${TaiBwoWannai.TRADING_STICKS} 299")
                client.command("item ${TaiBwoWannai.GOUT_TUBER} 1")
                client.command("item $OPAL 2")
                client.command("item $UNCUT_OPAL 2")
                client.await("the goods") { if (client.count(UNCUT_OPAL) == 2L && client.count(TaiBwoWannai.TRADING_STICKS) == 299L) Unit else null }
                client.tele(2806, 3083)

                client.orderOpal()
                client.chat("safta_doc.not_enough")
                client.chat("safta_doc.come_back")
                client.closed()
                assertEquals(0L, client.count(TaiBwoWannai.GemMachete.OPAL.machete))
                assertEquals(299L, client.count(TaiBwoWannai.TRADING_STICKS))

                client.command("item ${TaiBwoWannai.TRADING_STICKS} 1")
                client.await("300 sticks") { if (client.count(TaiBwoWannai.TRADING_STICKS) == 300L) Unit else null }
                client.orderOpal()
                client.chat("safta_doc.there_you_go_opal")
                client.closed()
                assertEquals(1L, client.count(TaiBwoWannai.GemMachete.OPAL.machete))
                assertEquals(0L, client.count(TaiBwoWannai.TRADING_STICKS))
                assertEquals(0L, client.count(TaiBwoWannai.GOUT_TUBER))
                assertEquals(0L, client.count(OPAL), "both cut opals went first")
                assertEquals(1L, client.count(UNCUT_OPAL), "then one uncut")
            }
        }
    }

    @Test
    fun `a villager who gave sticks comes back one step every 300 ticks, and has sticks again at the end`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cy").use { client ->
                client.ready()
                client.await("every villager shown with sticks") { if (client.varp(CHAT_VARP) == ALL_WITH_STICKS) Unit else null }
                client.command("varbit $MURCAILY_VARBIT 2")
                client.command("varbit ${safta.varbit} ${TaiBwoWannai.COOLDOWN_START}")
                client.await("both on cool-down") { if (client.murcailyState() == 2 && client.saftaState() == TaiBwoWannai.COOLDOWN_START) Unit else null }
                client.await("Murcaily's sticks back", TaiBwoWannai.COOLDOWN_TICKS * 30L + 10_000L) { if (client.murcailyState() == TaiBwoWannai.HAS_STICKS) Unit else null }
                assertEquals(TaiBwoWannai.COOLDOWN_START - 1, client.saftaState(), "one step for every villager on the same tick")
            }
        }
    }

    private companion object {
        const val LIGHT_JUNGLE = 9010
        const val GROVE_DOOR = 9038
        const val MACHETE = 975
        const val OPAL = 1609
        const val UNCUT_OPAL = 1625
        const val SPAR_LIGHT = 6281
        const val SPAR_MED = 6283
        const val SPAR_DENSE = 6285
        const val MURCAILY_MULTI = 6577
        const val MURCAILY = 6430
        const val MURCAILY_NO_STICKS = 6431
        const val MURCAILY_VARBIT = 900
        const val CHAT_VARP = 534
        const val FAVOUR_VARP = 535

        /** Every villager's three bits at 1. */
        val ALL_WITH_STICKS = (0 until 10).sumOf { 1 shl (it * 3) }

        /** The cache's chat_* varbits (892 to 901, three bits each of varp 534) and favour_percentage (907, bits 2 to 11 of 535). */
        val VARS = VarTypes(
            6_000,
            TaiBwoWannai.Villager.entries.withIndex().associate { (i, v) -> v.varbit to VarbitType(CHAT_VARP, i * 3, i * 3 + 2) } +
                (TaiBwoWannai.FAVOUR_VARBIT to VarbitType(FAVOUR_VARP, 2, 11)),
        )
    }
}
