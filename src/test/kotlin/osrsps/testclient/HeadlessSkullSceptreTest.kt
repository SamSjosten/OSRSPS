package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.Church
import osrsps.game.content.SkullSceptre
import osrsps.game.content.StrongholdOfSecurity
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Edgeville piece step 14b through the headless client: the four pieces joined into the skull sceptre, Divine, Invoke to
 * the Stronghold's entrance for a charge and the sceptre crumbling with its last, Solztun's imbue once he has seen the
 * Cradle's boots, and the imbued sceptre charged with bone fragments and a piece, a chisel breaking a piece. The
 * rl-client script test-runner/examples/skull-sceptre.json does the same in the real client.
 */
class HeadlessSkullSceptreTest {
    private val solztun = NpcType(SkullSceptre.SOLZTUN, "Solztun", ops = listOf("Talk-to", null, null, null, null), internalName = "sos_barb_spirit")

    private fun item(id: Int, name: String, iops: List<String?> = listOf(null, null, null, null, "Drop"), stackable: Boolean = false) =
        ObjType(id, name, iops = iops, stackable = stackable, wearpos1 = if (id == SkullSceptre.SCEPTRE || id == SkullSceptre.IMBUED) WearPos.WEAPON else -1)

    private val sceptreOps = listOf(null, "Wield", "Invoke", "Divine", "Drop")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(SkullSceptre.BONE_FRAGMENTS + 1)
        for (type in listOf(
            item(SkullSceptre.RIGHT_SKULL_HALF, "Right skull half"), item(SkullSceptre.LEFT_SKULL_HALF, "Left skull half"),
            item(SkullSceptre.STRANGE_SKULL, "Strange skull"), item(SkullSceptre.TOP_OF_SCEPTRE, "Top of sceptre"),
            item(SkullSceptre.BOTTOM_OF_SCEPTRE, "Bottom of sceptre"), item(SkullSceptre.RUNED_SCEPTRE, "Runed sceptre"),
            item(SkullSceptre.SCEPTRE, "Skull sceptre", sceptreOps), item(SkullSceptre.IMBUED, "Skull sceptre (i)", sceptreOps),
            item(SkullSceptre.BONE_FRAGMENTS, "Bone fragments", stackable = true), item(SkullSceptre.CHISEL, "Chisel"),
            item(StrongholdOfSecurity.FANCY_BOOTS, "Fancy boots", listOf(null, "Wear", null, null, "Drop")),
        )) objs[type.id] = type
        World(
            crcs, CollisionMap().apply { markLoaded(2338 shr 6, 5213 shr 6); markLoaded(3081 shr 6, 3421 shr 6); markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(3),
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(solztun),
            npcSpawns = listOf(NpcSpawn(solztun, 2338, 5213, source = "test")),
            varTypes = VarTypes(
                6_000,
                mapOf(
                    SkullSceptre.VARBIT_IMBUED to VarbitType(VARP_SCEPTRE, 0, 0),
                    SkullSceptre.VARBIT_BROTHER_FOUND to VarbitType(VARP_SCEPTRE, 1, 1),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slot(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.useOn(id: Int, target: Int) =
        ifButtonT(INVENTORY, INVENTORY_ITEMS, slot(id), id, INVENTORY, INVENTORY_ITEMS, slot(target), target)

    private fun HeadlessClient.itemOp(id: Int, iop: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot(id), id)

    private fun HeadlessClient.carried(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.npcSays(key: String) = awaitDialogue("Solztun: $key") { it is DialogueView.Chat && it.text == "[solztun.$key]" && it.name == "Solztun" }.also { continueDialogue() }

    private fun HeadlessClient.playerSays(key: String) = awaitDialogue("the player: $key") { it is DialogueView.Chat && it.text.startsWith("[solztun.$key") && it.name != "Solztun" }.also { continueDialogue() }

    private fun HeadlessClient.pick(vararg keys: String, option: Int) {
        val texts = keys.map { "[solztun.$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    @Test
    fun `the pieces join into the skull sceptre, whose Invoke takes the player to the Stronghold for a charge until it crumbles`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (id in listOf(SkullSceptre.LEFT_SKULL_HALF, SkullSceptre.RIGHT_SKULL_HALF, SkullSceptre.TOP_OF_SCEPTRE, SkullSceptre.BOTTOM_OF_SCEPTRE)) {
                    client.command("item $id")
                }
                client.await("the four pieces") { client.carried(SkullSceptre.BOTTOM_OF_SCEPTRE).takeIf { it == 1 } }
                client.useOn(SkullSceptre.LEFT_SKULL_HALF, SkullSceptre.RIGHT_SKULL_HALF)
                client.awaitMessage("the strange skull") { it == "[skull_sceptre.join_skull]" }
                client.useOn(SkullSceptre.TOP_OF_SCEPTRE, SkullSceptre.BOTTOM_OF_SCEPTRE)
                client.awaitMessage("the runed sceptre") { it == "[skull_sceptre.join_sceptre]" }
                client.useOn(SkullSceptre.RUNED_SCEPTRE, SkullSceptre.STRANGE_SKULL)
                client.awaitMessage("the skull sceptre") { it == "[skull_sceptre.join_whole]" }
                client.await("only the sceptre left") { Unit.takeIf { client.inventory(BACKPACK).filterNotNull().map { it.id } == listOf(SkullSceptre.SCEPTRE) } }
                client.itemOp(SkullSceptre.SCEPTRE, SkullSceptre.DIVINE)
                client.awaitMessage("ten charges") { it == "[skull_sceptre.divine charges=10]" }

                repeat(SkullSceptre.MAX_CHARGES) { used ->
                    client.tele(3222, 3218 + used % 2)
                    client.itemOp(SkullSceptre.SCEPTRE, SkullSceptre.INVOKE)
                    val landed = client.await("at the Stronghold after ${used + 1}") { client.position?.takeIf { it == Coord(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0) } }
                    assertEquals(Coord(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0), landed)
                }
                client.await("the sceptre crumbled") { client.messages().firstOrNull { it.text == "[skull_sceptre.crumbles]" } }
                client.await("the sceptre gone") { Unit.takeIf { client.carried(SkullSceptre.SCEPTRE) == 0 } }
            }
        }
    }

    @Test
    fun `Solztun imbues the sceptre once he sees the boots, and the imbued one takes fragments and pieces`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2339, 5213)
                val npc = client.await("Solztun in view") { client.npcs().firstOrNull { it.id == SkullSceptre.SOLZTUN } }
                // His story, the first time; Bye ends it.
                client.npcOp(npc.index, 1)
                client.npcSays("can_you_hear")
                client.playerSays("hear_no_amulet")
                client.npcSays("spirit")
                client.playerSays("strange")
                client.npcSays("dont_dwell")
                client.pick("who", "why", "bye", option = 1)
                client.playerSays("who")
                client.npcSays("who_intro")
                client.playerSays("who_name")
                for (key in listOf("who_knows", "who_exploring", "who_sister", "who_litara", "who_died", "who_help", "who_assumed", "who_lifestyle", "who_explore", "who_agree")) {
                    if (key in setOf("who_exploring", "who_litara", "who_help", "who_lifestyle", "who_agree")) client.playerSays(key) else client.npcSays(key)
                }
                assertEquals(2, client.await("his story heard") { client.varp(VARP_SCEPTRE).takeIf { it and 2 != 0 } })
                client.pick("who", "why", "bye", option = 3)
                client.playerSays("bye")
                client.npcSays("farewell")

                // The sceptre and the boots: the imbue.
                client.command("item ${SkullSceptre.SCEPTRE}")
                client.command("item ${StrongholdOfSecurity.FANCY_BOOTS}")
                client.await("the boots") { client.carried(StrongholdOfSecurity.FANCY_BOOTS).takeIf { it == 1 } }
                client.npcOp(npc.index, 1)
                for (key in listOf("can_you_hear", "hear_no_amulet", "spirit", "strange", "dont_dwell")) {
                    if (key.startsWith("hear") || key == "strange") client.playerSays(key) else client.npcSays(key)
                }
                client.pick("imbue", "who", "why", "bye", option = 1)
                client.playerSays("imbue")
                client.npcSays("imbue_need")
                client.pick("safe", "treasure", option = 1)
                client.playerSays("safe")
                client.npcSays("safe_yes")
                client.pick("safe", "treasure", option = 2)
                client.playerSays("treasure")
                client.awaitDialogue("the boots shown") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.npcSays("delighted")
                client.playerSays("imbue_again")
                client.npcSays("imbue_done")
                client.await("the imbued sceptre") { client.carried(SkullSceptre.IMBUED).takeIf { it == 1 } }
                assertEquals(0, client.carried(SkullSceptre.SCEPTRE))
                for (key in listOf("wont_break", "how_many", "charges", "chisel", "warn", "keep_safe", "farewell")) {
                    if (key == "how_many" || key == "keep_safe") client.playerSays(key) else client.npcSays(key)
                }
                assertTrue(client.varp(VARP_SCEPTRE) and 1 != 0, "sos_sceptre_imbued set")

                // Charges: the account had none left; fragments one each, a piece its 5, never past 10.
                client.command("item ${SkullSceptre.BONE_FRAGMENTS} 4")
                client.useOn(SkullSceptre.BONE_FRAGMENTS, SkullSceptre.IMBUED)
                client.awaitMessage("4 charges") { it == "[skull_sceptre.charged charges=4]" }
                client.command("item ${SkullSceptre.LEFT_SKULL_HALF}")
                client.useOn(SkullSceptre.LEFT_SKULL_HALF, SkullSceptre.IMBUED)
                client.awaitMessage("9 charges") { it == "[skull_sceptre.charged charges=9]" }
                client.command("item ${SkullSceptre.RIGHT_SKULL_HALF}")
                client.useOn(SkullSceptre.RIGHT_SKULL_HALF, SkullSceptre.IMBUED)
                client.awaitMessage("no room for a piece's 3") { it == "[skull_sceptre.full]" }
                client.command("item ${SkullSceptre.CHISEL}")
                client.useOn(SkullSceptre.CHISEL, SkullSceptre.RIGHT_SKULL_HALF)
                client.awaitMessage("3 fragments") { it == "[skull_sceptre.chiselled fragments=3]" }
                client.await("the fragments") { client.carried(SkullSceptre.BONE_FRAGMENTS).takeIf { it == 3 } }
                client.useOn(SkullSceptre.BONE_FRAGMENTS, SkullSceptre.IMBUED)
                client.awaitMessage("10 charges") { it == "[skull_sceptre.charged charges=10]" }
                client.await("two fragments kept") { client.carried(SkullSceptre.BONE_FRAGMENTS).takeIf { it == 2 } }

                // An imbued sceptre stays when its charges run out.
                repeat(SkullSceptre.MAX_CHARGES) { used ->
                    client.tele(2339, 5213 + used % 2)
                    client.itemOp(SkullSceptre.IMBUED, SkullSceptre.INVOKE)
                    val landed = client.await("at the Stronghold after ${used + 1}") { client.position?.takeIf { it == Coord(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0) } }
                    assertEquals(Coord(Church.STRONGHOLD.x, Church.STRONGHOLD.z, 0), landed)
                }
                client.itemOp(SkullSceptre.IMBUED, SkullSceptre.INVOKE)
                client.awaitMessage("no charges") { it == "[skull_sceptre.no_charges]" }
                assertEquals(1, client.carried(SkullSceptre.IMBUED))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0

        /** A spare varp for the test's two varbits. */
        const val VARP_SCEPTRE = 4_000
    }
}
