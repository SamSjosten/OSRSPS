package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/** Archmage Sedridor's lines once Rune Mysteries is done (the Wiki's Transcript:Archmage Sedridor). */
object SedridorLines : Lines("sedridor", "Transcript:Archmage Sedridor") {
    val HELLO = line("hello", "the player's opening line")
    val GREETING = line("greeting", "his greeting, naming the player", "name")
    val TELEPORT = line("teleport", "option 1, which the player then says; he teleports them to the mine")
    val WHO = line("who", "option 2, which the player then says")
    val WHO_ANSWER = line("who_answer", "option 2: his answer's first box, naming the first two others who know the teleport")
    val WHO_ANSWER_2 = line("who_answer_2", "option 2: his answer's second box, naming the other two")
    val WHO_THANKS = line("who_thanks", "option 2: the player's thanks")
    val WHO_PLEASURE = line("who_pleasure", "option 2: his last line")
    val OLD_TOWER = line("old_tower", "option 3, which the player then says")
    val OLD_TOWER_ANSWER = (1..11).map { line("old_tower_$it", "option 3: his box $it of 11; his options show again after the last") }
    val NOTHING = line("nothing", "option 4, which the player then says")
    val FAREWELL = line("farewell", "option 4: his farewell")
}

/** What a teleporting wizard shouts (the Wiki's Rune essence mine page). */
object EssenceMineLines : Lines("essence_mine", "Rune essence mine") {
    val INCANTATION = line("incantation", "the teleporting NPC's overhead shout as it sends the player to the mine")
}

/**
 * The rune essence mine and Archmage Sedridor, who sends players there from the Wizards' Tower's basement (Draynor piece
 * step 15a). Every account counts as having done Rune Mysteries.
 *
 * - Sedridor is spawned as `head_wizard_2op` 11433, the type his multi-NPC (`head_wizard` 5034, varp 63) shows after the
 *   quest: Talk-to and Teleport. His talk's four options are the Wiki transcript's; option 1 and Teleport send the player
 *   to the mine ([teleport]).
 * - The send, as 2004Scape's essence_mine.rs2 has it: the wizard shouts the incantation and gets `curse_casting` (108),
 *   `curse_travel` (109) flies to the player, who gets `curse_impact` (110); four ticks on the player lands on one of the
 *   mine's 22 spots, or a free tile beside it. No animation, as on live (2004Scape's note).
 * - The mine (map square 45_75): the four Rune Essence rocks are the map's ([osrsps.game.skill.Mining] mines them). Its
 *   four portals aren't on the 241 map, which live fills at each entry with one of seven look-alikes (five locs, two
 *   NPCs; the Wiki's Portal (Rune essence mine)); here `essencemine_portal_1` 34774 stands at 2004Scape's four tiles
 *   always, and every portal loc type's option sends the player back. They come out beside Sedridor (2004Scape's
 *   3106,9572), or beside whoever else sent them ([teleportToEssenceMine]'s returnTo, as Aubury does).
 * - Guessed: that the shout is overhead only (the transcript shows it as his line). The projectiles' heights and timing
 *   are 2004Scape's, not measured on live.
 *   Live's instances (a new one every fifth entry) are left out: everyone shares the one mine.
 */
object EssenceMine {
    const val SEDRIDOR = 11433
    const val TALK_TO = 1
    const val TELEPORT_OP = 3

    const val CURSE_CASTING = 108
    const val CURSE_TRAVEL = 109
    const val CURSE_IMPACT = 110
    const val SEND_TICKS = 4

    /** The portal loc types, every one of which leads out. */
    val PORTALS: List<Int> = (34774..34778).toList()
    const val PORTAL = 34774

    /** 2004Scape's four portals, beside the four rocks. */
    val PORTAL_LOCS = listOf(
        ZoneLoc(PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, 2885, 4850, 0),
        ZoneLoc(PORTAL, LocShape.CENTREPIECE_STRAIGHT, 3, 2889, 4813, 0),
        ZoneLoc(PORTAL, LocShape.CENTREPIECE_STRAIGHT, 3, 2932, 4854, 0),
        ZoneLoc(PORTAL, LocShape.CENTREPIECE_STRAIGHT, 3, 2933, 4815, 0),
    )

    /** Where the mine sends players back to Sedridor (2004Scape's essence_mine_to_sedridor). */
    val TO_SEDRIDOR = Tile(3106, 9572)

    /**
     * Where each player in the mine goes back to: beside whoever sent them ([teleportToEssenceMine]'s returnTo). Kept in
     * memory only, so a player who logs out in the mine comes back out beside Sedridor (2004Scape keeps it in a varp).
     */
    private val returns = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Player, Tile>())

    internal fun sentFrom(player: Player, tile: Tile) {
        returns[player] = tile
    }

    /** Where [player] leaves the mine for, forgetting it: beside whoever sent them, or Sedridor. */
    internal fun takeReturn(player: Player): Tile = returns.remove(player) ?: TO_SEDRIDOR

    /** 2004Scape's essence_mine_teleports: where a player sent to the mine lands. */
    val LANDINGS: List<Tile> = listOf(
        32 to 33, 35 to 31, 33 to 26, 30 to 24, 26 to 26, 28 to 35, 25 to 37, 32 to 38, 43 to 46, 51 to 42, 41 to 56,
        55 to 46, 14 to 39, 16 to 45, 19 to 58, 13 to 26, 19 to 21, 16 to 9, 41 to 20, 45 to 18, 41 to 11, 54 to 21,
    ).map { (x, z) -> Tile(MINE_X + x, MINE_Z + z) }

    private const val MINE_X = 2880
    private const val MINE_Z = 4800

    /**
     * [tile], or the first tile within [radius] of it that [canStand] allows, nearest rings first, in a random order
     * within each ring from [random]; [tile] itself when none is.
     */
    fun freeTileNear(tile: Tile, radius: Int, canStand: (Int, Int) -> Boolean, random: (Int) -> Int): Tile {
        if (canStand(tile.x, tile.z)) return tile
        for (r in 1..radius) {
            val ring = buildList {
                for (dx in -r..r) for (dz in -r..r) if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r) add(Tile(tile.x + dx, tile.z + dz))
            }.toMutableList()
            while (ring.isNotEmpty()) {
                val pick = ring.removeAt(random(ring.size))
                if (canStand(pick.x, pick.z)) return pick
            }
        }
        return tile
    }

    internal suspend fun Dialogue.sedridor(player: Player): Boolean {
        with(SedridorLines) {
            chatPlayer(ChatAnim.NEUTRAL, HELLO)
            chatNpc(ChatAnim.NEUTRAL, GREETING, "name" to player.username)
            while (true) {
                when (choice(TELEPORT, WHO, OLD_TOWER, NOTHING)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, TELEPORT)
                        return true
                    }
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, WHO)
                        chatNpc(ChatAnim.NEUTRAL, WHO_ANSWER)
                        chatNpc(ChatAnim.NEUTRAL, WHO_ANSWER_2)
                        chatPlayer(ChatAnim.NEUTRAL, WHO_THANKS)
                        chatNpc(ChatAnim.HAPPY, WHO_PLEASURE)
                        return false
                    }
                    // The tower's story ends back at his options.
                    3 -> {
                        chatPlayer(ChatAnim.QUIZ, OLD_TOWER)
                        for (line in OLD_TOWER_ANSWER) chatNpc(ChatAnim.NEUTRAL, line)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, NOTHING)
                        chatNpc(ChatAnim.NEUTRAL, FAREWELL)
                        return false
                    }
                }
            }
        }
    }
}

/**
 * [npc] sends the player to the rune essence mine, shouting [incantation] (each wizard's own, as live's capitals differ);
 * its portals send them back to [returnTo].
 */
suspend fun ScriptContext.teleportToEssenceMine(
    world: World,
    npc: Npc,
    returnTo: Tile = EssenceMine.TO_SEDRIDOR,
    incantation: Line = EssenceMineLines.INCANTATION,
) {
    npc.effects.say(world.textFor(player)[incantation])
    npc.effects.spotanim(0, EssenceMine.CURSE_CASTING, 92)
    // 2004Scape's essence_mine.rs2: heights 31 (ours are four times 2004Scape's), delay 61, offset 128, 5 cycles a tile.
    world.zones.projectile(
        Projectile(
            id = EssenceMine.CURSE_TRAVEL,
            fromX = npc.x,
            fromZ = npc.z,
            level = npc.level,
            toX = player.x,
            toZ = player.z,
            startHeight = 124,
            endHeight = 124,
            startTime = 61,
            endTime = 61 + 5 * maxOf(kotlin.math.abs(npc.x - player.x), kotlin.math.abs(npc.z - player.z)),
            angle = 16,
            progress = 128,
            sourceIndex = npc.index + 1,
            targetIndex = -(player.index + 1),
        ),
    )
    player.spotanim(EssenceMine.CURSE_IMPACT, 124, 70)
    delay(EssenceMine.SEND_TICKS)
    val landing = EssenceMine.LANDINGS[world.random.nextInt(EssenceMine.LANDINGS.size)]
    val to = EssenceMine.freeTileNear(landing, 1, { x, z -> world.canStand(x, z, 0) }) { world.random.nextInt(it) }
    EssenceMine.sentFrom(player, returnTo)
    world.teleport(player, to.x, to.z, 0)
}

fun registerEssenceMine(world: World) {
    with(EssenceMine) {
        for (portal in PORTAL_LOCS) if (world.locs.types[portal.id] != null) world.locs.add(portal)
        world.handlers.onNpc(SEDRIDOR, TALK_TO) { op ->
            var send = false
            world.dialogues.start(this, op.npc) { send = sedridor(player) }
            if (send) teleportToEssenceMine(world, op.npc!!)
        }
        world.handlers.onNpc(SEDRIDOR, TELEPORT_OP) { op -> teleportToEssenceMine(world, op.npc!!) }
        for (portal in PORTALS) {
            // The way out, as 2004Scape's essence_mine.rs2 has it (its portal, loc_2492): curse_travel from the portal to
            // the player (heights 15 to 31, no delay, offset 128), curse_impact 30 cycles on, and the teleport a tick later.
            world.handlers.onLoc(portal, 1) { op ->
                val loc = op.loc
                if (loc != null) {
                    world.zones.projectile(
                        Projectile(
                            id = CURSE_TRAVEL,
                            fromX = loc.x,
                            fromZ = loc.z,
                            level = loc.level,
                            toX = player.x,
                            toZ = player.z,
                            startHeight = 60,
                            endHeight = 124,
                            startTime = 0,
                            endTime = 5 * maxOf(kotlin.math.abs(loc.x - player.x), kotlin.math.abs(loc.z - player.z)),
                            angle = 16,
                            progress = 128,
                            targetIndex = -(player.index + 1),
                        ),
                    )
                }
                player.spotanim(CURSE_IMPACT, 124, 30)
                delay(1)
                val to = freeTileNear(takeReturn(player), 2, { x, z -> world.canStand(x, z, 0) }) { world.random.nextInt(it) }
                world.teleport(player, to.x, to.z, 0)
            }
        }
    }
}
