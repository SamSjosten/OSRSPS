package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.NpcRangedAttack
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.Npcs
import osrsps.game.script.ScriptContext
import osrsps.map.LineOfSight

/** Professor Oddenstein's warning at the rift (the Wiki's Transcript:Professor Oddenstein, read 2026-11-19). */
object KillerwattPlaneLines : Lines("killerwatt_plane", "Transcript:Professor Oddenstein") {
    val ERR = line("err", "Oddenstein, as a player without insulated boots enters the rift")
    val PROBLEM = line("problem", "the player asks what the problem is")
    val SHOCKING = line("shocking", "Oddenstein's warning, before the menu")
    val WHERE_ARMOUR = line("where_armour", "option 1, which the player then says")
    val SLAYER_MASTERS = line("slayer_masters", "where: his answer")
    val DO_THAT = line("do_that", "where, option 1, which the player then says")
    val NO_PROBLEM = line("no_problem", "do that: his answer")
    val GOING_THROUGH = line("going_through", "where, option 2, which the player then says")
    val FAIR_ENOUGH = line("fair_enough", "going through: his answer, then the player goes through")
    val STAY = line("stay", "option 2, which the player then says")
    val NOT_SCARED = line("not_scared", "option 3, which the player then says")
    val OK_WARNED = line("ok_warned", "not scared: his answer, then the player goes through")
}

/**
 * The Killerwatt plane (post-quest step 15; every account has finished Ernest the Chicken, which opens it). From the
 * Wiki's Killerwatt plane, Portal machine, Portal Home, Killerwatt, Storm Cloud and Professor Oddenstein's transcript,
 * read 2026-11-19, and the 242 cache's map, NPCs and names.
 *
 * - **In and out**: the interdimensional rift (11354, shown by varbit 1766 on the portal machine's multiloc 11355, set
 *   after the quests in [osrsps.game.Quests]) on Draynor Manor's top floor takes the player to the plane, and the
 *   Portal Home (11356) there back. Without insulated boots on, Oddenstein warns them first, and they go through or
 *   stay as they choose.
 * - **Killerwatts**: passive orbs (470) until one fights; then it grows limbs (469) for the fight. Attacked from beside
 *   it, it fights in melee and walks after the player; attacked from further off, it stays where it is and shoots its
 *   magical ranged attack from up to 13 tiles. Both attack every 2 ticks, for up to 6 in melee or 8 at range, or 15
 *   either way at a player without insulated boots (the Wiki's Killerwatt).
 * - **Storm clouds** (488) wander and do nothing (the spawn file).
 *
 * Guessed: the landing tiles either side; no animation going through; that a killerwatt picks melee or range by
 * whether the player is beside it when it starts fighting back; that it turns back into an orb once it stops
 * fighting; the transformation's spotanim; and the ranged attack rolling against Ranged defence.
 */
class KillerwattPlane(private val world: World) {
    /** The killerwatts fighting, and whether each fights in melee (true) or at range. */
    private val melee = HashMap<Npc, Boolean>()

    fun insulated(player: Player): Boolean = player.equipment.count(INSULATED_BOOTS) > 0

    suspend fun ScriptContext.enterRift() {
        arriveDelay()
        if (!insulated(player) && !warned()) return
        world.teleport(player, PLANE_LANDING.x, PLANE_LANDING.z, PLANE_LANDING.level)
    }

    /** Oddenstein's warning: whether the player goes through. Guessed: with no Oddenstein near, they just go. */
    private suspend fun ScriptContext.warned(): Boolean {
        val oddenstein = world.npcs.near(player.x, player.z, player.level, WARN_RADIUS).firstOrNull { it.type.id == DraynorManorPeople.ODDENSTEIN } ?: return true
        var goes = false
        world.dialogues.start(this, oddenstein) {
            with(KillerwattPlaneLines) {
                chatNpc(ChatAnim.NEUTRAL, ERR)
                chatPlayer(ChatAnim.QUIZ, PROBLEM)
                chatNpc(ChatAnim.NEUTRAL, SHOCKING)
                when (choice(WHERE_ARMOUR, STAY, NOT_SCARED)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, WHERE_ARMOUR)
                        chatNpc(ChatAnim.NEUTRAL, SLAYER_MASTERS)
                        if (choice(DO_THAT, GOING_THROUGH) == 1) {
                            chatPlayer(ChatAnim.HAPPY, DO_THAT)
                            chatNpc(ChatAnim.HAPPY, NO_PROBLEM)
                        } else {
                            chatPlayer(ChatAnim.NEUTRAL, GOING_THROUGH)
                            chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                            goes = true
                        }
                    }
                    2 -> chatPlayer(ChatAnim.NEUTRAL, STAY)
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, NOT_SCARED)
                        chatNpc(ChatAnim.NEUTRAL, OK_WARNED)
                        goes = true
                    }
                }
            }
        }
        return goes
    }

    suspend fun ScriptContext.portalHome() {
        arriveDelay()
        world.teleport(player, MANOR_LANDING.x, MANOR_LANDING.z, MANOR_LANDING.level)
    }

    /** Each tick, for each killerwatt: its fight, which this drives ([Npc.scriptedAttacks]). */
    internal fun tick(npc: Npc) {
        npc.scriptedAttacks = true
        val player = npc.target
        if (player == null) {
            if (melee.remove(npc) != null) world.npcTypes[ORB]?.let { world.npcs.transform(npc, it, 1) }
            return
        }
        val fighting = world.meleeCombat
        val inMelee = melee.getOrPut(npc) {
            world.npcTypes[BIPED]?.let { world.npcs.transform(npc, it, Npcs.FOREVER) }
            npc.effects.spotanim(0, TRANSFORM_SPOTANIM, 0)
            fighting.reaches(npc, player)
        }
        val tick = world.tick
        if (inMelee) {
            if (npc.following !== player) world.npcs.follow(npc, player)
            if (tick < npc.nextAttackTick || !fighting.reaches(npc, player)) return
            fighting.swingAt(npc, player, tick, maxHit = if (insulated(player)) null else UNINSULATED_MAX_HIT)
        } else {
            npc.face(player)
            val distance = LineOfSight.distance(npc.x, npc.z, npc.type.size, player.x, player.z)
            if (tick < npc.nextAttackTick || distance !in 1..RANGE || !fighting.sees(npc, player)) return
            val attack = NpcRangedAttack(anim = CAST_ANIM, maxHit = if (insulated(player)) RANGED_MAX_HIT else UNINSULATED_MAX_HIT)
            fighting.fireAt(npc, player, tick, attack, AttackType.RANGED)
        }
    }

    internal fun died(npc: Npc) {
        melee.remove(npc)
    }

    companion object {
        const val RIFT = 11355
        const val PORTAL_HOME = 11356
        const val ENTER = 1
        const val ORB = 470
        const val BIPED = 469
        const val STORM_CLOUD = 488
        const val INSULATED_BOOTS = 7159

        /** The Wiki's Killerwatt: its magical ranged attack's max hit and range, killerwatt_biped_cast its animation. */
        const val RANGED_MAX_HIT = 8
        const val RANGE = 13
        const val CAST_ANIM = 3164

        /** The Wiki's Killerwatt: 15 either way without insulated boots. */
        const val UNINSULATED_MAX_HIT = 15

        /** killerwatt_transform. Guessed: shown as it grows limbs. */
        const val TRANSFORM_SPOTANIM = 2651

        /** Guessed: how near Oddenstein has to be to warn the player. */
        const val WARN_RADIUS = 10

        /** Guessed tiles: beside the Portal Home in the plane, and before the rift on the manor's top floor. */
        val PLANE_LANDING = Destination(2676, 5214, 2)
        val MANOR_LANDING = Destination(3110, 3364, 2)
    }
}

fun registerKillerwattPlane(world: World, plane: KillerwattPlane) = with(plane) {
    world.handlers.onLoc(KillerwattPlane.RIFT, KillerwattPlane.ENTER) { enterRift() }
    world.handlers.onLoc(KillerwattPlane.PORTAL_HOME, KillerwattPlane.ENTER) { portalHome() }
    world.npcScripts.onTick(listOf(KillerwattPlane.ORB)) { tick(it) }
    world.npcScripts.onDeath(listOf(KillerwattPlane.ORB)) { died(it) }
}
