package fr.nekotine.vi6clean.impl.game.phase;

import java.time.Duration;
import java.util.Map;

import org.bukkit.entity.Player;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Entrance;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InfiltrationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;

public class Vi6PhaseInfiltration extends CollectionPhase<Vi6PhaseInMap, Player>{

	public Vi6PhaseInfiltration(IPhaseMachine machine) {
		super(machine);
	}
	
	@Override
	public Class<Vi6PhaseInMap> getParentType() {
		return Vi6PhaseInMap.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getPlayerList();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void globalSetup(Object inputData) {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		game.getThiefs().spawnToEntrances((Map<Player, Entrance>)inputData);
	}

	@Override
	protected void globalTearDown() {
	}

	@Override
	public void itemSetup(Player item) {
		NekotineCore.MODULES.get(WrappingModule.class).makeWrapper(item, InfiltrationPhasePlayerWrapper::new);
	}

	@Override
	public void itemTearDown(Player item) {
		NekotineCore.MODULES.get(WrappingModule.class).removeWrapper(null, InfiltrationPhasePlayerWrapper.class);
	}
	
	public void checkForCompletion() {
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		if (Vi6Main.IOC.resolve(Vi6Game.class).getGuards().stream().allMatch(
				guard -> !wrappingModule.getWrapper(guard, InMapPhasePlayerWrapper.class).isInside())
				) {
			var game = Vi6Main.IOC.resolve(Vi6Game.class);
			game.sendMessage(Component.text("La partie est finie", NamedTextColor.GOLD));
			game.showTitle(Title.title(Component.text("Fin de partie", NamedTextColor.GOLD), Component.empty(), Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1))));
		}
	}

}
