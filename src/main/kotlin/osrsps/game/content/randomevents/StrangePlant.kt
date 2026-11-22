package osrsps.game.content.randomevents

import osrsps.game.Movement
import osrsps.game.World
import osrsps.game.combat.Foods
import osrsps.game.content.message
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The strange fruit's words; in the dialogue file, from the Strange plant page (read 2026-11-19). */
object StrangePlantLines : Lines("strange_plant", "Strange plant") {
    val EATEN = line("eaten", "the message upon eating the strange fruit")
}

/**
 * The Strange plant (NPC 323, macro_triffidseed; Pick, Dismiss), from the OSRS Wiki's Strange plant page (read
 * 2026-11-19): it comes up near the player and grows; once grown, Pick gives a strange fruit (obj 464), which goes on
 * the ground for three hours, seen only by the picker, if the backpack is full. Eaten, the fruit gives back 30% run
 * energy and cures poison and venom. A members' event; every world here is one.
 *
 * Guessed: it stays where it came up rather than following (it is a plant); it grows for [GROW_TICKS] ticks
 * (triffid_grow, seq 348, whose length we don't have) and Pick before then does nothing; it says nothing overhead (no
 * transcript has words for it), and another player's Pick does nothing; the pick animation is the plant's
 * triffid_fruit_picked (350) with nothing from the player, the plant going [RandomEvents.LEAVE_TICKS] later; eating
 * the fruit takes a food's 3-tick delay. It cures as an anti-venom does with no immunity after, only Mod Ash's ~18
 * second block on poison and venom going on again ([POISON_BLOCK_TICKS]).
 */
object StrangePlant : RandomEvent("strange_plant", 323) {
    const val FRUIT = 464
    const val GROW_ANIM = 348
    const val PICKED_ANIM = 350
    const val GROW_TICKS = 5

    /** The Wiki: three hours on the ground. */
    const val FRUIT_GROUND_TICKS = 3 * 60 * 100

    const val ENERGY_PERCENT = 30

    /** Mod Ash (on the Wiki's Strange fruit page): a flag for ~18 seconds that stops poison and venom being put on again. */
    const val POISON_BLOCK_TICKS = 30

    override val follows = false

    override fun started(world: World, out: RandomEvents.Out) {
        out.npc.effects.anim(GROW_ANIM)
    }

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        val out = events.outFor(player) ?: return false
        if (world.tick - out.since < GROW_TICKS) return false
        out.npc.effects.anim(PICKED_ANIM)
        events.reward(player, FRUIT, ownTicks = FRUIT_GROUND_TICKS)
        // It goes a little later, as a leaving event does, so the pick animation is sent; till then a Pick does nothing.
        events.leave(out, player)
        return false
    }

    /** Eat on the strange fruit in [slot]. */
    fun eat(world: World, player: osrsps.game.Player, slot: Int) {
        if (player.dead || player.inventory[slot]?.id != FRUIT) return
        if (world.tick < player.foodDelayUntil) return
        player.foodDelayUntil = world.tick + Foods.DEFAULT_DELAY
        player.inventory.remove(slot, 1)
        player.anim(Foods.EAT_ANIM)
        player.runEnergy = minOf(Movement.MAX_ENERGY, player.runEnergy + Movement.MAX_ENERGY * ENERGY_PERCENT / 100)
        world.poison.cureVenom(player, 0)
        world.poison.block(player, POISON_BLOCK_TICKS)
        world.message(player, StrangePlantLines.EATEN)
    }
}
