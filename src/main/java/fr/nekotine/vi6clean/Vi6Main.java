package fr.nekotine.vi6clean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.NekotinePlugin;
import fr.nekotine.core.autorestart.UpdateFolderRestart;
import fr.nekotine.core.eventguard.PlayerDoubleEventGuard;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.majordom.Majordom;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.nekotine.vi6clean.tool.ToolHandlerContainer;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Vi6Main extends NekotinePlugin implements Listener {

	private final ComponentLogger logger = NekotineLogger.make(this);

	private UpdateFolderRestart autoRestart;

	@Override
	public void onLoad() {
		try {
			super.onLoad();
			mapCommandsFor(Vi6Map.class);
			Vi6Commands.registerCommands();
		}catch(Exception e) {
			logger.error("Une erreur est survenue lors du chargement du plugin",e);
		}
	}

	@Override
	public void onEnable() {
		try {
			super.onEnable();
			autoRestart = new UpdateFolderRestart();
			/*-
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
			} catch (NoClassDefFoundError e) {
				// ignore, ca arrive quand il n'y a pas SimpleVoiceChat
			}
			*/
			Vi6Styles.load();
			var game = new Vi6Game();
			Ioc.getProvider().registerSingleton(game);
			Ioc.getProvider().registerSingleton(new Majordom());
			var moduleManager = Ioc.resolve(ModuleManager.class);
			moduleManager.tryLoad(TickingModule.class);
			moduleManager.tryLoad(PlayerDoubleEventGuard.class);
			Vi6ResourcePack.setup();
			var container = new ToolHandlerContainer();
			container.discoverHandlers();
			Ioc.getProvider().registerSingleton(container);
			game.start();
			EventUtil.register(this);
		}catch(Exception e) {
			logger.error("Une erreur est survenue lors de l'activation du plugin",e);
		}
	}

	@Override
	public void onDisable() {
		var game = Ioc.resolve(Vi6Game.class);
		game.close();
		super.onDisable();
		EventUtil.unregister(this);
		autoRestart.close();
	}

	// WORKAROUND https://bugs.mojang.com/browse/MC/issues/MC-277422
	@EventHandler
	public void onHeldItemChange(PlayerItemHeldEvent evt) {
		var player = evt.getPlayer();
		var item = player.getInventory().getItem(evt.getNewSlot());
		if (item == null) {
			return;
		}
		var equipable = item.getData(DataComponentTypes.EQUIPPABLE);
		if (equipable != null && equipable.slot() == EquipmentSlot.HAND) {
			player.playSound(Sound.sound(equipable.equipSound(), Sound.Source.MASTER, 1, 1));
		}
	}
}
