package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.tool.ToolHandlerContainer;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/** Typed access to the running Vi6Clean plugin from an integration test. */
class Vi6SmokeIT {

	@Test
	void enableCompleted() throws Exception {
		var tools = PaperServer.onMain(() -> Ioc.resolve(ToolHandlerContainer.class));
		assertNotNull(tools, "ToolHandlerContainer not registered: Vi6Main.onEnable did not complete");
		// Not isRunning(): Core's PhaseMachine.goTo never sets running on the first
		// transition, so it stays false after a successful start().
		var lobby = PaperServer
				.onMain(() -> Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class));
		assertNotNull(lobby, "Vi6PhaseLobby not entered: game.start() was not reached in onEnable");
	}

	@Test
	void loadedThroughVi6PluginClassLoader() {
		var pluginLoader = PaperServer.plugin().getClass().getClassLoader();
		assertSame(pluginLoader, getClass().getClassLoader().getParent(),
				"test class loader's parent is not Vi6Clean's plugin classloader");
		assertSame(pluginLoader, Ioc.class.getClassLoader(),
				"NekotineCore's Ioc is not loaded from Vi6Clean's shaded jar");
	}
}
