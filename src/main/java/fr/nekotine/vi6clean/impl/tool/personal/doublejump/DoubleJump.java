package fr.nekotine.vi6clean.impl.tool.personal.doublejump;

import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

public class DoubleJump extends Tool{
	
	public DoubleJump(ToolHandler<?> handler) {
		super(handler);
	}
	private boolean canDoubleJump;

	public boolean canDoubleJump() {
		return canDoubleJump;
	}
	
	public void setCanDoubleJump(boolean canDoubleJump) {
		this.canDoubleJump = canDoubleJump;
	}
}
