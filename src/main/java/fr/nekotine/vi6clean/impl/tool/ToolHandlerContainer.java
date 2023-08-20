package fr.nekotine.vi6clean.impl.tool;
/*
import java.util.LinkedList;
import java.util.List;

import fr.nekotine.core.game.phase.Game;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.game.GD_Vi6;

public class ToolHandlerContainer {

	private List<ToolHandler<?>> toolHandlers = new LinkedList<ToolHandler<?>>();
	
	/**
	 * Ajoute le gestionnaire d'outil si aucun du même type est déja présent.
	 * @param handler
	 * @return
	 *//*
	public boolean registerHandler(ToolHandler<?> handler) {
		if (toolHandlers.stream().anyMatch(th -> th.getClass() == handler.getClass())) {
			return false;
		}
		toolHandlers.add(handler);
		return true;
	}
	
	public List<ToolHandler<?>> getToolList(){
		return toolHandlers;
	}
	
	public void enableToolHandlers(Game<GD_Vi6> game) {
		for (var handler : toolHandlers) {
			handler.enableGlobal(game);
			EventUtil.register(game.getGameMode().getPlugin(), handler);
		}
	}
	
	public void disableToolHandlers(Game<GD_Vi6> game) {
		for (var handler : toolHandlers) {
			EventUtil.unregister(handler);
			handler.disableGlobal(game);
		}
	}
	
}
*/