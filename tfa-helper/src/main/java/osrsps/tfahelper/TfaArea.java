package osrsps.tfahelper;

/**
 * The parts of The Fractured Archive the tracker tells apart, by the tile you stand on in the raid's map as the game's
 * cache has it (the template RuneLite gives back for an instance, {@code WorldPoint.fromLocalInstance}).
 *
 * <p>From our server's raid ({@code osrsps.game.raid.ArchiveRoom} and {@code ArchiveRaids}), which takes each room's
 * map square from the cache's locs: the six side rooms are whole map squares, the nexus is square 64,87 south of the
 * Archive Door north (z 5623), Ocellus's arena is the rest of that square and square 64,88, and the reward vault is
 * square 64,90. Inferred: that live's instance uses the same squares, as our server's copy of them was checked against
 * Sam's live recordings of 9 and 10 November 2026 room by room.
 */
public enum TfaArea
{
	NEXUS("Nexus", 64, 87, 5568, 5623),
	EZEKIEL("Ezekiel", 63, 86),
	BORK("Bork", 65, 87),
	MOIA("Moia", 63, 88),
	DREADNOTH("Dreadnoth", 65, 88),
	DAGONHAI("Dagon'hai", 63, 87),
	XARTH("Xarth", 65, 86),
	OCELLUS("Ocellus", 64, 87, 5623, 5696),
	VAULT("Vault", 64, 90);

	private final String label;
	private final int minX;
	private final int maxX;
	private final int minZ;
	/** Exclusive. */
	private final int maxZ;

	TfaArea(String label, int squareX, int squareZ)
	{
		this(label, squareX, squareZ, squareZ << 6, (squareZ + 1) << 6);
	}

	TfaArea(String label, int squareX, int squareZ, int minZ, int maxZ)
	{
		this.label = label;
		this.minX = squareX << 6;
		this.maxX = (squareX + 1) << 6;
		this.minZ = minZ;
		this.maxZ = maxZ;
	}

	/** The name the overlay and the chat summary show. */
	public String label()
	{
		return label;
	}

	boolean contains(int x, int z)
	{
		return x >= minX && x < maxX && z >= minZ && z < maxZ;
	}

	/**
	 * The area holding template tile {@code x}, {@code z}, or null outside the raid. Only meaningful inside an
	 * instance: outside one these coordinates are the static world's, where none of these squares is somewhere you
	 * stand, but the plugin checks for the instance anyway.
	 */
	public static TfaArea at(int x, int z)
	{
		for (TfaArea area : values())
		{
			if (area.contains(x, z))
			{
				return area;
			}
		}
		return null;
	}
}
