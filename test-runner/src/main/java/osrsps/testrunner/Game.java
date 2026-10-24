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

	/** Puts the text in the chat input and presses Enter, as typing it would. */
	String sendChat(String text);

	/** Clicks "Walk here" on a tile in the loaded scene. */
	String walk(int x, int y);

	/** Clicks an option on the nearest NPC with this name (or id, when the name is null). */
	String npcOption(String name, int id, String option);

	/** The dialogue open in the chatbox, or null when none is. */
	Dialogue dialogue();

	/** Clicks "Click here to continue" on the open dialogue. */
	String continueDialogue();

	/** Clicks an option of the open option menu, by the slot {@link Dialogue#options} gives. */
	String chooseOption(int slot);
}
