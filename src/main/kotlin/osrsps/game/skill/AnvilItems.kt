package osrsps.game.skill

/**
 * One item on the anvil (interface smithing, 312): its component, the obj made, the level, the bars it takes and how
 * many it makes. As the client's own smithing_setup lays them out for each bar, with the level, bars and count from its
 * enums 846, 845 and 844 (cache 241, read 2026-10-29); an enum 846 level of 0 is level 1.
 */
class AnvilItem(val component: Int, val obj: Int, val level: Int, val bars: Int, val count: Int)

/** Every bar's anvil items, by the bar's obj id. Lovakite's Shayzien armour is left out (out of scope). */
object AnvilItems {
    val BY_BAR: Map<Int, List<AnvilItem>> = mapOf(
        Smithing.BRONZE.obj to listOf(
            AnvilItem(9, 1205, 1, 1, 1), // bronze_dagger
            AnvilItem(10, 1277, 4, 1, 1), // bronze_sword
            AnvilItem(11, 1321, 5, 2, 1), // bronze_scimitar
            AnvilItem(12, 1291, 6, 2, 1), // bronze_longsword
            AnvilItem(13, 1307, 14, 3, 1), // bronze_2h_sword
            AnvilItem(14, 1351, 1, 1, 1), // bronze_axe
            AnvilItem(15, 1422, 2, 1, 1), // bronze_mace
            AnvilItem(16, 1337, 9, 3, 1), // bronze_warhammer
            AnvilItem(17, 1375, 10, 3, 1), // bronze_battleaxe
            AnvilItem(18, 3095, 13, 2, 1), // bronze_claws
            AnvilItem(19, 1103, 11, 3, 1), // bronze_chainbody
            AnvilItem(20, 1075, 16, 3, 1), // bronze_platelegs
            AnvilItem(21, 1087, 16, 3, 1), // bronze_plateskirt
            AnvilItem(22, 1117, 18, 5, 1), // bronze_platebody
            AnvilItem(23, 4819, 4, 1, 15), // nails_bronze
            AnvilItem(24, 1139, 3, 1, 1), // bronze_med_helm
            AnvilItem(25, 1155, 7, 2, 1), // bronze_full_helm
            AnvilItem(26, 1173, 8, 2, 1), // bronze_sq_shield
            AnvilItem(27, 1189, 12, 3, 1), // bronze_kiteshield
            AnvilItem(29, 819, 4, 1, 10), // bronze_dart_tip
            AnvilItem(30, 39, 5, 1, 15), // bronze_arrowheads
            AnvilItem(31, 864, 7, 1, 5), // bronze_knife
            AnvilItem(32, 1794, 4, 1, 1), // bronzecraftwire
            AnvilItem(34, 9375, 3, 1, 10), // xbows_crossbow_bolts_bronze_unfeathered
            AnvilItem(35, 9420, 6, 1, 1), // xbows_crossbow_limbs_bronze
            AnvilItem(33, 19570, 6, 1, 5), // bronze_javelin_head
            AnvilItem(36, 31999, 10, 5, 1), // sailing_boat_keel_part_bronze
        ),
        Smithing.IRON.obj to listOf(
            AnvilItem(9, 1203, 15, 1, 1), // iron_dagger
            AnvilItem(10, 1279, 19, 1, 1), // iron_sword
            AnvilItem(11, 1323, 20, 2, 1), // iron_scimitar
            AnvilItem(12, 1293, 21, 2, 1), // iron_longsword
            AnvilItem(13, 1309, 29, 3, 1), // iron_2h_sword
            AnvilItem(14, 1349, 16, 1, 1), // iron_axe
            AnvilItem(15, 1420, 17, 1, 1), // iron_mace
            AnvilItem(16, 1335, 24, 3, 1), // iron_warhammer
            AnvilItem(17, 1363, 25, 3, 1), // iron_battleaxe
            AnvilItem(18, 3096, 28, 2, 1), // iron_claws
            AnvilItem(19, 1101, 26, 3, 1), // iron_chainbody
            AnvilItem(20, 1067, 31, 3, 1), // iron_platelegs
            AnvilItem(21, 1081, 31, 3, 1), // iron_plateskirt
            AnvilItem(22, 1115, 33, 5, 1), // iron_platebody
            AnvilItem(23, 4820, 19, 1, 15), // nails_iron
            AnvilItem(24, 1137, 18, 1, 1), // iron_med_helm
            AnvilItem(25, 1153, 22, 2, 1), // iron_full_helm
            AnvilItem(26, 1175, 23, 2, 1), // iron_sq_shield
            AnvilItem(27, 1191, 27, 3, 1), // iron_kiteshield
            AnvilItem(28, 4540, 26, 1, 1), // oil_lantern_frame
            AnvilItem(29, 820, 19, 1, 10), // iron_dart_tip
            AnvilItem(30, 40, 20, 1, 15), // iron_arrowheads
            AnvilItem(31, 863, 22, 1, 5), // iron_knife
            AnvilItem(32, 7225, 17, 1, 1), // spit_iron
            AnvilItem(34, 9377, 18, 1, 10), // xbows_crossbow_bolts_iron_unfeathered
            AnvilItem(35, 9423, 23, 1, 1), // xbows_crossbow_limbs_iron
            AnvilItem(33, 19572, 21, 1, 5), // iron_javelin_head
            AnvilItem(36, 32002, 22, 5, 1), // sailing_boat_keel_part_iron
        ),
        Smithing.STEEL.obj to listOf(
            AnvilItem(9, 1207, 30, 1, 1), // steel_dagger
            AnvilItem(10, 1281, 34, 1, 1), // steel_sword
            AnvilItem(11, 1325, 35, 2, 1), // steel_scimitar
            AnvilItem(12, 1295, 36, 2, 1), // steel_longsword
            AnvilItem(13, 1311, 44, 3, 1), // steel_2h_sword
            AnvilItem(14, 1353, 31, 1, 1), // steel_axe
            AnvilItem(15, 1424, 32, 1, 1), // steel_mace
            AnvilItem(16, 1339, 39, 3, 1), // steel_warhammer
            AnvilItem(17, 1365, 40, 3, 1), // steel_battleaxe
            AnvilItem(18, 3097, 43, 2, 1), // steel_claws
            AnvilItem(19, 1105, 41, 3, 1), // steel_chainbody
            AnvilItem(20, 1069, 46, 3, 1), // steel_platelegs
            AnvilItem(21, 1083, 46, 3, 1), // steel_plateskirt
            AnvilItem(22, 1119, 48, 5, 1), // steel_platebody
            AnvilItem(23, 1539, 34, 1, 15), // nails
            AnvilItem(24, 1141, 33, 1, 1), // steel_med_helm
            AnvilItem(25, 1157, 37, 2, 1), // steel_full_helm
            AnvilItem(26, 1177, 38, 2, 1), // steel_sq_shield
            AnvilItem(27, 1193, 42, 3, 1), // steel_kiteshield
            AnvilItem(28, 4544, 49, 1, 1), // bullseye_lantern_nolens
            AnvilItem(29, 821, 34, 1, 10), // steel_dart_tip
            AnvilItem(30, 41, 35, 1, 15), // steel_arrowheads
            AnvilItem(31, 865, 37, 1, 5), // steel_knife
            AnvilItem(32, 2370, 36, 1, 1), // studs
            AnvilItem(34, 9378, 33, 1, 10), // xbows_crossbow_bolts_steel_unfeathered
            AnvilItem(35, 9425, 36, 1, 1), // xbows_crossbow_limbs_steel
            AnvilItem(33, 19574, 36, 1, 5), // steel_javelin_head
            AnvilItem(36, 32005, 38, 5, 1), // sailing_boat_keel_part_steel
            AnvilItem(37, 32886, 34, 1, 1), // chain
        ),
        Smithing.MITHRIL.obj to listOf(
            AnvilItem(9, 1209, 50, 1, 1), // mithril_dagger
            AnvilItem(10, 1285, 54, 1, 1), // mithril_sword
            AnvilItem(11, 1329, 55, 2, 1), // mithril_scimitar
            AnvilItem(12, 1299, 56, 2, 1), // mithril_longsword
            AnvilItem(13, 1315, 64, 3, 1), // mithril_2h_sword
            AnvilItem(14, 1355, 51, 1, 1), // mithril_axe
            AnvilItem(15, 1428, 52, 1, 1), // mithril_mace
            AnvilItem(16, 1343, 59, 3, 1), // mithril_warhammer
            AnvilItem(17, 1369, 60, 3, 1), // mithril_battleaxe
            AnvilItem(18, 3099, 63, 2, 1), // mithril_claws
            AnvilItem(19, 1109, 61, 3, 1), // mithril_chainbody
            AnvilItem(20, 1071, 66, 3, 1), // mithril_platelegs
            AnvilItem(21, 1085, 66, 3, 1), // mithril_plateskirt
            AnvilItem(22, 1121, 68, 5, 1), // mithril_platebody
            AnvilItem(23, 4822, 54, 1, 15), // nails_mithril
            AnvilItem(24, 1143, 53, 1, 1), // mithril_med_helm
            AnvilItem(25, 1159, 57, 2, 1), // mithril_full_helm
            AnvilItem(26, 1181, 58, 2, 1), // mithril_sq_shield
            AnvilItem(27, 1197, 62, 3, 1), // mithril_kiteshield
            AnvilItem(29, 822, 54, 1, 10), // mithril_dart_tip
            AnvilItem(30, 42, 55, 1, 15), // mithril_arrowheads
            AnvilItem(31, 866, 57, 1, 5), // mithril_knife
            AnvilItem(34, 9379, 53, 1, 10), // xbows_crossbow_bolts_mithril_unfeathered
            AnvilItem(35, 9427, 56, 1, 1), // xbows_crossbow_limbs_mithril
            AnvilItem(33, 19576, 56, 1, 5), // mithril_javelin_head
            AnvilItem(32, 9416, 59, 1, 1), // xbows_grapple_tip_mithril
            AnvilItem(36, 32008, 56, 5, 1), // sailing_boat_keel_part_mithril
        ),
        Smithing.ADAMANTITE.obj to listOf(
            AnvilItem(9, 1211, 70, 1, 1), // adamant_dagger
            AnvilItem(10, 1287, 74, 1, 1), // adamant_sword
            AnvilItem(11, 1331, 75, 2, 1), // adamant_scimitar
            AnvilItem(12, 1301, 76, 2, 1), // adamant_longsword
            AnvilItem(13, 1317, 84, 3, 1), // adamant_2h_sword
            AnvilItem(14, 1357, 71, 1, 1), // adamant_axe
            AnvilItem(15, 1430, 72, 1, 1), // adamant_mace
            AnvilItem(16, 1345, 79, 3, 1), // adamnt_warhammer
            AnvilItem(17, 1371, 80, 3, 1), // adamant_battleaxe
            AnvilItem(18, 3100, 83, 2, 1), // adamant_claws
            AnvilItem(19, 1111, 81, 3, 1), // adamant_chainbody
            AnvilItem(20, 1073, 86, 3, 1), // adamant_platelegs
            AnvilItem(21, 1091, 86, 3, 1), // adamant_plateskirt
            AnvilItem(22, 1123, 88, 5, 1), // adamant_platebody
            AnvilItem(23, 4823, 74, 1, 15), // nails_adamant
            AnvilItem(24, 1145, 73, 1, 1), // adamant_med_helm
            AnvilItem(25, 1161, 77, 2, 1), // adamant_full_helm
            AnvilItem(26, 1183, 78, 2, 1), // adamant_sq_shield
            AnvilItem(27, 1199, 82, 3, 1), // adamant_kiteshield
            AnvilItem(29, 823, 74, 1, 10), // adamant_dart_tip
            AnvilItem(30, 43, 75, 1, 15), // adamant_arrowheads
            AnvilItem(31, 867, 77, 1, 5), // adamant_knife
            AnvilItem(34, 9380, 73, 1, 10), // xbows_crossbow_bolts_adamantite_unfeathered
            AnvilItem(35, 9429, 76, 1, 1), // xbows_crossbow_limbs_adamantite
            AnvilItem(32, 19578, 76, 1, 5), // adamant_javelin_head
            AnvilItem(36, 32011, 74, 5, 1), // sailing_boat_keel_part_adamant
        ),
        Smithing.RUNITE.obj to listOf(
            AnvilItem(9, 1213, 85, 1, 1), // rune_dagger
            AnvilItem(10, 1289, 89, 1, 1), // rune_sword
            AnvilItem(11, 1333, 90, 2, 1), // rune_scimitar
            AnvilItem(12, 1303, 91, 2, 1), // rune_longsword
            AnvilItem(13, 1319, 99, 3, 1), // rune_2h_sword
            AnvilItem(14, 1359, 86, 1, 1), // rune_axe
            AnvilItem(15, 1432, 87, 1, 1), // rune_mace
            AnvilItem(16, 1347, 94, 3, 1), // rune_warhammer
            AnvilItem(17, 1373, 95, 3, 1), // rune_battleaxe
            AnvilItem(18, 3101, 98, 2, 1), // rune_claws
            AnvilItem(19, 1113, 96, 3, 1), // rune_chainbody
            AnvilItem(20, 1079, 99, 3, 1), // rune_platelegs
            AnvilItem(21, 1093, 99, 3, 1), // rune_plateskirt
            AnvilItem(22, 1127, 99, 5, 1), // rune_platebody
            AnvilItem(23, 4824, 89, 1, 15), // nails_rune
            AnvilItem(24, 1147, 88, 1, 1), // rune_med_helm
            AnvilItem(25, 1163, 92, 2, 1), // rune_full_helm
            AnvilItem(26, 1185, 93, 2, 1), // rune_sq_shield
            AnvilItem(27, 1201, 97, 3, 1), // rune_kiteshield
            AnvilItem(29, 824, 89, 1, 10), // rune_dart_tip
            AnvilItem(30, 44, 90, 1, 15), // rune_arrowheads
            AnvilItem(31, 868, 92, 1, 5), // rune_knife
            AnvilItem(34, 9381, 88, 1, 10), // xbows_crossbow_bolts_runite_unfeathered
            AnvilItem(35, 9431, 91, 1, 1), // xbows_crossbow_limbs_runite
            AnvilItem(32, 19580, 91, 1, 5), // rune_javelin_head
            AnvilItem(36, 32014, 86, 5, 1), // sailing_boat_keel_part_rune
        ),
    )
}
