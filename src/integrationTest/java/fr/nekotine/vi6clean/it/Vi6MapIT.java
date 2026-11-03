package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.IMapModule;
import fr.nekotine.core.map.MapMetadata;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.map.Vi6Map;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/** A test can choose the map, and an unknown map is refused. */
class Vi6MapIT {

	private String defaultMap;

	@BeforeEach
	void rememberDefaultMap() throws Exception {
		defaultMap = PaperServer.onMain(() -> Ioc.resolve(Vi6Game.class).getMapName());
		assertNotNull(defaultMap, "Vi6Game has no default map");
	}

	@AfterEach
	void restoreDefaultMap() throws Exception {
		if (defaultMap == null) {
			return;
		}
		PaperServer.onMain(() -> {
			Ioc.resolve(Vi6Game.class).setMapName(defaultMap);
			return null;
		});
	}

	@Test
	void forcesEachMap() throws Exception {
		List<MapMetadata> maps = PaperServer.onMain(() -> List.copyOf(Ioc.resolve(IMapModule.class).listMaps()));
		assertFalse(maps.isEmpty(), "MapModule lists no map");
		for (var metadata : maps) {
			var name = metadata.getName();
			var expectedWorld = PaperServer
					.onMain(() -> Ioc.resolve(IMapModule.class).getContent(metadata, Vi6Map.class).getWorldName());
			var actual = PaperServer.onMain(() -> {
				var game = Ioc.resolve(Vi6Game.class);
				game.setMapName(name);
				return new String[]{game.getMapName(), game.getWorld().getKey().asMinimalString()};
			});
			assertEquals(name, actual[0], "getMapName() after setMapName(\"" + name + "\")");
			assertEquals(expectedWorld, actual[1], "getWorld() key after setMapName(\"" + name + "\")");
		}
	}

	@Test
	void unknownMapFails() throws Exception {
		var before = PaperServer.onMain(() -> {
			var game = Ioc.resolve(Vi6Game.class);
			return new Object[]{game.getMapName(), game.getWorld()};
		});
		var thrown = assertThrows(NullPointerException.class, () -> PaperServer.onMain(() -> {
			Ioc.resolve(Vi6Game.class).setMapName("NoSuchMap");
			return null;
		}), "setMapName(\"NoSuchMap\") did not throw");
		assertNotNull(thrown.getMessage(), "setMapName(\"NoSuchMap\") threw without a message");
		assertTrue(thrown.getMessage().contains("NoSuchMap"),
				"setMapName(\"NoSuchMap\") message does not name the map: " + thrown.getMessage());
		var after = PaperServer.onMain(() -> {
			var game = Ioc.resolve(Vi6Game.class);
			return new Object[]{game.getMapName(), game.getWorld()};
		});
		assertEquals(before[0], after[0], "map name changed after setMapName(\"NoSuchMap\")");
		assertEquals(before[1], after[1], "world changed after setMapName(\"NoSuchMap\")");
	}
}
