package fr.nekotine.vi6clean.impl.wrapper;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;

import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

public class InfiltrationPhasePlayerWrapper extends WrapperBase<Player> {

	private List<Artefact> stolen = new LinkedList<>();
	
	public InfiltrationPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
	}

	public List<Artefact> getStolenArtefacts() {
		return stolen;
	}
	
	public void capture(Artefact artefact) {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		artefact.capture();
		stolen.add(artefact);
		var guardMsg = Component.text("Un ", NamedTextColor.RED)
				.append(Component.text("artéfact", NamedTextColor.DARK_AQUA))
				.append(Component.text(" a été volé !", NamedTextColor.RED));
		var thiefMsg = wrapped.displayName().color(NamedTextColor.AQUA)
				.append(Component.text(" a ", NamedTextColor.GOLD))
				.append(Component.text("volé ", NamedTextColor.DARK_AQUA))
				.append(Component.text(artefact.getName(), NamedTextColor.AQUA));
		var titleTimes = Title.Times.times(Ticks.duration(5L), Ticks.duration(20L), Ticks.duration(20L));
		var guardTitle = Title.title(guardMsg, Component.empty(), titleTimes);
		var thiefTitle = Title.title(thiefMsg, Component.empty(), titleTimes);
		game.getGuards().sendMessage(guardMsg);
		game.getGuards().showTitle(guardTitle);
		game.getThiefs().sendMessage(thiefMsg);
		game.getThiefs().showTitle(thiefTitle);
	}
	
}
