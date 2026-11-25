package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Prayers
import osrsps.game.content.ChampionsChallenge
import osrsps.game.content.ChampionsChallenge.Champion
import osrsps.game.content.ChampionsChestLines
import osrsps.game.content.LarxusLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 9 through the headless client: the giant champion's scroll used on Larxus arranges the duel,
 * the statue opens and goes down to the arena, the champion comes in for its player only, beating it sets its bit
 * and gives the XP (Larxus kept the scroll as it was arranged), the ladder goes back up, and the Chest of Champions gives the lamp that is rubbed
 * for ten times the XP. The rl-client script test-runner/examples/champions-challenge.json does the same on the real
 * map.
 */
class HeadlessChampionsChallengeTest {
    private val larxus = NpcType(ChampionsChallenge.LARXUS, "Larxus", ops = listOf("Talk-to", null, null, null, null), internalName = "champions_doorman")
    private val giant = NpcType(
        Champion.GIANT.npc, "Giant Champion", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 56,
        internalName = "champions_giant", stats = NpcStats(attack = 40, defence = 40, strength = 40, hitpoints = 70),
    )

    private fun champion(champion: Champion, name: String) = NpcType(
        champion.npc, name, ops = listOf(null, "Attack", null, null, null), combatLevel = 50,
        internalName = "champions_${champion.key}", stats = NpcStats(attack = 1, defence = 1, strength = 1, hitpoints = 99),
    )

    private val earthWarrior = champion(Champion.EARTH_WARRIOR, "Earth Warrior Champion")
    private val ghoul = champion(Champion.GHOUL, "Ghoul Champion")
    private val lesserDemon = champion(Champion.LESSER_DEMON, "Lesser Demon Champion")
    private val helm = ObjType(HELM, "Iron full helm", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HEAD)
    private val shield = ObjType(SHIELD, "Wooden shield", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.SHIELD)

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val statue = ZoneLoc(ChampionsChallenge.STATUE, LocShape.CENTREPIECE_STRAIGHT, 0, 3189, 9760, 0)
    private val ladder = ZoneLoc(ChampionsChallenge.ARENA_LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3183, 9760, 0)
    private val chest = ZoneLoc(ChampionsChallenge.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3186, 9762, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val npcTypes = NpcTypes.of(larxus, giant, earthWarrior, ghoul, lesserDemon, NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null)))
        World(
            crcs, CollisionMap().apply {
                markLoaded(3150 shr 6, 9740 shr 6)
                markLoaded(ChampionsChallenge.GRAVE.x shr 6, ChampionsChallenge.GRAVE.z shr 6)
            },
            random = GameRandom(5),
            varTypes = VarTypes(6_000, mapOf(CHEST_VARBIT to VarbitType(ChampionsChallenge.VARP_DEFEATED, 0, 15)), 21_000),
            objTypes = ObjTypes.of(
                ObjType(Champion.GIANT.scroll, "Giant champion scroll"),
                ObjType(ChampionsChallenge.LAMP, "Champion's lamp", iops = listOf("Rub", null, null, null, "Drop")),
                helm, shield,
            ),
            npcTypes = npcTypes,
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE),
            npcSpawns = listOf(NpcSpawn(larxus, 3187, 9758, source = "test")),
            locTypes = LocTypes(
                listOf(
                    type(ChampionsChallenge.STATUE, "Champion statue", "Open"),
                    type(ChampionsChallenge.STATUE_OPEN, "Champion statue", "Climb-down"),
                    type(ChampionsChallenge.ARENA_LADDER, "Ladder", "Climb-up"),
                    LocType(
                        ChampionsChallenge.CHEST, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = CHEST_VARBIT, multiLocs = listOf(CHEST_CLOSED, CHEST_OPEN),
                    ),
                    type(CHEST_CLOSED, "Chest", "Open"),
                    type(CHEST_OPEN, "Chest", "Search"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(statue, ladder, chest).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) = await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.chat(line: Line) {
        awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
        continueDialogue()
    }

    private fun HeadlessClient.objbox(closes: Boolean = true) {
        awaitDialogue("the object box") { it == DialogueView.Other(Chatbox.OBJECTBOX) }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
        if (closes) await("the object box closed") { if (dialogueView == null) Unit else null }
    }

    @Test
    fun `a scroll arranges the duel, the statue leads down to it, the win counts, and the chest's lamp gives ten times the XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                val challenge = server.world.championsChallenge
                client.command("tele 3188 9758 0")
                client.awaitAt(3188, 9758)

                // The statue won't open before a duel is arranged.
                client.locOp(statue.id, statue.x, statue.z, ChampionsChallenge.OPEN)
                client.chat(LarxusLines.ARRANGE_FIRST)

                client.commandReply("item ${Champion.GIANT.scroll} 1") { it.contains("Giant champion scroll") }
                client.npcTarget(client.npcIndex(larxus.id), InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Champion.GIANT.scroll), Champion.GIANT.scroll)
                client.chat(LarxusLines.RULES.getValue(Champion.GIANT))
                client.awaitDialogue("yes or no") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat(LarxusLines.READY)
                assertEquals(Champion.GIANT, challenge.arranged(alice))
                assertEquals(0, client.count(Champion.GIANT.scroll), "Larxus keeps the scroll as the duel is arranged")

                client.locOp(statue.id, statue.x, statue.z, ChampionsChallenge.OPEN)
                client.await("the statue open") { server.world.locs.find(ChampionsChallenge.STATUE_OPEN, statue.x, statue.z, 0) }
                client.locOp(ChampionsChallenge.STATUE_OPEN, statue.x, statue.z, ChampionsChallenge.CLIMB_DOWN)
                client.awaitAt(ChampionsChallenge.ARENA.x, ChampionsChallenge.ARENA.z)
                val champion = client.await("the champion in the arena") { challenge.fightOf(alice) }
                assertEquals(Champion.GIANT.npc, champion.type.id)

                val slayer = alice.skills.xp(Skill.SLAYER)
                client.commandReply("kill ${champion.index}") { it.startsWith("Killed") }
                client.await("the win") { if (challenge.defeated(alice, Champion.GIANT)) Unit else null }
                assertEquals(slayer + Champion.GIANT.xp, alice.skills.xp(Skill.SLAYER))
                assertNull(challenge.arranged(alice))
                assertNull(challenge.fightOf(alice))
                assertEquals(listOf(Champion.GIANT), challenge.lampsWaiting(alice))

                client.command("tele ${ladder.x + 1} ${ladder.z} 0")
                client.awaitAt(ladder.x + 1, ladder.z)
                client.locOp(ladder.id, ladder.x, ladder.z, ChampionsChallenge.CLIMB_UP)
                client.awaitAt(ChampionsChallenge.GALLERY.x, ChampionsChallenge.GALLERY.z)

                // The chest gives one lamp, and none while one is held.
                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.objbox()
                client.await("the lamp") { if (client.count(ChampionsChallenge.LAMP) == 1) Unit else null }
                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.objbox()
                assertEquals(1, client.count(ChampionsChallenge.LAMP))

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, RUB_BUTTON_OP, client.slotOf(ChampionsChallenge.LAMP), ChampionsChallenge.LAMP)
                client.objbox(closes = false)
                client.awaitDialogue("the lamp's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                // The level-up box follows the XP box, so it doesn't close.
                client.objbox(closes = false)
                client.await("the lamp used") { if (client.count(ChampionsChallenge.LAMP) == 0) Unit else null }
                assertEquals(slayer + Champion.GIANT.xp * 11, alice.skills.xp(Skill.SLAYER))
                assertTrue(challenge.lampClaimed(alice, Champion.GIANT))
                assertEquals(emptyList(), challenge.lampsWaiting(alice))

                client.locOp(chest.id, chest.x, chest.z, ChampionsChallenge.CHEST_OP)
                client.await("nothing left") { client.messages().firstOrNull { it.text == DialogueText.NONE[ChampionsChestLines.NOTHING] } }
                assertFalse(challenge.leonWaiting(alice))
            }
        }
    }

    /** [block] run on the game's tick, its result back here. */
    private fun <T> TestServer.onTick(block: () -> T): T {
        val result = CompletableFuture<T>()
        world.npcScripts.later(1) { result.complete(block()) }
        return result.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `a duel's rules hold in the arena - prayers go off, spells count as magic, gear put on is refused, and a death's grave is upstairs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val alice = world.playerNamed("alice")!!
                val challenge = world.championsChallenge
                client.command("tele ${ChampionsChallenge.ARENA.x} ${ChampionsChallenge.ARENA.z} 0")
                client.awaitAt(ChampionsChallenge.ARENA.x, ChampionsChallenge.ARENA.z)

                // Prayers on as the earth warrior's duel starts go off, this tick's effect too (Codex review on #948).
                val prayer = 1 shl 18
                val earth = server.onTick {
                    alice.vars[Prayers.VARP_PRAYERS] = prayer
                    alice.prayersInEffect = prayer
                    challenge.start(alice, Champion.EARTH_WARRIOR)!!
                }
                assertEquals(0 to 0, server.onTick { world.prayers.active(alice) to alice.prayersInEffect })
                // Nor does the quick-prayer orb turn them back on.
                server.onTick { world.prayers.toggleQuick(alice) }
                client.await("the orb refused") { client.messages().firstOrNull { it.text == DialogueText.NONE[LarxusLines.NO_PRAYER] } }
                assertEquals(0, server.onTick { world.prayers.active(alice) })
                server.onTick { world.removeNpc(earth) }

                // A spell is judged as magic whatever the style set: the giant's duel is melee only (Codex review on #948).
                val giantChampion = server.onTick { challenge.start(alice, Champion.GIANT)!! }
                assertNull(server.onTick { world.combat.barred(alice, giantChampion) }, "a melee style is allowed")
                assertEquals(LarxusLines.ONLY_MELEE, server.onTick { world.combat.barred(alice, giantChampion, type = AttackType.MAGIC) })

                // Gear put on in the arena is held to the duel's rule at every attack, not only at the statue.
                val demon = server.onTick { challenge.start(alice, Champion.LESSER_DEMON)!! }
                assertNull(server.onTick { world.combat.barred(alice, demon) }, "nothing worn")
                server.onTick {
                    alice.inventory.add(helm, 1)
                    world.equipment.wear(alice, alice.inventory.contents().first { (_, obj) -> obj.id == HELM }.first)
                }
                assertEquals(LarxusLines.NO_WEAPONS_ARMOUR, server.onTick { world.combat.barred(alice, demon) })
                server.onTick { alice.equipment.clear() }
                val ghoulChampion = server.onTick { challenge.start(alice, Champion.GHOUL)!! }
                assertNull(server.onTick { world.combat.barred(alice, ghoulChampion) }, "nothing carried")
                server.onTick { alice.inventory.add(shield, 1) }
                assertEquals(LarxusLines.WEAPONS_ONLY, server.onTick { world.combat.barred(alice, ghoulChampion) })

                // A death in the arena leaves the grave beside the guild's trapdoor upstairs (Codex review on #948).
                server.onTick { repeat(4) { alice.inventory.add(shield, 1) } }
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                val until = System.currentTimeMillis() + 15_000
                var grave = world.deaths.graveOf(alice)
                while (grave == null && System.currentTimeMillis() < until) {
                    Thread.sleep(20)
                    grave = world.deaths.graveOf(alice)
                }
                assertNotNull(grave, "the grave")
                assertEquals(Triple(ChampionsChallenge.GRAVE.x, ChampionsChallenge.GRAVE.z, 0), Triple(grave.npc.x, grave.npc.z, grave.npc.level))
            }
        }
    }

    private companion object {
        const val HELM = 1153
        const val SHIELD = 1171
        const val BACKPACK = 93
        const val CHEST_VARBIT = 6278
        const val CHEST_CLOSED = 22456
        const val CHEST_OPEN = 31944
        const val RUB_BUTTON_OP = 2
    }
}
