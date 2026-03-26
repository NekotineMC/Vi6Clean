package fr.nekotine.vi6clean;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.NekotinePlugin;
import fr.nekotine.core.eventguard.PlayerDoubleEventGuard;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.majordom.Majordom;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;
import fr.nekotine.vi6clean.voicechat.Vi6VoiceChatPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Vi6Main extends NekotinePlugin{
	
	private final ComponentLogger logger = NekotineLogger.make();
	
	@Override
	public void onLoad() {
		super.onLoad();
		mapCommandsFor(Vi6Map.class);
		gameCommands();
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		try {
			var vc_service = getServer().getServicesManager().load(BukkitVoicechatService.class);
			if (vc_service != null) {
				var vc_plugin = new Vi6VoiceChatPlugin();
				Ioc.getProvider().registerSingleton(vc_plugin);
				vc_service.registerPlugin(vc_plugin);
				logger.info("SimpleVoiceChat plugin hooked");
			} else {
				logger.info("SimpleVoiceChat plugin not found");
			}
		}catch(NoClassDefFoundError e){
			// ignore, ca arrive quand il n'y a pas SimpleVoiceChat
		}
		Vi6Styles.load();
		var game = new Vi6Game();
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
		super.onDisable();
	}
	
	private void gameCommands() {
		var gameC = new CommandAPICommand("game");
		var sub = new CommandAPICommand("lobby").executes(_ -> {
			Ioc.resolve(Vi6Game.class).start();
		}, ExecutorType.ALL);
		gameC.withSubcommand(sub);
		gameC.register();
	}
}
