package fr.nekotine.vi6clean.tool.personal.bush;

import org.bukkit.scheduler.BukkitTask;

import fr.nekotine.vi6clean.tool.Tool;
import fr.nekotine.vi6clean.tool.ToolHandler;

public class Bush extends Tool {

	public Bush(ToolHandler<?> handler) {
		super(handler);
	}

	private boolean inBush;

	private boolean revealed;

	private BukkitTask fadeOffTask;

	public boolean isInBush() {
		return inBush;
	}

	public void setInBush(boolean value) {
		inBush = value;
	}

	public boolean isRevealed() {
		return revealed;
	}

	public void setRevealed(boolean value) {
		revealed = value;
	}

	public BukkitTask getFadeOffTask() {
		return fadeOffTask;
	}

	public void setFadeOffTask(BukkitTask task) {
		fadeOffTask = task;
	}
}
