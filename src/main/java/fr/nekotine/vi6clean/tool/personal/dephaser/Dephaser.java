package fr.nekotine.vi6clean.tool.personal.dephaser;

import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolHandler;

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
