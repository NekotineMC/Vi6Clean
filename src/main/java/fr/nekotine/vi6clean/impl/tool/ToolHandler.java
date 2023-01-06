package fr.nekotine.vi6clean.impl.tool;

import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.event.Listener;

import fr.nekotine.core.game.Game;
import fr.nekotine.vi6clean.impl.game.GD_Vi6;
import fr.nekotine.vi6clean.impl.tool.exception.InvalidToolException;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;

/**
 * Gestionnaire d'outil pour une partie de Vi6.
 * Une instance par partie.
 * L'objectif de cette classe est de fournir le comportemant attendu à tous les outils d'un même type.
 * @author XxGoldenbluexX
 *
 * @param <T>
 */
public abstract class ToolHandler<T extends Tool> implements Listener{

	private final Constructor<T> toolConstructor;
	
	private List<T> tools = new LinkedList<>();
	
	public ToolHandler(Class<T> toolType) {
		try {
			toolConstructor = toolType.getConstructor();
			toolConstructor.trySetAccessible();
		}catch(Exception e) {
			throw new InvalidToolException("Une erreur est survenue lors de la recuperation du constructeur pour l'outil.",e);
		}
	}

	/**
	 * Mise en place au lancement de la phase d'infiltration.
	 * Au moment de cet appel, ce gestionnaire viens d'être ajouté comme {@link org.bukkit.event.Listener Listener bukkit}.
	 * @param game
	 */
	public abstract void enableGlobal(Game<GD_Vi6> game);
	
	/**
	 * Nettoyage a la fin de la phase d'infiltration.
	 * Au moment de cet appel, ce gestionnaire viens d'être retiré comme {@link org.bukkit.event.Listener Listener bukkit}.
	 * @param game
	 */
	public abstract void disableGlobal(Game<GD_Vi6> game);
	
	/**
	 * Activation de l'outil (par exemple quand le joueur entre dans la map).
	 * L'ItemStack représentant l'outil est déja dans l'inventaire du joueur a ce moment.
	 * @param tool
	 */
	protected abstract void enableTool(T tool, PlayerWrapper wrapper);
	
	/**
	 * Activation de l'outil (par exemble quand le joueur sort de la map)
	 * @param tool
	 */
	protected abstract void disableTool(T tool, PlayerWrapper wrapper);
	
	/**
	 * Crée un nouvel outil.
	 * @return
	 */
	protected final T makeNewTool() {
		try {
			return toolConstructor.newInstance();
		}catch(Exception e) {
		throw new InvalidToolException("Une erreur est survenue lors de la creation de l'outil via son constructeur.", e);
		}
	};
	
	public final void attachNewToolToPlayer(PlayerWrapper wrapper) {
		var tool = makeNewTool();
		tools.add(tool);
		wrapper.GetWrapped().getInventory().addItem(tool.getItemStack());
		enableTool(tool, wrapper);
	}
	
	public final void detachToolFromPlayer(T tool, PlayerWrapper wrapper) {
		tools.remove(tool);
		disableTool(tool, wrapper);
	}
	
	public List<T> getToolList(){
		return tools;
	}
}
