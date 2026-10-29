package osrsps.game.content

import osrsps.game.World

/**
 * Lumbridge's town centre outside the castle (Rest of Lumbridge step 5): the shop keepers' and Bob's talk, the Sheared
 * Ram, Donie and Gee, the skill tutors, Abigaila and the Ironman tutor. The men, women and rats in the town share the
 * castle's [registerTownsfolk]; the spawns are in npc-spawns/lumbridge.json.
 */
fun registerLumbridgeTown(world: World) {
    registerLumbridgeShopkeepers(world)
    registerShearedRam(world)
    registerDonieAndGee(world)
    registerLumbridgeTutors(world)
    registerLumbridgeTownsfolk(world)
}
