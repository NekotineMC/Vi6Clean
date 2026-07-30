package fr.nekotine.vi6clean.configuration;

import java.nio.file.Path;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.IPluginModule;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class ConfigManager implements IPluginModule {

	private final ComponentLogger logger = NekotineLogger.make();

	private final Path configPath;

	private final YamlConfigurationLoader loader;

	private Vi6Config config = new Vi6Config();

	private BukkitTask loadConfigTask;

	public ConfigManager() {
		var plugin = Ioc.resolve(JavaPlugin.class);
		configPath = plugin.getDataPath().resolve("config_2.yml");
		loader = YamlConfigurationLoader.builder().path(configPath).nodeStyle(NodeStyle.BLOCK).build();
	}

	public void tryLoad() {
		if (configPath.toFile().exists()) {
			try {
				var configNode = loader.load();
				config = configNode.get(Vi6Config.class);
			} catch (ConfigurateException e) {
				logger.warn(
						"Une erreur est survenue lors du chargement de la configuration, les valeurs par défaut vont être utilisées",
						e);
				config = new Vi6Config();
			}
			logger.info("Configuration rechargée");
		}
	}

	public void save() throws ConfigurateException {
		var node = loader.createNode();
		node.set(config);
		loader.save(node);
	}

	/**
	 * Override file with default values provided by the plugin
	 */
	public void saveDefaults() throws ConfigurateException {
		var node = loader.createNode();
		node.set(new Vi6Config());
		loader.save(node);
	}

	/**
	 * Launch a task in background which will load config from file, override it if
	 * necessary, and creating it if necessary
	 */
	public void setupAsync() {
		var plugin = Ioc.resolve(JavaPlugin.class);
		loadConfigTask = new BukkitRunnable() {

			@Override
			public void run() {
				try {
					tryLoad();
					// Override configuration if specified
					if (config.override_config()) {
						saveDefaults();
						config = new Vi6Config();
						logger.info(
								"Configuration écrasée avec les valeurs par défaut du plugin (comme spécifié par \"override-config\")");
					}
				} catch (Exception e) {
					logger.warn(
							"Une erreur est survenue lors de la mise en route du gestionnaire de configuration. Les valeurs par défaut seront chargées",
							e);
				}
			}
		}.runTaskAsynchronously(plugin);
	}

	public Vi6Config getConfig() {
		return config;
	}

	@Override
	public void unload() {
		if (loadConfigTask != null && !loadConfigTask.isCancelled()) {
			loadConfigTask.cancel();
		}
	}

}
