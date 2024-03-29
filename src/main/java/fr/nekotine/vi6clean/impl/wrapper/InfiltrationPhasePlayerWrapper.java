package fr.nekotine.vi6clean.impl.wrapper;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.status.flag.AsthmaStatusFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

public class InfiltrationPhasePlayerWrapper extends WrapperBase<Player> {

	private boolean dead;
	
	private List<Artefact> stolen = new LinkedList<>();
	
	public InfiltrationPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
	}

	public List<Artefact> getStolenArtefacts() {
		return stolen;
	}
	
	public void capture(Artefact artefact) {
		var game = Ioc.resolve(Vi6Game.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for (var thief : game.getThiefs()) {
			var wrap = wrappingModule.getWrapper(thief, InMapPhasePlayerWrapper.class);
			wrap.thiefScheduleCanCaptureArtefact();
			if (thief.equals(wrapped)) {
				wrap.thiefScheduleCanLeaveMap();
			}
		}
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
		AsthmaStatusFlag.get().capture(wrapped);
	}

	public boolean isDead() {
		return dead;
	}

	public void setDead(boolean dead) {
		this.dead = dead;
	}
	
}
