package fr.nekotine.vi6clean;

import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.NekotinePlugin;
import fr.nekotine.core.eventguard.PlayerDoubleEventGuard;
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
		gameCommands();
		nekotinePlugin = builder.build();
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		Vi6Styles.load();
		var game = new Vi6Game();
		game.setMapName("SolarIndustries");
		Ioc.getProvider().registerSingleton(game);
		Ioc.getProvider().registerSingleton(new Majordom());
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		Ioc.resolve(ModuleManager.class).tryLoad(PlayerDoubleEventGuard.class);
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
	
	private void gameCommands() {
		var gameC = new CommandAPICommand("game");
		var sub = new CommandAPICommand("lobby").executes(e -> {
			Ioc.resolve(Vi6Game.class).start();
		}, ExecutorType.ALL);
		gameC.withSubcommand(sub);
		gameC.register();
	}
}
