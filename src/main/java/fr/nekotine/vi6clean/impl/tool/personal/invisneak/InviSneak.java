package fr.nekotine.vi6clean.impl.tool.personal.invisneak;

import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

public class InviSneak extends Tool{

	public InviSneak(ToolHandler<?> handler) {
		super(handler);
	}
	private boolean sneaking;
	
	private boolean revealed;

	public boolean isSneaking() {
		return sneaking;
	}

	public void setSneaking(boolean sneaking) {
		this.sneaking = sneaking;
	}

	public boolean isRevealed() {
		return revealed;
	}

	public void setRevealed(boolean revealed) {
		this.revealed = revealed;
	}
}
