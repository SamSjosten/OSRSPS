package osrsps.game.skill.magic

import osrsps.game.FountainOfRune
import osrsps.game.InvObj
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** What Bones to Bananas and Bones to Peaches say; their words are on the server (data/dialogue.txt), not in git. */
object BonesToFruitLines : Lines("bones_to_fruit", "Bones to Bananas") {
    val NO_BONES = line("no_bones", "casting Bones to Bananas or Bones to Peaches holding no bones (the Wiki's Bones to Peaches)")
    val UNSUITABLE = line("unsuitable", "casting either spell holding only bones it doesn't change (the Wiki's Bones to Peaches)")
}

/** One of the two spells: its [spell] (level, runes and magic tab component the cache's) and the [fruit] it makes. */
class FruitSpell(val spell: ItemSpell, val fruit: Int)

/**
 * Bones to Bananas and Bones to Peaches (Magic utility step U7a): Cast on the magic tab turns every unnoted bone of the
 * spell's list in the backpack into a banana or a peach, slot for slot, for the spell's XP once however many it
 * changes.
 *
 * Verified on the OSRS Wiki (Bones to Bananas and Bones to Peaches, read 2026-11-19): levels 15 and 60, 25 and 35.5 XP
 * a cast whatever the count, the cache's runes, the list of bones (bones, burnt, bat, bleached, jogre, wolf, monkey,
 * Alan's and big bones, both spells alike), only unnoted bones for peaches, higher-tier bones
 * refused, a cast a tick that doesn't stall the player, and the two messages quoted for peaches. The animation and
 * graphic are the cache's `human_castbonestobananas` (722) and `bonestobananas_casting` (141).
 *
 * Guessed: Bones to Peaches open to everyone (the Wiki gates it behind Mage Training Arena points; this world plays
 * as if everything is done); noted bones left alone by Bananas too; Peaches showing the same animation and graphic;
 * the graphic at the teleports' height 92; the peach messages for Bananas too; "monkey bones" taken as every
 * Ape Atoll bone named monkey bones and "bones" as the ordinary 526 only; any other buryable item counting as
 * unsuitable bones; no XP at the Fountain of Rune (as the other spells); nothing taken on a refusal. The Tombs of
 * Amascut refusal isn't built, as the Tombs aren't; nor are the Mage Training Arena's animal bones, which only the
 * Creature Graveyard gives and which make 1 to 4 fruit each there (Codex review on #964): they go with the Graveyard.
 */
class BonesToFruit(private val world: World, private val spells: List<FruitSpell>) {
    /** The tick of each player's last cast, so a cast a tick is the most anyone gets. */
    private val lastCast = java.util.WeakHashMap<Player, Long>()

    fun register() {
        for (spell in spells) world.handlers.onButton(Spells.SPELLBOOK, spell.spell.component, CAST) { cast(spell) }
    }

    private fun ScriptContext.cast(fruitSpell: FruitSpell) {
        val spell = fruitSpell.spell
        if (Spellbook.of(player) != Spellbook.STANDARD || lastCast[player] == world.tick) return
        val casting = world.itemSpells
        if (!casting.hasLevel(player, spell)) return
        val text = world.textFor(player)
        val slots = (0 until player.inventory.capacity).filter { player.inventory[it]?.id in BONES }
        if (slots.isEmpty()) {
            val otherBones = (0 until player.inventory.capacity).any { slot -> player.inventory[slot]?.let { isBones(it.id) } == true }
            player.message(text[if (otherBones) BonesToFruitLines.UNSUITABLE else BonesToFruitLines.NO_BONES])
            return
        }
        val costs = casting.runesToPay(player, spell) ?: return
        val fruit = world.objTypes[fruitSpell.fruit] ?: return
        if (!casting.pay(player, costs)) return
        for (slot in slots) player.inventory[slot] = InvObj(fruit.id, 1)
        lastCast[player] = world.tick
        if (!FountainOfRune.covers(player)) player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        player.anim(spell.anim)
        player.spotanim(spell.spotanim, ItemSpells.SPOTANIM_HEIGHT)
    }

    /** Whether [obj] is an unnoted item that is buried or broken down, as bones are. */
    private fun isBones(obj: Int): Boolean {
        val type = world.objTypes[obj] ?: return false
        if (type.isCert) return false
        val first = type.iops.firstOrNull()
        return first == "Bury" || first == "Break-down"
    }

    companion object {
        /** The magic tab's op1, Cast. */
        const val CAST = 1

        const val BANANA = 1963
        const val PEACH = 6883

        /** `human_castbonestobananas` and `bonestobananas_casting`. */
        const val ANIM = 722
        const val SPOTANIM = 141

        /**
         * The bones both spells change (Wiki): bones, burnt bones, bat bones, big bones, wolf bones, jogre bones, the
         * monkey bones of Ape Atoll, bleached bones and Alan's bones.
         */
        val BONES = setOf(526, 528, 530, 532, 2859, 3125, 3179, 3180, 3183, 3185, 3186, 25422, 30973)

        fun load(objTypes: ObjTypes): List<FruitSpell> = listOfNotNull(
            ItemSpell.load(objTypes, "15_bones_bananas", 250, 1, ANIM, SPOTANIM)?.let { FruitSpell(it, BANANA) },
            ItemSpell.load(objTypes, "60_bones_peaches", 355, 1, ANIM, SPOTANIM)?.let { FruitSpell(it, PEACH) },
        )
    }
}
