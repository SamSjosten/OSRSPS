package osrsps.game.skill.magic

import osrsps.game.FountainOfRune
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.skill.MakeLines

/** What the orb charging spells say; their words are on the server (data/dialogue.txt), not in git. */
object ChargeOrbLines : Lines("charge_orb", "Charge Air Orb") {
    val TITLE = line("title", "the Make-X box casting an orb charging spell on its obelisk opens; words guessed")
    val NO_ORB = line("no_orb", "casting an orb charging spell on its obelisk with no unpowered orb; words guessed")
    val WRONG_OBELISK = line("wrong_obelisk", "casting an orb charging spell on another element's obelisk; words guessed")
}

/** One of the four spells: its [spell] (level, runes and component the cache's), the [obelisk] it is cast on and the [orb] it makes. */
class OrbSpell(val spell: ItemSpell, val obelisk: Int, val orb: Int)

/**
 * Charge Water, Earth, Fire and Air Orb (Magic utility step U8a): the spell picked on the magic tab and clicked on its
 * obelisk walks the player to it, then a Make-X box over the charged orb asks how many; each cast takes an unpowered
 * orb and the runes (the orb is the spell obj's third rune; a staff stands in for its own) and gives the charged orb and the spell's XP, the next following
 * [INTERVAL] ticks later as a weak queue entry (walking off ends it), as [BoltEnchant]'s casts do.
 *
 * Verified on the OSRS Wiki (Charge Water/Earth/Fire/Air Orb, read 2026-11-19): levels 56, 60, 63 and 66, 66, 70, 73
 * and 76 XP, 30 elemental runes, 3 cosmic runes and an unpowered orb a cast (the runes the cache's), each cast on its
 * own element's obelisk, and a repeat every 6 ticks ("autocast") against 3 for clicking each cast. The animation is
 * the cache's `human_castchargeorb` (726) and the graphics `chargewaterorb_casting` (149), `chargewindorb_casting`
 * (150), `chargeearthorb_casting` (151) and `chargefireorb_casting` (152).
 *
 * Guessed: the Make-X box itself and its words; the refusal words for no orb and the wrong obelisk; casting from
 * beside the obelisk, as an item used on it; a click cast again after 3 ticks starting a fresh run; the graphic's
 * height 92; no XP at the Fountain of Rune, as the other spells; nothing taken on a refusal.
 */
class ChargeOrbs(private val world: World, private val spells: List<OrbSpell>) {
    fun register() {
        for (spell in spells) {
            for (obelisk in OBELISKS) {
                world.handlers.onSpellOnLoc(obelisk, Spells.SPELLBOOK, spell.spell.component) {
                    if (obelisk == spell.obelisk) open(spell) else player.message(world.textFor(player)[ChargeOrbLines.WRONG_OBELISK])
                }
            }
        }
    }

    private suspend fun ScriptContext.open(orbSpell: OrbSpell) {
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        val text = world.textFor(player)
        if (!world.itemSpells.hasLevel(player, orbSpell.spell)) return
        val held = player.inventory.count(UNPOWERED_ORB).toInt()
        if (held <= 0) {
            player.message(text[ChargeOrbLines.NO_ORB])
            return
        }
        var picked = 0 to 0
        world.dialogues.start(this) { picked = makeX(ChargeOrbLines.TITLE, listOf(orbSpell.orb), held) }
        val (index, quantity) = picked
        if (index != 0 || quantity <= 0) return
        world.interfaces.closeChatModal(player)
        world.scripts.clearQueue(player, QUEUE)
        castOne(orbSpell, quantity, player.x, player.z, player.level)
    }

    /**
     * One cast, then the next queued while [left] casts remain and the orbs, level and runes last. The player must still
     * stand where the first cast was, beside the obelisk at [x], [z], [level]: a fight that retaliation starts can move
     * them without dropping the weak queue (Codex review on #987).
     */
    private fun ScriptContext.castOne(orbSpell: OrbSpell, left: Int, x: Int, z: Int, level: Int) {
        val spell = orbSpell.spell
        val casting = world.itemSpells
        if (player.x != x || player.z != z || player.level != level) return
        // The clock alchemy, Telekinetic Grab and the others share, so no two land in one tick (Codex review on #987).
        if (casting.readyAt(player) > world.tick) return
        if (player.inventory.count(UNPOWERED_ORB) <= 0) return
        if (!casting.hasLevel(player, spell)) return
        val costs = casting.runesToPay(player, spell) ?: return
        val orb = world.objTypes[orbSpell.orb] ?: return
        // The unpowered orb is the cache's third "rune", so paying takes it.
        val saved = player.inventory.contents().toMap()
        if (!casting.pay(player, costs)) return
        if (player.inventory.add(orb, 1) < 1) {
            for (slot in 0 until player.inventory.capacity) player.inventory[slot] = saved[slot]
            player.message(world.textFor(player)[MakeLines.NO_SPACE])
            return
        }
        casting.startClock(player, CLICK_INTERVAL)
        if (!FountainOfRune.covers(player)) player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        player.anim(spell.anim)
        player.spotanim(spell.spotanim, ItemSpells.SPOTANIM_HEIGHT)
        if (left <= 1 || player.inventory.count(UNPOWERED_ORB) <= 0) return
        queue(QUEUE, QueueType.WEAK, INTERVAL) { castOne(orbSpell, left - 1, x, z, level) }
    }

    companion object {
        /** `stafforb`. */
        const val UNPOWERED_ORB = 567

        /** Ticks between the Make-X box's casts (Wiki: 6 on "autocast"). */
        const val INTERVAL = 6

        /** Ticks between casts clicked one by one (Wiki: 3). */
        const val CLICK_INTERVAL = 3

        const val QUEUE = "charge_orb"

        /** `human_castchargeorb`. */
        const val ANIM = 726

        fun load(objTypes: ObjTypes): List<OrbSpell> = listOfNotNull(
            orb(objTypes, "56_charge_water_orb", 660, 149, OBELISK_WATER, 571),
            orb(objTypes, "60_charge_earth_orb", 700, 151, OBELISK_EARTH, 575),
            orb(objTypes, "63_charge_fire_orb", 730, 152, OBELISK_FIRE, 569),
            orb(objTypes, "66_charge_air_orb", 760, 150, OBELISK_AIR, 573),
        )

        /** The cache's `obelisk_water`, `obelisk_earth`, `obelisk_fire` and `obelisk_air`. */
        const val OBELISK_WATER = 2151
        const val OBELISK_EARTH = 2150
        const val OBELISK_FIRE = 2153
        const val OBELISK_AIR = 2152
        val OBELISKS = listOf(OBELISK_WATER, OBELISK_EARTH, OBELISK_FIRE, OBELISK_AIR)

        /** [orb] is the charged orb's id, the cache's (`water_orb` and the rest). */
        private fun orb(objTypes: ObjTypes, key: String, xpTenths: Int, spotanim: Int, obelisk: Int, orb: Int): OrbSpell? =
            ItemSpell.load(objTypes, key, xpTenths, CLICK_INTERVAL, ANIM, spotanim)?.let { OrbSpell(it, obelisk, orb) }
    }
}
