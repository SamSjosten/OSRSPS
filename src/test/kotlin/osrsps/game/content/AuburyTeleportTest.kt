package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.GameRandom
import osrsps.game.Interfaces
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

/**
 * Aubury's shout as he sends a player to the essence mine is his own line, not Sedridor's (live's capitals differ). Read
 * from his NPC effects, as the headless client's reader skips overhead text sent with a spotanim.
 */
class AuburyTeleportTest {
    @Test
    fun `Aubury's Teleport shouts his own incantation`() {
        val world = World(IntArray(0), CollisionMap())
        val scripts = ScriptRunner(Handlers(), Interfaces { _, _ -> }::closeModals) { 0 }
        val player = Player(1, "Zezima", 3253, 3401, 0)
        val type = NpcType(VarrockStores.AUBURY[1], "Aubury", internalName = "aubury_3op")
        val aubury = Npcs(CollisionMap(), GameRandom(1)).add(NpcSpawn(type, 3253, 3403, source = "test"))!!
        val op = Op(OpTarget.NPC, type.id, VarrockStores.TELEPORT_OP, npc = aubury)
        val handler = world.handlers.find(op)!!
        scripts.start(player) { handler(op) }
        assertEquals(DialogueText.NONE[AuburyLines.INCANTATION], aubury.effects.say)
    }
}
