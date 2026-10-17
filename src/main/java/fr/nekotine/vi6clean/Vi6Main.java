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
import fr.nekotine.vi6clean.bot.BotRegistryLoader;
import fr.nekotine.vi6clean.bot.BotService;
import fr.nekotine.vi6clean.bot.CitizensAvailability;
import fr.nekotine.vi6clean.configuration.ConfigManager;
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

	/**
	 * The one line a server without Citizens gets at boot. It names what actually
	 * happened — the roster was never loaded, so the commands will refuse — rather
	 * than "disabled", which would send an operator looking for a command toggle
	 * that does not exist.
	 */
	private static final String BOOT_DEGRADED = "Roster de bots non chargé, les commandes de bot refuseront d'agir : {} {}";

	/**
	 * The remedy {@code REGISTRY_UNAVAILABLE} already names, repeated where the
	 * operator reads it first.
	 */
	private static final String BOOT_REMEDY = "Redémarrez le serveur avec Citizens installé et activé.";

	private UpdateFolderRestart autoRestart;

	@Override
	public void onLoad() {
		try {
			super.onLoad();
			mapCommandsFor(Vi6Map.class);
			Vi6Commands.registerCommands();
		} catch (Exception e) {
			logger.error("Une erreur est survenue lors du chargement du plugin", e);
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
			// BotRegistry is not an IPluginModule and module auto-registration only scans
			// fr.nekotine.core, so this line is what registers the roster at all. The
			// registry class itself is never named here: a `new BotRegistry()` would be
			// resolved by the verifier while Vi6Main is still linking, long before the
			// guard below is reached.
			// The state is read once and both the branch and the line below it answer to
			// that single reading, so the log can never describe a state different from
			// the one the branch acted on. The else is what keeps the boot from being
			// silent: without it a server without Citizens loaded the plugin and left the
			// operator with a dead /game bot and nothing to act on.
			var citizensAvailability = CitizensAvailability.check();
			if (citizensAvailability != CitizensAvailability.READY) {
				logger.info(BOOT_DEGRADED, citizensAvailability.describe(), BOOT_REMEDY);
			} else {
				BotRegistryLoader.load()
						.ifPresent(bots -> Ioc.getProvider().registerSingletonInstanceAs(bots, BotService.class));
			}
			Ioc.getProvider().registerSingleton(new Majordom());
			var moduleManager = Ioc.resolve(ModuleManager.class);
			moduleManager.tryLoad(Majordom.class);
			moduleManager.tryLoad(TickingModule.class);
			moduleManager.tryLoad(PlayerDoubleEventGuard.class);
			var cmanager = moduleManager.get(ConfigManager.class);
			cmanager.setupAsync();
			Ioc.getProvider().registerSingleton(cmanager);
			Vi6ResourcePack.setup();
			var container = new ToolHandlerContainer();
			container.discoverHandlers();
			Ioc.getProvider().registerSingleton(container);
			game.start();
			EventUtil.register(this);
		} catch (Exception e) {
			logger.error("Une erreur est survenue lors de l'activation du plugin", e);
		}
	}

	@Override
	public void onDisable() {
		// Unconditional: tryResolve already answers empty when nothing was registered,
		// and gating on Citizens here is what skipped the cleanup exactly when
		// survivors were most likely. It has to run before game.close(), which tears
		// the lobby phase down and unregisters the scoreboard teams the teardown reads.
		Ioc.getProvider().tryResolve(BotService.class).ifPresent(BotService::destroyAll);
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
