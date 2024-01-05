package fr.nekotine.vi6clean.impl.map.koth;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.TextModule.Builder;
import fr.nekotine.core.text.placeholder.TextPlaceholder;
import fr.nekotine.core.text.style.NekotineStyles;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.tuple.Pair;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.status.effect.DarkenedStatusEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;

public class LightKothEffect extends AbstractKothEffect implements TextPlaceholder{
	private final StatusEffect unlimitedDarkened = new StatusEffect(DarkenedStatusEffectType.get(), -1);
	private final int AMOUNT_FOR_OTHER_CAPTURE = getConfiguration().getInt("koth.emp.capture_amount_other", 200);
	private final int AMOUNT_FOR_GUARD_CAPTURE = getConfiguration().getInt("koth.emp.capture_amount_guard", 400);
	private final String DISPLAY_TEXT = getConfiguration().getString("display_text", "NO TEXT");
	
	//
	
	@Override
	public void tick(Koth koth) {
		koth.setText(textDisplay.buildFirst(koth));
	}
	@Override
	public void capture(Koth koth, Vi6Team owning, Vi6Team losing) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if(losing==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.addEffect(p, unlimitedDarkened));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Les voleurs ont désactivé le générateur", NamedTextColor.YELLOW));
			game.getGuards().sendMessage(Component.text("Les voleurs ont désactivé le générateur", NamedTextColor.YELLOW));
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a déactivé le générateur", NamedTextColor.GREEN));
			game.getThiefs().sendMessage(Component.text("Votre équipe a déactivé le générateur", NamedTextColor.GREEN));
		}else if(owning==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.removeEffect(p, unlimitedDarkened));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Les gardes ont redémarré le générateur", NamedTextColor.RED));
			game.getThiefs().sendMessage(Component.text("Les gardes ont redémarré le générateur", NamedTextColor.RED));
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a redémarré le générateur", NamedTextColor.GREEN));
			game.getGuards().sendMessage(Component.text("Votre équipe a redémarré le générateur", NamedTextColor.GREEN));
		}
	}
	@Override
	public void setup(Koth koth) {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}
	@Override
	public void clean() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		Ioc.resolve(Vi6Game.class).getGuards().forEach(
				p -> statusEffectModule.removeEffect(p, unlimitedDarkened));
	}
	@Override
	public Pair<Particle, DustOptions> getParticle(Vi6Team owning) {
		return Pair.from(Particle.REDSTONE,new DustOptions(Color.YELLOW, 0.5f));
		/*return (owning==Vi6Team.GUARD? 
				Pair.from(Particle.REDSTONE,new DustOptions(Color.YELLOW, 1)):
				Pair.from(Particle.REDSTONE,new DustOptions(Color.RED, 1)));*/
	}

	//
	
	private final Builder textDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine(DISPLAY_TEXT)
			.addPlaceholder(this));
	@Override
	public <T> List<Pair<String, String>> resolve(T resolveData) {
		var koth = (Koth)resolveData;
		var owningTeam = koth.getOwningTeam();
		var tickAdvancement = koth.getTickAdvancement();
		var percentage = (int)(((float)koth.getCaptureAdvancement() / koth.getCaptureAmountNeeded()) * 100);
		var status = owningTeam == Vi6Team.GUARD ? "<green>Actif</green>" : "<red>Désactivé</red>";
		var power = owningTeam == Vi6Team.GUARD ? (100 - percentage)+"%" : percentage+"%";
		if(owningTeam == Vi6Team.GUARD)
			tickAdvancement = -tickAdvancement;
		var evolution = tickAdvancement == 0 ? "-" : (tickAdvancement > 0 ? "<green>↑</green>" : "<red>↓</red>");

		return List.of(
				Pair.from("status", status),
				Pair.from("power", power),
				Pair.from("evolution", evolution));
	}
}
