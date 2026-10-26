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

	/** Clicks "Walk here" on a tile in the loaded scene. */
	String walk(int x, int y);

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

	/** How many of the item (by name, or id when the name is null) the inventory holds; -1 while the client has none. */
	int inventoryCount(String name, int id);

	/** The dialogue open in the chatbox, or null when none is. */
	Dialogue dialogue();

	/** Clicks "Click here to continue" on the open dialogue. */
	String continueDialogue();

	/** Clicks an option of the open option menu, by the slot {@link Dialogue#options} gives. */
	String chooseOption(int slot);
}
