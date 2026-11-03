package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.configuration.Vi6Config;
import fr.nekotine.vi6clean.map.koth.AbstractKothEffect;
import fr.nekotine.vi6clean.map.koth.effect.EmpKothEffect;
import fr.nekotine.vi6clean.map.koth.effect.LightKothEffect;
import fr.nekotine.vi6clean.map.koth.effect.RegenKothEffect;
import fr.nekotine.vi6clean.tool.ToolHandlerContainer;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * Vi6 reads its three kinds of configuration: {@code config.yml} (Configurate),
 * {@code tools/<code>.yml} and {@code koths/<code>.yml} (Bukkit YAML). The
 * staged bundle has {@code override-config: true} and already holds default
 * values, so the match-defaults tests only check the values end up equal; the
 * read and override behaviour is checked by tests that write a non-default
 * value into a staged file first.
 */
class Vi6ConfigIT {

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread.
		PaperServer.awaitTicks(40);
	}

	@Test
	void gameConfigMatchesDefaults() throws Exception {
		var live = PaperServer.onMain(() -> Ioc.resolve(ConfigManager.class).getConfig());
		assertTrue(live.override_config(), "override-config is false in the live config");
		assertEquals(raw(new Vi6Config()), raw(live), "live Vi6Config differs from new Vi6Config()");
	}

	@Test
	void tryLoadReadsConfigFile() throws Exception {
		PaperServer.onMain(() -> {
			var manager = Ioc.resolve(ConfigManager.class);
			var path = Ioc.resolve(JavaPlugin.class).getDataPath().resolve("config.yml");
			var original = Files.readAllBytes(path);
			try {
				var fileLoader = loader(path);
				var node = fileLoader.load();
				node.node("game", "preparation", "duration-seconds").set(123);
				fileLoader.save(node);
				manager.tryLoad();
				assertEquals(Duration.ofSeconds(123), manager.getConfig().game().preparation().duration(),
						"game.preparation.duration-seconds: 123 in config.yml was not read by tryLoad()");
			} finally {
				Files.write(path, original);
				manager.tryLoad();
			}
			return null;
		});
	}

	@Test
	void configFileIsKebabCaseAndReloadsToLiveConfig() throws Exception {
		Path path = PaperServer.onMain(() -> Ioc.resolve(JavaPlugin.class).getDataPath().resolve("config.yml"));
		assertTrue(Files.isRegularFile(path), "config.yml missing: " + path);
		var node = loader(path).load();
		assertFalse(node.node("override-config").virtual(), "config.yml has no override-config key");
		assertFalse(node.node("game", "preparation", "duration-seconds").virtual(),
				"config.yml has no game.preparation.duration-seconds key");
		assertTrue(node.node("override_config").virtual(), "config.yml has a snake_case override_config key");
		var reloaded = node.get(Vi6Config.class);
		var live = PaperServer.onMain(() -> Ioc.resolve(ConfigManager.class).getConfig());
		assertEquals(raw(live), raw(reloaded), "config.yml reloads to different values than the live config");
	}

	@Test
	void toolConfigsMatchJar() throws Exception {
		var handlers = PaperServer.onMain(() -> List.copyOf(Ioc.resolve(ToolHandlerContainer.class).getHandlers()));
		assertFalse(handlers.isEmpty(), "ToolHandlerContainer has no handler");
		for (var handler : handlers) {
			var code = handler.getToolCode();
			assertMatchesJar("tool " + code, "tools/" + code + ".yml", handler::getConfiguration);
		}
	}

	@Test
	void kothConfigsMatchJar() throws Exception {
		List<AbstractKothEffect> effects = PaperServer
				.onMain(() -> List.of(new EmpKothEffect(), new LightKothEffect(), new RegenKothEffect()));
		for (var effect : effects) {
			var code = effect.getCode();
			assertMatchesJar("koth " + code, "koths/" + code + ".yml", effect::getConfiguration);
		}
	}

	@Test
	void kothOverrideReplacesStaleServerValue() throws Exception {
		var plugin = PaperServer.onMain(() -> Ioc.resolve(JavaPlugin.class));
		YamlConfiguration jar;
		try (var in = plugin.getResource("koths/emp.yml")) {
			assertNotNull(in, "koth emp: koths/emp.yml is not in the jar");
			jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		var expected = jar.getDouble("probability");
		assertNotEquals(0.123, expected, "koth emp: jar probability is the stale test value");
		var serverFile = new File(plugin.getDataFolder(), "koths/emp.yml");
		var stale = YamlConfiguration.loadConfiguration(serverFile);
		stale.set("probability", 0.123);
		stale.save(serverFile);
		var effect = PaperServer.onMain(EmpKothEffect::new);
		assertEquals(expected, effect.getConfiguration().getDouble("probability"),
				"koth emp, key probability: live value after a stale server copy");
		assertEquals(expected, YamlConfiguration.loadConfiguration(serverFile).getDouble("probability"),
				"koth emp, key probability: server copy was not overridden");
	}

	/**
	 * The jar holds {@code path}, the server copy exists, and both the live and the
	 * server copy have the jar value for every jar key.
	 */
	private static void assertMatchesJar(String what, String path, Supplier<Configuration> live) throws Exception {
		var plugin = PaperServer.onMain(() -> Ioc.resolve(JavaPlugin.class));
		YamlConfiguration jar;
		try (var in = plugin.getResource(path)) {
			assertNotNull(in, what + ": " + path + " is not in the jar");
			jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		var serverFile = new File(plugin.getDataFolder(), path);
		assertTrue(serverFile.isFile(), what + ": server copy missing: " + serverFile);
		var server = YamlConfiguration.loadConfiguration(serverFile);
		var liveConfig = PaperServer.onMain(live::get);
		assertNotNull(liveConfig, what + ": no live configuration");
		var keys = jar.getKeys(true).stream().filter(k -> !jar.isConfigurationSection(k)).toList();
		assertFalse(keys.isEmpty(), what + ": " + path + " in the jar has no key");
		for (var key : keys) {
			var expected = jar.get(key);
			assertEquals(expected, liveConfig.get(key), what + ", key " + key + ": live value");
			assertEquals(expected, server.get(key), what + ", key " + key + ": server copy value");
		}
	}

	private static YamlConfigurationLoader loader(Path path) {
		return YamlConfigurationLoader.builder().path(path).nodeStyle(NodeStyle.BLOCK).build();
	}

	/** Serializes a config into a Configurate node and returns its raw tree. */
	private static Object raw(Vi6Config config) throws Exception {
		var node = YamlConfigurationLoader.builder().nodeStyle(NodeStyle.BLOCK).build().createNode();
		node.set(Vi6Config.class, config);
		return node.raw();
	}
}
