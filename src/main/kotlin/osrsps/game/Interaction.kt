package osrsps.game

/**
 * Something the player clicked in the world and is walking to use: a loc ([osrsps.game.loc.LocInteraction]) or an NPC
 * ([osrsps.game.npc.NpcInteraction]). A player has at most one; a new click replaces it.
 */
interface Interaction
