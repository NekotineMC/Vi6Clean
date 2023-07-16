package fr.nekotine.vi6clean;

import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.vi6clean.impl.map.MAP_Vi6;

public class Vi6Main extends JavaPlugin{
	
	private static Vi6Main main;
	
	/**
	 * Récupère la dernière instance de Vi6Main créée.
	 * Cette methode existe principalement pour permettre la création de NamespacedKey
	 * @return
	 */
	public static Vi6Main getOneVi6Main() {
		return main;
	}
	
	@Override
	public void onEnable() {
		main = this;
		super.onEnable();
	}
	
	@Override
	public void onLoad() {
		super.onLoad();
		NekotineCore.setupFor(this);
		//CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
		NekotineCore.MODULES.load(MapModule.class);
		var commandGenerator = NekotineCore.MODULES.get(MapModule.class).getGenerator();
		commandGenerator.generateFor(MAP_Vi6.class);
		commandGenerator.register();
	}
	
	@Override
	public void onDisable() {
		NekotineCore.MODULES.unloadAll();
		super.onDisable();
	}
}
