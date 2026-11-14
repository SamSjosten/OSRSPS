package osrsps.game.skill.farming

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.FarmsRoads
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext

/**
 * The patch gardeners' lines, from the Wiki's Transcript:Fayeth and Transcript:Treznor (read 2026-11-14), the fullest
 * of the gardeners' pages; every gardener is taken to say the same (inferred: the pages of the others are marked
 * incomplete, "should be near identical").
 */
object GardenerLines : Lines("gardener", "Transcript:Fayeth") {
    val LOOK_AFTER = line("look_after", "Talk-to's menu: would they look after the crops, which the player then says")
    val CHOP = line("chop", "Talk-to's menu with a grown tree in the patch: would they chop it down, which the player then says")
    val ADVICE = line("advice", "Talk-to's menu: farming advice, which the player then says")
    val SELL = line("sell", "Talk-to's menu: can they sell something, which the player then says")
    val ANOTHER_TIME = line("another_time", "Talk-to's menu, last option, which the player then says")

    val NO_SAPLING = line("no_sapling", "look after a tree or fruit tree patch with nothing planted")
    val NO_SEEDS = line("no_seeds", "look after any other patch with nothing planted (guessed: the Wiki has only the tree gardeners' words)")
    val ALREADY = line("already", "look after, Talk-to: a patch already paid for")
    val FORGOT = line("forgot", "look after, Talk-to: the player's answer to that")
    val PAY_ALREADY = line("pay_already", "Pay on a patch already paid for")
    val DISEASED = line("diseased", "look after a diseased patch")
    val DEAD = line("dead", "look after a dead patch")
    val GROWN = line("grown", "look after a grown crop that can't be chopped down (guessed: the Wiki has no line)")
    val IMMUNE = line("immune", "look after a poison ivy bush, which can't fall ill (guessed: the Wiki has no line)")
    val WANT = line("want", "look after, Talk-to: the payment asked for", "payment")
    val PAY_WANT = line("pay_want", "Pay without the payment: the payment asked for", "payment")
    val PAID = line("paid", "the payment taken: they will look after the patch")
    val DONT_HAVE = line("dont_have", "look after, Talk-to, without the payment: the player")
    val NOT_FREE = line("not_free", "look after, Talk-to, without the payment: their answer")

    val CHOP_YOURSELF = line("chop_yourself", "chop, with the Woodcutting level to chop the tree: they ask why")
    val DO_IT_MYSELF = line("do_it_myself", "that question's menu, option 1, which the player then says")
    val RATHER_PAY = line("rather_pay", "that question's menu, option 2, which the player then says")
    val HARD_WORK = line("hard_work", "chop: the 200 coins asked")
    val NO_COINS = line("no_coins", "chop without 200 coins: the player")
    val HERES_200 = line("heres_200", "the 200 coins menu, option 1, which the player then says")
    val TOO_MUCH = line("too_much", "the 200 coins menu, option 2, which the player then says")
    val CHOP_TITLE = line("chop_title", "Pay on a grown tree: the menu's title")
    val YES = line("yes", "Pay on a grown tree: the menu, option 1")
    val NO = line("no", "Pay on a grown tree: the menu, option 2")

    val SELL_WHAT = line("sell_what", "can they sell something: their answer, before the shop opens")
    val COMPOSTABLE = line("compostable", "an item that makes compost used on them")
    val SUPERCOMPOSTABLE = line("supercompostable", "an item that makes supercompost used on them")
    val NOT_COMPOSTABLE = line("not_compostable", "any other item used on them (guessed: the Wiki has no line)")

    /** The farming advice, one picked at random each time; numbered as on Transcript:Treznor. */
    val ADVICES: List<Line> = List(ADVICE_COUNT) { line("advice_${it + 1}", "farming advice, Treznor's dialogue ${it + 1}") }

    /** Each crop's payment as the gardener names it, by crop key, for [WANT] and [PAY_WANT]: Fayeth's for the trees, guessed for the rest. */
    val PAYMENT_NAMES: Map<String, Line> = Gardeners.PAYMENTS.keys.associateWith { line("payment_$it", "the payment for $it, as they name it") }
}

private const val ADVICE_COUNT = 17

/** What a gardener takes to look after a crop: [count] of [obj], noted or not (the Wiki's Farming: "Payment may be noted"). */
class Payment(val obj: Int, val count: Int)

/**
 * Misthalin's patch gardeners and their Farming options (the Wiki's Farming, "Disease, payment, compost, and
 * watering", the payment tables of Allotment, Hops, Bush, Tree and Fruit tree patch seeds, and Transcript:Fayeth,
 * read 2026-11-14).
 *
 * - Look after (Talk-to or Pay): a crop growing and well is paid for once with its payment ([PAYMENTS]), and never
 *   falls ill from then on ([PatchState.paid]). Herbs and flowers can't be paid for (none of their gardeners is
 *   placed); a poison ivy bush can't fall ill.
 * - A grown tree or fruit tree: Talk-to offers to chop it down and Pay asks, for 200 coins, which takes it away and
 *   leaves the patch clear (the Farming page: "instantly remove it").
 * - Advice: one of 17 lines at random, then the menu again. Sell opens Farming Supplies, as Trade does (shops.json). An item used on a
 *   gardener says whether it makes compost or supercompost.
 * - An item used on a Tool Leprechaun is changed to banknotes, every one of it in the pack, if it is something grown
 *   ([NOTABLE]); his store is the next step.
 *
 * Guessed: the hops and bush gardeners' words for an empty patch and the payment names other than the trees'; what a
 * gardener says of a grown crop that can't be chopped, of poison ivy and of an item that makes no compost; the
 * player's line for having no 200 coins on Pay; that the coins come out and the patch clears with no more said; the
 * patch cleared to raked soil, as a dug-up stump leaves it; unnoted payment taken before noted; advice going back to
 * the menu (Fayeth's page: "initial"); the Farming Supplies stock count (the Wiki: infinite).
 */
class Gardeners(private val world: World) {
    fun register() {
        for ((npc, patch) in PATCHES) {
            world.handlers.onNpc(npc, FarmsRoads.TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = talk(patch) }
                if (trade) openShop(npc)
            }
            world.handlers.onNpc(npc, FarmsRoads.PAY) { op -> world.dialogues.start(this, op.npc) { pay(patch) } }
            world.handlers.onObjOnNpc(npc) { op -> world.dialogues.start(this, op.npc) { showItem(op.obj) } }
        }
        world.handlers.onObjOnNpc(FarmsRoads.TOOL_LEPRECHAUN) { op -> note(op) }
    }

    private fun ScriptContext.openShop(npc: Int) {
        world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
    }

    /** Talk-to: the menu, again after advice. True when the player asked to buy something. */
    private suspend fun Dialogue.talk(patch: FarmingPatch): Boolean = with(GardenerLines) {
        var trade = false
        var again = true
        while (again) {
            again = false
            val options = if (choppable(patch)) listOf(LOOK_AFTER, CHOP, ADVICE, SELL, ANOTHER_TIME) else listOf(LOOK_AFTER, ADVICE, SELL, ANOTHER_TIME)
            val picked = options[choice(*options.toTypedArray()) - 1]
            chatPlayer(if (picked == ANOTHER_TIME) ChatAnim.NEUTRAL else ChatAnim.QUIZ, picked)
            when (picked) {
                LOOK_AFTER -> lookAfter(patch, talk = true)
                CHOP -> chopTalk(patch)
                ADVICE -> {
                    chatNpc(ChatAnim.NEUTRAL, ADVICES[world.random.nextInt(ADVICES.size)])
                    again = true
                }
                SELL -> {
                    chatNpc(ChatAnim.HAPPY, SELL_WHAT)
                    trade = true
                }
            }
        }
        trade
    }

    /** Pay: look after the crop, or with a grown tree, chop it down. */
    private suspend fun Dialogue.pay(patch: FarmingPatch) = with(GardenerLines) {
        if (!choppable(patch)) return@with lookAfter(patch, talk = false)
        if (choice(YES, NO, title = text(CHOP_TITLE)) != 1) return@with
        if (player.inventory.count(COINS) < CHOP_COINS) return@with chatPlayer(ChatAnim.SAD, NO_COINS)
        chopDown(patch)
    }

    private suspend fun Dialogue.lookAfter(patch: FarmingPatch, talk: Boolean): Unit = with(GardenerLines) {
        val state = player.farming.patches[patch.key]
        val crop = state?.crop
        when {
            state == null || crop == null -> chatNpc(ChatAnim.NEUTRAL, if (patch.kind.tree || patch.kind == PatchKind.FRUIT_TREE) NO_SAPLING else NO_SEEDS)
            state.paid -> if (talk) {
                chatNpc(ChatAnim.CONFUSED, ALREADY)
                chatPlayer(ChatAnim.SAD, FORGOT)
            } else {
                chatNpc(ChatAnim.NEUTRAL, PAY_ALREADY)
            }
            state.dead -> chatNpc(ChatAnim.SAD, DEAD)
            state.diseased -> chatNpc(ChatAnim.ANGRY, DISEASED)
            state.stage >= crop.stages -> chatNpc(ChatAnim.NEUTRAL, GROWN)
            else -> {
                val payment = PAYMENTS[crop.key] ?: return@with chatNpc(ChatAnim.NEUTRAL, IMMUNE)
                val named = text(PAYMENT_NAMES.getValue(crop.key))
                if (talk) {
                    chatNpc(ChatAnim.NEUTRAL, WANT, "payment" to named)
                    // The patch kept growing while she spoke: one that is no longer the same healthy crop is answered afresh.
                    val now = player.farming.patches[patch.key]
                    if (now !== state || now.crop !== crop || !now.growing || now.paid) return@with lookAfter(patch, talk = false)
                }
                val has = held(world, player, payment) >= payment.count
                if (!has) {
                    if (talk) {
                        chatPlayer(ChatAnim.SAD, DONT_HAVE)
                        chatNpc(ChatAnim.ANGRY, NOT_FREE)
                    } else {
                        chatNpc(ChatAnim.NEUTRAL, PAY_WANT, "payment" to named)
                    }
                    return@with
                }
                take(world, player, payment)
                state.paid = true
                chatNpc(ChatAnim.HAPPY, PAID)
            }
        }
    }

    private suspend fun Dialogue.chopTalk(patch: FarmingPatch) = with(GardenerLines) {
        if (canChop(patch)) {
            chatNpc(ChatAnim.QUIZ, CHOP_YOURSELF)
            if (choice(DO_IT_MYSELF, RATHER_PAY) == 1) return@with chatPlayer(ChatAnim.NEUTRAL, DO_IT_MYSELF)
            chatPlayer(ChatAnim.NEUTRAL, RATHER_PAY)
        }
        chatNpc(ChatAnim.NEUTRAL, HARD_WORK)
        if (player.inventory.count(COINS) < CHOP_COINS) return@with chatPlayer(ChatAnim.SAD, NO_COINS)
        if (choice(HERES_200, TOO_MUCH) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, TOO_MUCH)
        chatPlayer(ChatAnim.NEUTRAL, HERES_200)
        chopDown(patch)
    }

    /** Takes the 200 coins and the tree: the patch is clear soil again. */
    private fun Dialogue.chopDown(patch: FarmingPatch) {
        if (!choppable(patch) || player.inventory.count(COINS) < CHOP_COINS) return
        removeCount(player.inventory, COINS, CHOP_COINS)
        player.farming.patches[patch.key]?.clear()
        world.farming.refresh(player)
    }

    /** A grown tree or fruit tree (standing or a stump) in [patch]. */
    private fun Dialogue.choppable(patch: FarmingPatch): Boolean {
        if (!patch.kind.tree && patch.kind != PatchKind.FRUIT_TREE) return false
        val state = player.farming.patches[patch.key] ?: return false
        val crop = state.crop ?: return false
        return !state.dead && state.stage >= crop.stages
    }

    /** Whether the player could chop the tree in [patch] themselves: any fruit tree, or a tree at its Woodcutting level. */
    private fun Dialogue.canChop(patch: FarmingPatch): Boolean {
        val crop = player.farming.patches[patch.key]?.crop ?: return false
        val key = Trees.of(crop)?.woodcutting ?: return true
        val level = world.woodcutting.data.trees.values.firstOrNull { it.key == key }?.level ?: return true
        return player.skills.currentLevel(Skill.WOODCUTTING) >= level
    }

    private suspend fun Dialogue.showItem(obj: Int) = with(GardenerLines) {
        chatNpc(
            ChatAnim.NEUTRAL,
            when (obj) {
                in Compostables.SUPERCOMPOSTABLE -> SUPERCOMPOSTABLE
                in Compostables.COMPOSTABLE -> COMPOSTABLE
                else -> NOT_COMPOSTABLE
            },
        )
    }

    /** An item used on a Tool Leprechaun: every one of it in the pack changed to banknotes, or why not. */
    private suspend fun ScriptContext.note(op: Op) {
        val type = world.objTypes[op.obj] ?: return
        val refusal = when {
            type.isCert -> NoteLines.IS_NOTE
            type.certlink < 0 -> NoteLines.NO_SUCH_NOTE
            op.obj !in NOTABLE -> NoteLines.NO_NOTES
            else -> null
        }
        if (refusal != null) return world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, refusal) }
        val note = world.objTypes[type.certlink] ?: return
        val count = player.inventory.count(op.obj)
        if (count <= 0 || player.inventory.count(note.id) + count > Inventory.MAX_STACK) return
        removeCount(player.inventory, op.obj, count.toInt())
        player.inventory.add(note, count.toInt())
    }

    companion object {
        const val COINS = 995
        const val CHOP_COINS = 200

        /** Which patch each gardener looks after: Dreven, Vasquen, Fayeth and Treznor (their spawns' sources). */
        val PATCHES: Map<Int, FarmingPatch> = mapOf(
            FarmsRoads.DREVEN to FarmingPatches.CHAMPIONS_BUSH,
            FarmsRoads.VASQUEN to FarmingPatches.LUMBRIDGE_HOPS,
            FarmsRoads.FAYETH to FarmingPatches.LUMBRIDGE_TREE,
            FarmsRoads.TREZNOR to FarmingPatches.VARROCK_TREE,
        )

        private const val COMPOST = 6032
        private const val POTATOES_10 = 5438
        private const val ONIONS_10 = 5458
        private const val CABBAGES_10 = 5478
        private const val TOMATOES_5 = 5968
        private const val APPLES_5 = 5386
        private const val ORANGES_5 = 5396
        private const val STRAWBERRIES_5 = 5406
        private const val BANANAS_5 = 5416

        /**
         * Each crop's payment, by crop key: the Wiki's payment columns (Allotment, Hops, Bush, Tree and Fruit tree patch
         * seeds, read 2026-11-14). Sacks hold 10 and baskets 5, full ones only.
         */
        val PAYMENTS: Map<String, Payment> = linkedMapOf(
            "potato" to Payment(COMPOST, 2), "onion" to Payment(POTATOES_10, 1), "cabbage" to Payment(ONIONS_10, 1),
            "tomato" to Payment(CABBAGES_10, 2), "sweetcorn" to Payment(5931, 10), "strawberry" to Payment(APPLES_5, 1),
            "watermelon" to Payment(5970, 10), "snape_grass" to Payment(247, 5),
            "barley" to Payment(COMPOST, 3), "hammerstone" to Payment(6010, 1), "asgarnian" to Payment(ONIONS_10, 1),
            "jute" to Payment(6008, 6), "yanillian" to Payment(TOMATOES_5, 1), "krandorian" to Payment(CABBAGES_10, 3),
            "wildblood" to Payment(6012, 1),
            "redberry" to Payment(CABBAGES_10, 4), "cadavaberry" to Payment(TOMATOES_5, 3), "dwellberry" to Payment(STRAWBERRIES_5, 3),
            "jangerberry" to Payment(5982, 6), "whiteberry" to Payment(6004, 8),
            "oak" to Payment(TOMATOES_5, 1), "willow" to Payment(APPLES_5, 1), "maple" to Payment(ORANGES_5, 1),
            "yew" to Payment(6016, 10), "magic" to Payment(5974, 25),
            "apple" to Payment(5986, 9), "banana" to Payment(APPLES_5, 4), "orange" to Payment(STRAWBERRIES_5, 3),
            "curry" to Payment(BANANAS_5, 5), "pineapple" to Payment(5982, 10), "papaya" to Payment(2114, 10),
            "palm" to Payment(5972, 15), "dragonfruit" to Payment(5974, 15),
        )

        /** Clean herbs, which the leprechaun notes as he does grimy ones (the Wiki's Tool Leprechaun). */
        private val CLEAN_HERBS = listOf(249, 251, 253, 255, 257, 2998, 259, 261, 263, 3000, 30097, 265, 2481, 267, 269)

        /**
         * What a Tool Leprechaun notes: "any items ... collected from Farming, including clean or grimy herbs as well as
         * saplings" (the Wiki's Tool Leprechaun), so every crop's produce and sapling, the clean herbs, weeds and roots, but not logs
         * (a tree's produce here) nor baskets and sacks, which his list of exceptions names.
         */
        val NOTABLE: Set<Int> = buildSet {
            for (crop in Crops.ALL) if (crop.kind != PatchKind.TREE) add(crop.produce)
            for (tree in Trees.ALL) add(tree.sapling)
            addAll(CLEAN_HERBS)
            // Weeds and roots: the page's changes ("can now exchange weeds", "will now bank note tree roots").
            add(Farming.WEEDS)
            for (tree in Trees.ALL) if (tree.roots != 0) add(tree.roots)
        }

        /** How many of [payment]'s item [player] has, noted or not. */
        fun held(world: World, player: Player, payment: Payment): Long {
            val note = world.objTypes[payment.obj]?.certlink ?: -1
            return player.inventory.count(payment.obj) + if (note >= 0) player.inventory.count(note) else 0L
        }

        /** Takes [payment], unnoted first. */
        fun take(world: World, player: Player, payment: Payment) {
            val unnoted = minOf(player.inventory.count(payment.obj), payment.count.toLong()).toInt()
            if (unnoted > 0) removeCount(player.inventory, payment.obj, unnoted)
            val rest = payment.count - unnoted
            val note = world.objTypes[payment.obj]?.certlink ?: -1
            if (rest > 0 && note >= 0) removeCount(player.inventory, note, rest)
        }

        /** Takes [count] of [id] from [inventory], over as many slots as it takes. */
        fun removeCount(inventory: Inventory, id: Int, count: Int) {
            var left = count
            for ((slot, obj) in inventory.contents()) {
                if (left <= 0) break
                if (obj.id != id) continue
                left -= inventory.remove(slot, left)
            }
        }
    }
}

/** A Tool Leprechaun's answers to an item used on him (the Wiki's Transcript:Tool Leprechaun, read 2026-11-14). */
object NoteLines : Lines("tool_leprechaun_note", "Transcript:Tool Leprechaun") {
    val NO_NOTES = line("no_notes", "an item used on him that has a banknote but isn't grown")
    val NO_SUCH_NOTE = line("no_such_note", "an item used on him that has no banknote")
    val IS_NOTE = line("is_note", "a banknote used on him")
}
