package fr.nekotine.vi6clean.impl.tool.personal.dephaser;

import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

public class Dephaser extends Tool {

	public Dephaser(ToolHandler<?> handler) {
		super(handler);
	}

	private boolean active;

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean value) {
		active = value;
	}
}
