package fr.nekotine.vi6clean;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.Scanner;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.NekotinePlugin;
import fr.nekotine.core.eventguard.PlayerDoubleEventGuard;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Styles;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.majordom.Majordom;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.tool.ToolHandlerContainer;
import fr.nekotine.vi6clean.impl.wrapper.LobbyPhasePlayerWrapper;
import fr.nekotine.vi6clean.voicechat.Vi6VoiceChatPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Vi6Main extends NekotinePlugin implements Listener {

	private final ComponentLogger logger = NekotineLogger.make(this);

	private WatchService ws;
	
	private BukkitTask watchServiceTask;

	private ResourcePackInfo vi6CleanResourcePack;

	@Override
	public void onLoad() {
		super.onLoad();
		mapCommandsFor(Vi6Map.class);
		gameCommands();
		makeResourcePackInfo();
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
		} catch (NoClassDefFoundError e) {
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
		EventUtil.register(this);
		try {
			ws = FileSystems.getDefault().newWatchService();
			var updateFolder = Bukkit.getUpdateFolderFile();
			updateFolder.toPath().register(ws, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY);
			watchServiceTask = new BukkitRunnable() {
				@Override
				public void run() {
					// Check for update
					try {
						var key = ws.take(); // This call is blocking until file changed
						if (key.pollEvents().size() > 0) {
							new BukkitRunnable() {
								public void run() {
									Bukkit.getServer().restart();
								};
							}.runTaskLater(Ioc.resolve(JavaPlugin.class), 10);
						}
					} catch (Exception e) {
						logger.error("Erreur du system de restart automatique: ", e);
					}
				}
			}.runTaskAsynchronously(this);
		} catch (Exception e) {
			logger.error("Erreur du system de restart automatique: ", e);
		}
	}

	@Override
	public void onDisable() {
		var game = Ioc.resolve(Vi6Game.class);
		game.close();
		super.onDisable();
		EventUtil.unregister(this);
		if (watchServiceTask != null && !watchServiceTask.isCancelled()) {
			watchServiceTask.cancel();
		}
	}

	private void gameCommands() {
		var gameC = new CommandAPICommand("game");
		var lobby = new CommandAPICommand("lobby").executes(_ -> {
			Ioc.resolve(Vi6Game.class).start();
		}, ExecutorType.ALL);
		var team = new CommandAPICommand("team").withArguments(new MultiLiteralArgument("teamName", "guard", "thief"))
				.executesPlayer((p, args) -> {
					var game = Ioc.resolve(Vi6Game.class);
					switch ((String) args.get("teamName")) {
						case "guard" :
							game.addPlayerInGuards(p);
							break;
						case "thief" :
							game.addPlayerInThiefs(p);
							break;
					}
				});
		var ready = new CommandAPICommand("ready").executesPlayer(info -> {
			var p = info.sender();
			var wrapOpt = Ioc.resolve(WrappingModule.class).getWrapperOptional(p, LobbyPhasePlayerWrapper.class);
			if (wrapOpt.isPresent()) {
				var wrap = wrapOpt.get();
				wrap.setReadyForNextPhase(!wrap.isReadyForNextPhase());
			}
		});
		gameC.withSubcommands(lobby, team, ready);
		gameC.register();
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

	private void makeResourcePackInfo() {
		var uuid = UUID.fromString("34ec4879-e002-413b-9fab-4a4dc7949b11");
		var baseurl = "https://github.com/NekotineMC/Vi6Clean/releases/download/dev/";
		var packurl = baseurl + "Vi6CleanPack.zip";
		var checksumurl = packurl + ".sha1";
		String sha;
		logger.info("Lecture du sha1 du resourcepack depuis l'url suivante: " + checksumurl);
		try (var scanner = new Scanner(URI.create(checksumurl).toURL().openStream())) {
			scanner.useDelimiter("\\A");
			sha = scanner.hasNext() ? scanner.next().trim() : "";
		} catch (Exception e) {
			logger.error("Une erreur est survenue lors de la lecture du sha1", e);
			throw new RuntimeException(e); // sad
		}
		logger.info("Le sha1 du resourcepack est le suivant: " + sha);
		vi6CleanResourcePack = ResourcePackInfo.resourcePackInfo(uuid, URI.create(packurl), sha);
	}

	@EventHandler
	public void onPlayerJoined(PlayerJoinEvent evt) {
		var request = ResourcePackRequest.resourcePackRequest();
		request.packs(vi6CleanResourcePack);
		request.required(true)
				.prompt(Component.text("Le pack de resource du Vi6 est nécessaire pour éviter les erreurs visuelles"));
		evt.getPlayer().sendResourcePacks(request.build());
	}
}
