package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.InvObj
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Spellbook
import osrsps.game.skill.magic.TeleportSpell
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Telescope
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON7a (the study): its furniture builds ([FurnitureBuilds]), the telescopes are looked through and
 * the crystal balls change elemental staves, from the Wiki's Study page and its pieces' pages (read 2026-11-27).
 *
 * - Telescopes: Observe shows where and roughly when the next shooting star lands, to within 24, 9 or 2 minutes for
 *   the oak, teak and mahogany one (the telescope pages; [osrsps.game.skill.ShootingStars.lookThrough]).
 * - Crystal ball, elemental sphere and crystal of power: an elemental staff used on one is changed to another element
 *   ([changeStaff]). The crystal ball changes the basic staves, free; the elemental sphere a battlestaff too, for 100
 *   runes of the new element; the crystal of power a mystic staff too, for 1,000. Combination staves aren't elemental
 *   staves here. GUESSED: the element is picked from a menu of the other three; the words.
 * - Globes, orreries and charts are for looks (the Small orrery page: "no practical use ... except for ornamentation").
 *
 * - Lecterns (step CON7b; the Magic tablet, Oak, Demon and Marble lectern pages, read 2026-11-28): Study shows the
 *   tablets the lectern makes ([LECTERNS]) in a Make-X box, and Create-last makes the last tablet and quantity picked at
 *   that kind of lectern again ([makeTablets]). A tablet takes soft clay and "the same quantity of runes as for casting
 *   the spell", which a wielded elemental staff stands in for; it "requires the same Magic level (and active spellbook)
 *   as casting the spell", but the marble lectern makes them off the standard spellbook; one takes 4 ticks; its Magic XP
 *   is the spell's. Only the teleport tablets this server breaks ([osrsps.game.skill.magic.Teleports]) are made: the
 *   enchantment, bones-to and house, boat tablets wait on their effects. INFERRED: the `poh_create_magic_tablet` seq (with
 *   its `_withstaff` one while a staff is wielded) each tablet. GUESSED: the Make-X box (live has its own interface);
 *   the boosted Magic level counts; anyone in a house may use it; Create-last forgotten on logout; the words.
 *
 * Next (CON7c): the portal chamber. Left out: the charts' Study and the S.T.A.S.H. chart's View (no S.T.A.S.H.
 * units yet).
 */
class Study(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, telescope) in TELESCOPES) {
            world.handlers.onLoc(loc, OBSERVE) { op ->
                arriveDelay()
                val at = op.loc ?: return@onLoc
                if (houses.houseAt(at.x, at.z) == null) return@onLoc
                with(world.shootingStars) { lookThrough(telescope) }
            }
        }
        for (lectern in LECTERNS.keys) {
            world.handlers.onLoc(lectern, STUDY) { op -> arriveDelay(); studyLectern(this, op.loc ?: return@onLoc) }
            world.handlers.onLoc(lectern, CREATE_LAST) { op ->
                arriveDelay()
                val at = op.loc ?: return@onLoc
                val (spell, count) = lastMade[player]?.get(at.id) ?: return@onLoc studyLectern(this, at)
                makeTablets(this, at, spell, count)
            }
        }
        for ((loc, tier) in BALLS) {
            for (staff in STAVES.keys) {
                world.handlers.onObjOnLoc(loc, staff) { op ->
                    arriveDelay()
                    changeStaff(this, op.loc ?: return@onObjOnLoc, tier, op.sub, op.obj)
                }
            }
        }
    }

    /** The last tablet and quantity each player picked, by the kind of lectern (Create-last). */
    private val lastMade = java.util.WeakHashMap<osrsps.game.Player, MutableMap<Int, Pair<TeleportSpell, Int>>>()

    /** The teleport spells whose tablets [lectern] makes, lowest level first. */
    fun tablets(lectern: Int): List<TeleportSpell> {
        val levels = LECTERNS[lectern] ?: return emptyList()
        return world.teleports.data.spells.filter { it.tablet >= 0 && it.level in levels }.sortedBy { it.level }
    }

    /** Study on [loc]: the Make-X box of its tablets, then that many made. */
    private suspend fun studyLectern(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val offered = tablets(loc.id)
        if (offered.isEmpty()) return
        var picked = 0 to 0
        world.dialogues.start(context) { picked = makeX(ConstructionLines.LECTERN_TITLE, offered.map { it.tablet }, player.inventory.count(SOFT_CLAY).toInt().coerceAtLeast(1)) }
        world.interfaces.closeChatModal(player)
        val spell = offered.getOrNull(picked.first) ?: return
        if (picked.second <= 0) return
        lastMade.getOrPut(player) { HashMap() }[loc.id] = spell to picked.second
        makeTablets(context, loc, spell, picked.second)
    }

    /**
     * Makes up to [count] of [spell]'s tablet at the lectern [loc], one each [TABLET_TICKS], while it all lasts. Each next
     * tablet is a weak queue entry on [osrsps.game.skill.Making.QUEUE], as Making's products are, so walking off or
     * starting something else stops the batch (Codex review on #1057).
     */
    private fun makeTablets(context: ScriptContext, loc: ZoneLoc, spell: TeleportSpell, count: Int) {
        world.scripts.clearQueue(context.player, osrsps.game.skill.Making.QUEUE)
        context.makeTablet(loc, spell, count)
    }

    /** One tablet, then the next queued while [left] remain. */
    private fun ScriptContext.makeTablet(loc: ZoneLoc, spell: TeleportSpell, left: Int) {
        if (houses.houseAt(loc.x, loc.z) == null || !world.locs.exists(loc)) return
        if (loc.id != MARBLE_LECTERN && Spellbook.of(player) != Spellbook.STANDARD) return world.message(player, ConstructionLines.LECTERN_SPELLBOOK)
        if (player.skills.currentLevel(Skill.MAGIC) < spell.level) return world.message(player, ConstructionLines.LECTERN_LEVEL, "level" to spell.level)
        if (player.inventory.count(SOFT_CLAY) < 1) return world.message(player, ConstructionLines.LECTERN_CLAY)
        val weapon = player.equipment[WearPos.WEAPON]?.id
        val provided = world.spells.runesFrom(weapon)
        val costs = spell.runes.filter { it.rune !in provided } + listOfNotNull(spell.extra)
        if (costs.any { player.inventory.count(it.rune) < it.count }) return world.message(player, ConstructionLines.LECTERN_RUNES)
        for (cost in costs) Ned.take(player.inventory, cost.rune, cost.count)
        Ned.take(player.inventory, SOFT_CLAY, 1)
        player.inventory.add(world.objTypes[spell.tablet] ?: return, 1)
        player.anim(if (provided.isNotEmpty()) CREATE_ANIM_STAFF else CREATE_ANIM)
        player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        if (left > 1) queue(osrsps.game.skill.Making.QUEUE, osrsps.game.script.QueueType.WEAK, TABLET_TICKS) { makeTablet(loc, spell, left - 1) }
    }

    /** [staff], in pack slot [slot], used on a crystal ball [loc] that changes staves up to [tier]: to another element. */
    private suspend fun changeStaff(context: ScriptContext, loc: ZoneLoc, tier: Int, slot: Int, staff: Int) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val (element, kind) = STAVES[staff] ?: return
        if (kind > tier) return world.message(player, ConstructionLines.CRYSTAL_BALL_STAFF)
        val others = Element.entries.filter { it != element }
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(*others.map { it.line }.toTypedArray(), title = text(ConstructionLines.CRYSTAL_BALL_TITLE))
        }
        val to = others.getOrNull(pick - 1) ?: return
        if (player.inventory[slot]?.id != staff || houses.houseAt(loc.x, loc.z) == null || !world.locs.exists(loc)) return
        val runes = RUNE_COST[kind]
        if (player.inventory.count(to.rune) < runes) return world.message(player, ConstructionLines.CRYSTAL_BALL_RUNES, "runes" to runes)
        if (runes > 0) Ned.take(player.inventory, to.rune, runes)
        player.inventory[slot] = InvObj(to.staves[kind], 1)
        logger.info { "${player.username} changed staff $staff to ${to.staves[kind]} at a crystal ball" }
    }

    /** The four elements: each one's staff, battlestaff and mystic staff, its rune, and its line in the menu. */
    enum class Element(val staves: List<Int>, val rune: Int, val line: osrsps.game.dialogue.Line) {
        AIR(listOf(1381, 1397, 1405), 556, ConstructionLines.CRYSTAL_BALL_AIR),
        WATER(listOf(1383, 1395, 1403), 555, ConstructionLines.CRYSTAL_BALL_WATER),
        EARTH(listOf(1385, 1399, 1407), 557, ConstructionLines.CRYSTAL_BALL_EARTH),
        FIRE(listOf(1387, 1393, 1401), 554, ConstructionLines.CRYSTAL_BALL_FIRE),
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The telescopes (poh_telescope_1 to _3) and their Observe (op 1). */
        val TELESCOPES: Map<Int, Telescope> = mapOf(13656 to Telescope.OAK, 13657 to Telescope.TEAK, 13658 to Telescope.MAHOGANY)
        const val OBSERVE = 1

        /**
         * The lecterns (poh_lectern_1 to _8) and the levels of the teleport spells whose tablets each makes (the Magic
         * tablet page's table): oak Varrock; eagle Lumbridge and Falador too; teak eagle Camelot, Kourend Castle and
         * Ardougne too; mahogany eagle Civitas illa Fortis and Watchtower too; the demon ones Varrock (their other tablets
         * are enchantments and bones-to); marble all of them.
         */
        val LECTERNS: Map<Int, Set<Int>> = run {
            val oak = setOf(25)
            val eagle = oak + setOf(31, 37)
            val teakEagle = eagle + setOf(45, 48, 51)
            val mahoganyEagle = teakEagle + setOf(54, 58)
            mapOf(13642 to oak, 13643 to eagle, 13644 to oak, 13645 to teakEagle, 13646 to oak, 13647 to mahoganyEagle, 13648 to oak, MARBLE_LECTERN to mahoganyEagle)
        }
        const val MARBLE_LECTERN = 37349
        const val STUDY = 1
        const val CREATE_LAST = 2
        const val SOFT_CLAY = 1761

        /** A tablet's time (the Magic tablet page: "taking 4 ticks"), and the seqs poh_create_magic_tablet and its _withstaff. */
        const val TABLET_TICKS = 4
        const val CREATE_ANIM = 4067
        const val CREATE_ANIM_STAFF = 4068

        /** The crystal ball, elemental sphere and crystal of power (poh_crystalball_1 to _3), and the staves each changes: 0 basic, 1 battle, 2 mystic. */
        val BALLS: Map<Int, Int> = mapOf(13659 to 0, 13660 to 1, 13661 to 2)

        /** The runes of the new element each kind of staff costs (the Elemental sphere and Crystal of power pages). */
        val RUNE_COST = listOf(0, 100, 1000)

        /** Every elemental staff, battlestaff and mystic staff, with its element and kind. */
        val STAVES: Map<Int, Pair<Element, Int>> = Element.entries.flatMap { e -> e.staves.mapIndexed { kind, id -> id to (e to kind) } }.toMap()
    }
}
