package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.audit.FlatCache
import osrsps.game.AllSettings
import osrsps.game.SettingDependency
import osrsps.game.SettingRow
import osrsps.game.SettingVar
import osrsps.game.VarTypes

class SettingsLoaderTest {
    @Test
    fun `reads an enum's int outputs and a struct's int params`() {
        val enum = bytes {
            writeByte(1); writeByte('i'.code)
            writeByte(2); writeByte('J'.code)
            writeByte(4); writeInt(-1)
            writeByte(6); writeShort(2); writeInt(0); writeInt(905); writeInt(1); writeInt(913)
            writeByte(0)
        }
        val decoded = SettingsLoader.decodeEnum(enum)
        assertEquals(mapOf(0 to 905, 1 to 913), decoded.outputs)
        assertEquals(2, decoded.count)
        assertEquals(-1, decoded.get(5))

        val struct = bytes {
            writeByte(249); writeByte(3)
            writeByte(0); writeMedium(1077); writeInt(7)
            writeByte(1); writeMedium(1086); writeBytes("Hide roofs"); writeByte(0)
            writeByte(0); writeMedium(1078); writeInt(0)
            writeByte(0)
        }
        assertEquals(mapOf(1077 to 7, 1078 to 0), SettingsLoader.decodeStructInts(struct))
    }

    @Test
    fun `reads each shape of getter case and calls the rest the client's`() {
        assertEquals(
            mapOf(
                1 to SettingVar.Varbit(100),
                2 to SettingVar.Varp(200),
                3 to SettingVar.Inverted(SettingVar.Varbit(101)),
                4 to SettingVar.Bit(102, 3),
                5 to SettingVar.Conditional(103, 1, SettingVar.Varbit(104)),
                6 to SettingVar.Client,
                7 to SettingVar.Mapped(SettingVar.Varbit(105), mapOf(1 to 1, 2 to 2), 0),
                8 to SettingVar.Offset(SettingVar.Varp(201), -1),
            ),
            SettingsLoader.decodeGetter(getterScript()) { if (it == 700) SettingsLoader.decodeEnum(intEnum(1 to 1, 2 to 2)) else null },
        )
        // An enum the cache hasn't leaves the case to the client.
        assertEquals(SettingVar.Client, SettingsLoader.decodeGetter(getterScript())[7])
    }

    @Test
    fun `builds categories, rows in order and their dependencies`() {
        val enums = mapOf(
            422 to intEnum(0 to 10, 1 to 11),
            423 to intEnum(0 to 10, 1 to 11),
            500 to intEnum(0 to 20, 1 to 21),
            501 to intEnum(0 to 22),
            // Row 21 requires row 20 to be 1.
            600 to intEnum(0 to 20),
            601 to intEnum(0 to 1),
            // Row 22's options.
            602 to intEnum(0 to 0, 1 to 1, 2 to 2),
        )
        val structs = mapOf(
            10 to structOf(743 to 0, 745 to 500),
            11 to structOf(743 to 1, 745 to 501),
            20 to structOf(1077 to 7, 1078 to 0),
            21 to structOf(1077 to 8, 1078 to 0, 1080 to 600, 1081 to 601),
            22 to structOf(1077 to 9, 1078 to 2, 1091 to 602),
        )
        val catalog = SettingsLoader.build(enums::get, structs::get, mapOf(SettingsLoader.TOGGLE_GETTER to getterScript())::get)
        assertEquals(listOf(0, 1), catalog.categories.map { it?.id })
        assertEquals(listOf(7, 8), catalog.category(0)!!.rows.map { it.id })
        assertEquals(listOf(9), catalog.category(1)!!.rows.map { it.id })
        assertEquals(SettingRow.KIND_DROPDOWN, catalog.category(1)!!.rows[0].kind)
        assertEquals(3, catalog.category(1)!!.rows[0].options)
        assertEquals(0, catalog.category(0)!!.rows[0].options)
        assertEquals(3, catalog.maxOptions)
        assertEquals(listOf(SettingDependency(20, 1)), catalog.rows.getValue(21).requires)
        assertEquals(3, catalog.slotCount)
        assertEquals(SettingVar.Varbit(100), catalog.toggles[1])
    }

    /**
     * The real 241 settings, when given a checkout of github.com/abextm/osrs-cache at a rev 241 tag (2.flatcache and
     * 12.flatcache) in OSRSPS_FLATCACHE. Not in CI. The ids are the rev 241 dump's (Joshua-F/osrs-dumps, 2026-10-28).
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the live cache's settings`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val enums = cache.group(2, 8)
        val structs = cache.group(2, 34)
        val catalog = SettingsLoader.build(enums::get, structs::get) { cache.group(12, it)[0] }
        // Activities, Audio, Chat, Controls, Display, Gameplay, Interfaces, Warnings, Popout, Leagues.
        assertEquals((0..9).toList(), catalog.categories.map { it?.id })
        assertEquals(listOf(88, 21, 61, 46, 18, 53, 151, 61, 37, 3), catalog.categories.map { it!!.rows.size })
        assertEquals(1108, catalog.categories.last()!!.struct)
        assertEquals(324, catalog.toggles.size)
        assertEquals(SettingVar.Varbit(12378), catalog.toggles[7]) // option_hide_rooftops
        assertEquals(SettingVar.Varbit(4084), catalog.toggles[8]) // orbs_disabled
        assertEquals(SettingVar.Varp(1074), catalog.toggles[37]) // option_chatfilter_disabled
        assertEquals(SettingVar.Inverted(SettingVar.Varbit(14194)), catalog.toggles[278]) // option_item_retrieval_warning_disabled
        assertEquals(SettingVar.Bit(11959, 0), catalog.toggles[85]) // option_collection_new_item
        assertEquals(SettingVar.Conditional(12454, 1, SettingVar.Varbit(12457)), catalog.toggles[162]) // ca_refailure_notifications_enabled
        assertEquals(SettingVar.Client, catalog.toggles[241]) // full screen, a device option
        assertEquals(listOf(241), catalog.toggles.filterValues { it == SettingVar.Client }.keys.toList())
        assertEquals(SettingVar.Varbit(6374), catalog.dropdowns[1]) // chatbox_scrollbarside
        assertEquals(SettingVar.Varp(1107), catalog.dropdowns[55]) // option_attackpriority
        assertEquals(SettingVar.Client, catalog.dropdowns[44]) // loginlogout_setting_get, hand-coded in AllSettings
        // The F-key binds, through enum 1161 (1 to 13 as themselves): combat stone_combat_key and so on.
        val f = (1..13).associateWith { it }
        assertEquals((16..29).toList() + 238, catalog.keybinds.keys.sorted())
        assertEquals(SettingVar.Mapped(SettingVar.Varbit(4675), f, 0), catalog.keybinds[16])
        assertEquals(SettingVar.Mapped(SettingVar.Varbit(6517), f, 0), catalog.keybinds[26])
        // Enum 3506: None, F1 to F12, Esc.
        assertEquals(14, catalog.rows.getValue(2739).options)
        assertEquals(12, catalog.rows.getValue(3107).options) // quest list sort
        // Number inputs: a varbit or a varp each.
        assertEquals(SettingVar.Varbit(5400), catalog.numbers[39]) // option_lootnotification_value
        assertEquals(SettingVar.Varp(3800), catalog.numbers[328]) // ground_items_t5_threshold
        // Colours: 50 cases, each a varp holding the colour plus 1.
        assertEquals(50, catalog.colours.size)
        assertEquals(SettingVar.Offset(SettingVar.Varp(2992), -1), catalog.colours[87]) // option_chat_colour_public_opaque
        assertEquals(emptyList(), catalog.colours.filterValues { it !is SettingVar.Offset }.keys.toList())
        val varbits = cache.group(2, 14).mapValues { VarTypeLoader.decodeVarbit(it.value) }
        assertEquals(emptyList(), AllSettings.checkVarbits(VarTypes(6_000, varbits)))
    }

    /**
     * `iload 0; switch; jump end` then one case per shape: get_varbit; get_varp; 1 - get_varbit; testbit;
     * a conditional; a device option; an enum lookup; get_varp - 1. Cases 1 to 8 in that order.
     */
    private fun getterScript(): ByteArray = bytes {
        writeByte(0)
        fun op(opcode: Int, operand: Int = 0) {
            writeShort(opcode)
            if (opcode < 100 && opcode != 21) writeInt(operand) else writeByte(operand)
        }
        op(33); op(60); op(6, 21) // 0..2
        op(25, 100); op(21) // 3
        op(1, 200); op(21) // 5
        op(0, 1); op(25, 101); op(4001); op(21) // 7
        op(25, 102); op(0, 3); op(4010); op(21) // 11
        op(25, 103); op(0, 1); op(8, 1); op(6, 5); op(25, 104); op(21) // 15
        op(0, 17); op(3214); op(21) // 21
        op(0, 0); op(21) // 24
        op(0, 105); op(0, 105); op(0, 700); op(25, 105); op(3408); op(21) // 26
        op(1, 201); op(0, 1); op(4001); op(21) // 32
        writeInt(36)
        repeat(6) { writeShort(if (it == 3) 1 else 0) }
        val targets = listOf(3, 5, 7, 11, 15, 21, 26, 32)
        writeByte(1); writeShort(targets.size)
        targets.forEachIndexed { index, target -> writeInt(index + 1); writeInt(target - 2) }
        writeShort(1 + 2 + 8 * targets.size)
    }

    private fun intEnum(vararg entries: Pair<Int, Int>): ByteArray = bytes {
        writeByte(6); writeShort(entries.size)
        for ((key, value) in entries) { writeInt(key); writeInt(value) }
        writeByte(0)
    }

    private fun structOf(vararg params: Pair<Int, Int>): ByteArray = bytes {
        writeByte(249); writeByte(params.size)
        for ((key, value) in params) { writeByte(0); writeMedium(key); writeInt(value) }
        writeByte(0)
    }

    private fun DataOutputStream.writeMedium(value: Int) {
        writeByte(value shr 16); writeByte(value shr 8); writeByte(value)
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use(block) }.toByteArray()
}
