package osrsps.testrunner;

/** A world tile, named as RuneLite's WorldPoint names it. */
final class Tile
{
	final int x;
	final int y;
	final int plane;

	Tile(int x, int y, int plane)
	{
		this.x = x;
		this.y = y;
		this.plane = plane;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Tile))
		{
			return false;
		}
		Tile t = (Tile) o;
		return x == t.x && y == t.y && plane == t.plane;
	}

	@Override
	public int hashCode()
	{
		return (x * 31 + y) * 31 + plane;
	}

	@Override
	public String toString()
	{
		return x + ", " + y + ", " + plane;
	}
}
