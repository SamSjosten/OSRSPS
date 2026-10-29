package osrsps.testrunner;

/**
 * What a test step can see and do in the client. The plugin implements it with RuneLite's API; tests use a fake.
 * Every method is called on the client thread. Actions return null when sent, or why they could not be sent.
 */
interface Game
{
	boolean loggedIn();

	/** The logged in player's name, or null when there is none. */
	String localName();

	/** The player's tile, or null when there is no player. */
	Tile location();

	/**
	 * The tile the client draws the player on, or null when there is no player. It trails {@link #location} while the
	 * player walks; any other difference means the client shows the player somewhere the server doesn't have them.
	 */
	Tile drawnLocation();

	/** Puts the text in the chat input and presses Enter, as typing it would. */
	String sendChat(String text);

	/**
	 * True until the last {@link #sendChat} has gone: its Enter key has been delivered and the client has taken the
	 * text out of the chat input. Until then a second line would overwrite the first.
	 */
	boolean chatPending();

	/**
	 * Presses Enter once more for a line the client has left in its chat input, asking for the canvas's keyboard focus
	 * first when it lacks it. Returns a note on the focus for the result file.
	 */
	String pressEnterAgain();

	/** Clicks "Walk here" on a tile in the loaded scene. */
	String walk(int x, int y);

	/** The tile the client's walk flag marks, where the player is heading, or null when there is none. */
	Tile walkDestination();

	/** Clicks an option on the nearest NPC with this name (or id, when the name is null). Examine works on any NPC. */
	String npcOption(String name, int id, String option);

	/**
	 * Clicks an option on the nearest loc (a door, stairs, a bookcase) with this name (or id, when the name is null) on
	 * the player's plane. With a tile, only a loc on that tile counts. Examine works on any loc.
	 */
	String locOption(String name, int id, String option, Tile at);

	/**
	 * Null when a loc with this name (or id, when the name is null) is on the tile, on the player's plane, and has the
	 * option when one is given; otherwise what is there instead.
	 */
	String locAt(String name, int id, String option, Tile at);

	/**
	 * Clicks Take on the nearest item on the ground with this name (or id, when the name is null) on the player's plane.
	 * With a tile, only an item on that tile counts.
	 */
	String takeItem(String name, int id, Tile at);

	/**
	 * Clicks ground op [op] (1 to 5; Take is 3), named [option] in the menu, on the nearest item on the ground with this
	 * name (or id) on the player's plane, on the tile when given.
	 */
	String groundItemOption(String name, int id, int op, String option, Tile at);

	/** Uses the first such item in the inventory on the first other slot holding the second item, as Use then a click does. */
	String useItemOnItem(String name, int id, String onName, int onId);

	/** How many of the item (by name, or id when the name is null) the inventory holds; -1 while the client has none. */
	int inventoryCount(String name, int id);

	/** The dialogue open in the chatbox, or null when none is. */
	Dialogue dialogue();

	/** Clicks "Click here to continue" on the open dialogue. */
	String continueDialogue();

	/** Clicks an option of the open option menu, by the slot {@link Dialogue#options} gives. */
	String chooseOption(int slot);

	/**
	 * The NPC an assert means by a name (or id, when the name is null): the one the player is interacting with (fighting
	 * or talking to) when it matches, else the nearest matching one on the player's plane. Its index, or -1 when none is
	 * in view.
	 */
	int npcIndex(String name, int id);

	/** The health bar of the NPC {@link #npcIndex} would pick, or null when no such NPC is in view. */
	Health npcHealth(String name, int id);

	/** What the client shows of an actor's health: a bar of {@link #scale} units with {@link #ratio} filled, or none. */
	final class Health
	{
		/** -1 while the NPC shows no bar (the client is not told its health until it is hit). */
		final int ratio;
		final int scale;

		Health(int ratio, int scale)
		{
			this.ratio = ratio;
			this.scale = scale;
		}

		/** The bar as a percentage, or -1 without one. */
		int percent()
		{
			return ratio < 0 || scale <= 0 ? -1 : ratio * 100 / scale;
		}

		@Override
		public String toString()
		{
			return ratio < 0 ? "no health bar" : percent() + "% (" + ratio + "/" + scale + ")";
		}
	}

	/**
	 * Varp {@code id} twice: as the server last sent it, then as the client holds it now (the client's own scripts
	 * change their copy, as the settings do before telling the server). Null when logged out or the client has no such
	 * varp.
	 */
	int[] varp(int id);

	/** Varbit {@code id} worked out from both copies of its varp, as {@link #varp} gives them; null for one the client lacks. */
	int[] varbit(int id);

	/** The carried weight in whole kilograms as the server last sent it (UPDATE_RUNWEIGHT); {@link Integer#MIN_VALUE} when logged out. */
	int weight();

	/** The player's real (unboosted) level in the skill, as the stats tab shows it; -1 for a skill the client doesn't know. */
	int skillLevel(String skill);

	/** The player's experience in the skill; -1 for a skill the client doesn't know. */
	int skillXp(String skill);

	/**
	 * Null when an item with this name (or id, when the name is null) lies on the ground in the scene on the player's
	 * plane, on the tile when one is given; otherwise what is missing.
	 */
	String groundItem(String name, int id, Tile at);

	/** Whether the tile is in the loaded scene, so the client can see what lies on it. */
	boolean tileLoaded(Tile at);

	/** Whether the interface (by its id) is open and shown anywhere in the client's frame. */
	boolean interfaceOpen(int interfaceId);

	/**
	 * Clicks an option (such as Wield, Wear, Eat or Drop, or Examine) on the first item in the inventory with this name
	 * (or id, when the name is null).
	 */
	String itemOption(String name, int id, String option);

	/**
	 * The item worn in a slot (a wearpos, 0 for the head to 13 for ammo), as the worn inventory has it: its id, or -1 when
	 * the slot is empty. A client that has had nothing worn since login has no worn inventory at all (the client makes
	 * one only once an item is in it), which is every slot empty.
	 */
	int wornId(int wearpos);

	/**
	 * The item the player's model shows in a slot: its id, -1 for none, or {@link #NOT_ON_MODEL} for a slot the model
	 * doesn't show (ring, ammo).
	 */
	int modelWornId(int wearpos);

	int NOT_ON_MODEL = -3;

	/** The item's name, as the client has it, or null for an unknown id. */
	String itemName(int id);

	/** Clicks an option (Remove, Examine or one of the item's own) on the item worn in a slot of the equipment tab. */
	String wornOption(int wearpos, String option);

	/**
	 * Clicks op {@code op} (1 to 10) on an interface component, or on slot {@code sub} of it when {@code sub} is 0 or more,
	 * as a click on a menu entry of that component would.
	 */
	String clickWidget(int interfaceId, int component, int sub, int op);

	/**
	 * Runs client script {@code id} from the cache with {@code args} (Integers and Strings, in the script's order), as
	 * the client's own interfaces would: what the script sends the server goes out as it would from a click.
	 */
	String runClientScript(int id, Object[] args);

	/**
	 * Clicks the left mouse button on the middle of an interface component, or of its slot {@code sub} when that is 0 or
	 * more, for what only reacts to the mouse itself (a dropdown's box opens on the click, not on a menu option).
	 */
	String mouseClickWidget(int interfaceId, int component, int sub);

	/**
	 * Picks a spell (any component that targets, as the spellbook's spells do) at {@code interfaceId}:{@code component}
	 * and uses it on the nearest NPC with this name (or id, when the name is null), as clicking the spell and then
	 * "Cast ... -> NPC" would. Null when both clicks were made; otherwise what is missing.
	 */
	String castOnNpc(int interfaceId, int component, String npc, int npcId);

	/**
	 * The text of a component and everything inside it, or of the whole interface when {@code component} is -1, with
	 * colour tags removed and the pieces joined by " / "; null while the interface isn't open (loaded) in the client.
	 */
	String widgetText(int interfaceId, int component);

	/** The animation the player stands in (the stance's ready pose), or -1 without a player. */
	int idlePose();

	/** Clicks "Click here to logout" in the logout tab. */
	String logout();
}
