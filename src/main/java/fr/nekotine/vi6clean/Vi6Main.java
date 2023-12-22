package fr.nekotine.vi6clean;

import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.NekotinePlugin;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.setup.PluginBuilder;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.majordom.Majordom;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;

public class Vi6Main extends JavaPlugin{
	
	private NekotinePlugin nekotinePlugin;
	
	@Override
	public void onLoad() {
		super.onLoad();
		var builder = new PluginBuilder(this);
		builder.mapCommandsFor(Vi6Map.class);
		nekotinePlugin = builder.build();
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		Vi6Styles.load();
		var game = new Vi6Game();
		Ioc.getProvider().registerSingleton(game);
		Ioc.getProvider().registerSingleton(new Majordom());
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		var container = new ToolHandlerContainer();
		container.discoverHandlers();
		Ioc.getProvider().registerSingleton(container);
		game.start();
	}
	
	@Override
	public void onDisable() {
		var game = Ioc.resolve(Vi6Game.class);
		game.close();
		nekotinePlugin.disable();
		super.onDisable();
	}
}
