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
import fr.nekotine.vi6clean.impl.map.Vi6Map;

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
		commandGenerator.generateFor(Vi6Map.class);
		commandGenerator.register();
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		var game = new Vi6Game((failureEvt) -> 
			LOGGER.log(Level.WARNING, "Une erreur est survenue lors de la phase Vi6Game: "+failureEvt.info(),failureEvt.exception())
			);
		IOC.registerSingleton(game);
		try {
			game.setup();
		}catch(Exception e){
			LOGGER.log(Level.SEVERE, "Une erreur est survenue lor du chargement de la game (methode \"setup\")", e);
		}
		EventUtil.register(this, this);
	}
	
	@Override
	public void onDisable() {
		EventUtil.unregister(this);
		var game = IOC.resolve(Vi6Game.class);
		if (game != null && game.isRunning()) {
			game.abort("Désactivation du plugin", null);
		}
		NekotineCore.MODULES.unloadAll();
		super.onDisable();
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
