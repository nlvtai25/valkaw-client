package com.valkaw.client.module;

/** Side of another HUD element a module is attached to. */
public enum Link {
	NONE, RIGHT, LEFT, BELOW, ABOVE;

	public static Link parse(String name)
	{
		for (Link link : values())
		{
			if (link.name().equalsIgnoreCase(name))
			{
				return link;
			}
		}
		return NONE;
	}

	/** True when the module sits beside its parent instead of above or below it. */
	public boolean horizontal()
	{
		return this == RIGHT || this == LEFT;
	}
}
