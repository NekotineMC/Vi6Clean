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
import fr.nekotine.vi6clean.impl.status.effect.EmpStatusEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;

public class EmpKothEffect implements KothEffect,TextPlaceholder{
	private static final int AMOUNT_FOR_OTHER_CAPTURE = 200;
	private static final int AMOUNT_FOR_GUARD_CAPTURE = 400;
	private static final StatusEffect effect = new StatusEffect(EmpStatusEffectType.get(), -1);
	
	//
	
	private Koth koth;
	@Override
	public void tick() {
		koth.setText(textDisplay.buildFirst());
	}
	@Override
	public void capture(Vi6Team owning, Vi6Team losing) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if(losing==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.addEffect(p, effect));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Les voleurs ont activé le brouilleur", NamedTextColor.DARK_PURPLE));
			game.getGuards().sendMessage(Component.text("Les voleurs ont activé le brouilleur", NamedTextColor.DARK_PURPLE));
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a activé le brouilleur", NamedTextColor.GREEN));
			game.getThiefs().sendMessage(Component.text("Votre équipe a activé le brouilleur", NamedTextColor.GREEN));
		}else if(owning==Vi6Team.GUARD) {
			game.getGuards().forEach(
					p -> statusEffectModule.removeEffect(p, effect));
			koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Les gardes ont désactivé le brouilleur", NamedTextColor.RED));
			game.getThiefs().sendMessage(Component.text("Les gardes ont désactivé le brouilleur", NamedTextColor.RED));
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a désactivé le brouilleur", NamedTextColor.GREEN));
			game.getGuards().sendMessage(Component.text("Votre équipe a désactivé le brouilleur", NamedTextColor.GREEN));
		}
	}
	@Override
	public void setup(Koth koth) {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		this.koth = koth;
		koth.setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}
	@Override
	public void clean() {
		if(koth.getOwningTeam() == Vi6Team.GUARD) 
			return;
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		Ioc.resolve(Vi6Game.class).getGuards().forEach(
				p -> statusEffectModule.removeEffect(p, effect));
	}
	@Override
	public Pair<Particle, DustOptions> getParticle(Vi6Team owning) {
		return Pair.from(Particle.REDSTONE,new DustOptions(Color.PURPLE, 0.5f));
		/*return (owning==Vi6Team.GUARD? 
				Pair.from(Particle.REDSTONE,new DustOptions(Color.RED, 1)):
				Pair.from(Particle.COMPOSTER,null));*/
	}
	
	//
	
	private final Builder textDisplay = Ioc.resolve(TextModule.class).message(Leaf.builder()
			.addStyle(NekotineStyles.STANDART)
			.addLine("<dark_purple><u>Brouilleur</u></dark_purple>\n"
					+"<aqua><power></aqua> <evolution>\n"
					+"<status>")
			.addPlaceholder(this));
	@Override
	public List<Pair<String, String>> resolve() {
		var owningTeam = koth.getOwningTeam();
		var tickAdvancement = koth.getTickAdvancement();
		var percentage = (int)(((float)koth.getCaptureAdvancement() / koth.getCaptureAmountNeeded()) * 100);
		var status = (owningTeam == Vi6Team.GUARD) ? "<red>Désactivé</red>" : "<green>Actif</green>";
		var power = (owningTeam == Vi6Team.GUARD) ? percentage+"%" : (100-percentage)+"%";
		if(owningTeam == Vi6Team.GUARD)
			tickAdvancement = -tickAdvancement;
		var evolution = tickAdvancement == 0 ? "-" : (tickAdvancement > 0 ? "<red>↓</red>" : "<green>↑</green>");

		return List.of(
				Pair.from("status", status),
				Pair.from("power", power),
				Pair.from("evolution", evolution));
	}
}
