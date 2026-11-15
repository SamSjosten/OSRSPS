package osrsps.game.skill

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines

/** The Slayer helmet's assembly messages. None is quoted on the Wiki, so all are unsourced and stay empty until found. */
object SlayerHelmetLines : Lines("slayer_helmet", "Slayer helmet") {
    val NEED_UNLOCK = line("need_unlock", "using one Slayer helmet part on another without Malevolent Masquerade bought")
    val NEED_CRAFTING = line("need_crafting", "using one Slayer helmet part on another below 55 Crafting")
    val MISSING_PARTS = line("missing_parts", "using one Slayer helmet part on another without all seven parts in the inventory")
    val ASSEMBLED = line("assembled", "the Slayer helmet made from its parts")
    val NO_ROOM = line("no_room", "Disassemble on a Slayer helmet without room for its parts")
    val DISASSEMBLED = line("disassembled", "a Slayer helmet taken apart into its parts")
}

/**
 * The Slayer helmet's assembly (Slayer step 7), from the OSRS Wiki's Slayer helmet and Slayer helmet (i) pages (read
 * 2026-11-15). With Malevolent Masquerade bought and 55 Crafting (boosts count, no XP), using any part on another while
 * all of them are in the inventory makes the helmet at once: a black mask (any charges, which are lost), earmuffs, a
 * facemask, a nose peg, a spiny helmet, an enchanted gem and reinforced goggles (needed after A Porcine of Interest,
 * which counts as done here). A black mask (i) makes the Slayer helmet (i) of its own kind (Nightmare Zone, Soul Wars
 * or Emir's Arena). Disassemble gives the parts back, an imbued helmet giving its uncharged black mask (i). The helmet's
 * Check, worn or held, is the enchanted gem's.
 *
 * Guessed: the helmet takes the slot of the part that was used; Disassemble needs room for the six parts beyond the
 * helmet's own slot. Left out: the recoloured helmets (their unlocks and items aren't in), Partner and Log.
 */
class SlayerHelmets(private val world: World, private val slayer: Slayer) {
    /** One kind of black mask: its charged and uncharged ids, and the helmet it makes. */
    class Kind(val masks: List<Int>, val uncharged: Int, val helmet: Int)

    companion object {
        /** Malevolent Masquerade's bit in slayer_unlock (the 242 cache's row, cost 400). */
        const val MALEVOLENT_MASQUERADE = 5

        const val CRAFTING_LEVEL = 55

        const val EARMUFFS = 4166
        const val FACEMASK = 4164
        const val NOSE_PEG = 4168
        const val SPINY_HELMET = 4551
        const val REINFORCED_GOGGLES = 24942

        /** The parts beside the mask. */
        val PARTS = listOf(EARMUFFS, FACEMASK, NOSE_PEG, SPINY_HELMET, Slayer.ENCHANTED_GEM, REINFORCED_GOGGLES)

        const val SLAYER_HELMET = 11864

        /** The 242 cache's black masks: charged (10) down to (1) on every other id or in a row, then uncharged. */
        val KINDS = listOf(
            Kind((8901..8919 step 2).toList(), 8921, SLAYER_HELMET),
            Kind((11774..11783).toList(), 11784, 11865),
            Kind((25266..25275).toList(), 25276, 25177),
            Kind((26771..26780).toList(), 26781, 26674),
        )

        val HELMETS = KINDS.map { it.helmet }

        /** Its option numbers on the 242 cache's helmets: iop3 Check, iop4 Disassemble, worn option 1 Check. */
        const val CHECK = 3
        const val DISASSEMBLE = 4
        const val WORN_CHECK = 1
    }

    private fun Kind.allMasks() = masks + uncharged

    fun register() {
        val handlers = world.handlers
        val kinds = KINDS.filter { world.objTypes[it.helmet] != null }
        val masks = kinds.flatMap { it.allMasks() }.filter { world.objTypes[it] != null }
        val parts = PARTS.filter { world.objTypes[it] != null }
        if (parts.size != PARTS.size) return
        // Every part on every other part or mask, either way round (the registration covers both orders).
        for ((i, part) in parts.withIndex()) {
            for (other in parts.drop(i + 1) + masks) handlers.onObjOnObj(part, other) { assemble(player) }
        }
        for (helmet in kinds.map { it.helmet }) {
            handlers.onObj(helmet, CHECK) { slayer.check(player) }
            handlers.onWorn(helmet, WORN_CHECK) { slayer.check(player) }
            handlers.onObj(helmet, DISASSEMBLE) { op -> disassemble(player, op.sub, helmet) }
        }
    }

    /** Makes the helmet from the parts in [player]'s inventory, if they may. */
    fun assemble(player: Player) {
        if (!slayer.hasBit(player, MALEVOLENT_MASQUERADE)) return world.message(player, SlayerHelmetLines.NEED_UNLOCK)
        if (player.skills.currentLevel(Skill.CRAFTING) < CRAFTING_LEVEL) return world.message(player, SlayerHelmetLines.NEED_CRAFTING)
        val inv = player.inventory
        val contents = inv.contents()
        // The first mask held decides the kind, plain before imbued (guessed, for a player holding both).
        val mask = KINDS.filter { world.objTypes[it.helmet] != null }.firstNotNullOfOrNull { kind -> contents.firstOrNull { it.second.id in kind.allMasks() }?.let { kind to it.first } }
        val partSlots = PARTS.map { part -> contents.firstOrNull { it.second.id == part }?.first }
        if (mask == null || partSlots.any { it == null }) return world.message(player, SlayerHelmetLines.MISSING_PARTS)
        val (kind, maskSlot) = mask
        for (slot in partSlots) inv.remove(slot!!, 1)
        inv[maskSlot] = InvObj(kind.helmet, 1)
        world.message(player, SlayerHelmetLines.ASSEMBLED)
    }

    /** Takes the helmet in [slot] apart into its parts, with the uncharged mask of its kind. */
    fun disassemble(player: Player, slot: Int, helmet: Int) {
        val inv = player.inventory
        if (slot < 0 || inv[slot]?.id != helmet) return
        val kind = KINDS.first { it.helmet == helmet }
        if (inv.freeSlots < PARTS.size) return world.message(player, SlayerHelmetLines.NO_ROOM)
        inv[slot] = InvObj(kind.uncharged, 1)
        for (part in PARTS) inv.add(world.objTypes[part] ?: continue, 1)
        world.message(player, SlayerHelmetLines.DISASSEMBLED)
    }
}
