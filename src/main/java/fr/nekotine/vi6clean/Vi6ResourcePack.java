package fr.nekotine.vi6clean;

import java.net.URI;
import java.util.Scanner;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.resourcepack.ResourcePackModule;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.text.Component;

public class Vi6ResourcePack {

	public static void setup() {
		var logger = NekotineLogger.make();
		var moduleManager = Ioc.resolve(ModuleManager.class);
		var resourcepackModule = moduleManager.get(ResourcePackModule.class);
		resourcepackModule.addMandatoryMessage(
				Component.text("Le pack de resource du Vi6 est nécessaire pour éviter les erreurs visuelles"));
		new BukkitRunnable() {
			@Override
			public void run() {
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
				var pack = ResourcePackInfo.resourcePackInfo(uuid, URI.create(packurl), sha);
				resourcepackModule.addMandatoryResourcePack(pack);
			}
		}.runTaskAsynchronously(Ioc.resolve(JavaPlugin.class));
	}

}
