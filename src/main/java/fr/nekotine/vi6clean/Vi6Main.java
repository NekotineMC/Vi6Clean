package fr.nekotine.vi6clean;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.ioc.IIocProvider;
import fr.nekotine.core.ioc.IocProvider;
import fr.nekotine.core.logging.FormatingRemoteLogger;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.text.Text;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.MAP_Vi6;

public class Vi6Main extends JavaPlugin implements Listener{
	
	public static final Logger LOGGER = new FormatingRemoteLogger(Text.namedLoggerFormat("Vi6"));
	
	public static final IIocProvider IOC = new IocProvider();
	
	@Override
	public void onLoad() {
		super.onLoad();
		((FormatingRemoteLogger)LOGGER).setRemote(getLogger());
		NekotineCore.setupFor(this);
		NekotineCore.MODULES.load(MapModule.class);
		var commandGenerator = NekotineCore.MODULES.get(MapModule.class).getGenerator();
		commandGenerator.generateFor(MAP_Vi6.class);
		commandGenerator.register();
	}
	
	@Override
	public void onDisable() {
		EventUtil.unregister(this);
		var game = IOC.resolve(Vi6Game.class);
		if (game != null) {
			game.abort(getName(), null);
		}
		NekotineCore.MODULES.unloadAll();
		super.onDisable();
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		EventUtil.register(this, this);
		var game = new Vi6Game((failureEvt) -> LOGGER.log(Level.WARNING, "Une erreur est survenue lors de la phase Vi6Game", failureEvt.exception()));
		IOC.registerSingleton(game);
		game.setup();
	}
	
	@EventHandler
	public void onPlayerJoined(PlayerJoinEvent evt) {
		IOC.resolve(Vi6Game.class).addPlayer(evt.getPlayer());
	}
	
	@EventHandler
	public void onPlayerLeft(PlayerQuitEvent evt) {
		IOC.resolve(Vi6Game.class).removePlayer(evt.getPlayer());
	}
}
