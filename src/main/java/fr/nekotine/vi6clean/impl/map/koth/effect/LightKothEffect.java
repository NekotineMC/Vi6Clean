package fr.nekotine.vi6clean.impl.map.koth.effect;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
import fr.nekotine.vi6clean.impl.map.koth.AbstractKothEffect;
import fr.nekotine.vi6clean.impl.map.koth.Koth;
import fr.nekotine.vi6clean.impl.map.koth.KothCode;
import fr.nekotine.vi6clean.impl.status.effect.DarkenedStatusEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;

@KothCode("light")
public class LightKothEffect extends AbstractKothEffect implements TextPlaceholder{
	private final StatusEffect unlimitedDarkened = new StatusEffect(DarkenedStatusEffectType.get(), -1);
	private final PotionEffect unlimitedNightVision = new PotionEffect(
			PotionEffectType.NIGHT_VISION, -1, 0, false, false, false);
	private final int AMOUNT_FOR_OTHER_CAPTURE = getConfiguration().getInt("koth.emp.capture_amount_other", 200);
	private final int AMOUNT_FOR_GUARD_CAPTURE = getConfiguration().getInt("koth.emp.capture_amount_guard", 400);
	private final float SLOW_MULTIPLIER = (float)getConfiguration().getDouble("slowness",0.8);
	private final String DISPLAY_TEXT = getConfiguration().getString("display_text", "NO TEXT");
	
	//
	
	@Override
	public void tick() {
		getKoth().setText(textDisplay.buildFirst(getKoth()));
	}
	@Override
	public void capture(Vi6Team owning, Vi6Team losing) {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if(losing==Vi6Team.GUARD) {
			for(Player guard : game.getGuards()) {
				statusEffectModule.addEffect(guard, unlimitedDarkened);
				guard.addPotionEffect(unlimitedNightVision);
				guard.setWalkSpeed(guard.getWalkSpeed() * SLOW_MULTIPLIER);
			}
			getKoth().setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Les voleurs ont désactivé le générateur", NamedTextColor.YELLOW));
			game.getGuards().sendMessage(Component.text("Les voleurs ont désactivé le générateur", NamedTextColor.YELLOW));
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a déactivé le générateur", NamedTextColor.GREEN));
			game.getThiefs().sendMessage(Component.text("Votre équipe a déactivé le générateur", NamedTextColor.GREEN));
		}else if(owning==Vi6Team.GUARD) {
			for(Player guard : game.getGuards()) {
				statusEffectModule.removeEffect(guard, unlimitedDarkened);
				guard.removePotionEffect(PotionEffectType.NIGHT_VISION);
				guard.setWalkSpeed(guard.getWalkSpeed() / SLOW_MULTIPLIER);
			}
			getKoth().setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
			game.getThiefs().sendTitlePart(TitlePart.TITLE,Component.text("Les gardes ont redémarré le générateur", NamedTextColor.RED));
			game.getThiefs().sendMessage(Component.text("Les gardes ont redémarré le générateur", NamedTextColor.RED));
			game.getGuards().sendTitlePart(TitlePart.TITLE,Component.text("Votre équipe a redémarré le générateur", NamedTextColor.GREEN));
			game.getGuards().sendMessage(Component.text("Votre équipe a redémarré le générateur", NamedTextColor.GREEN));
		}
	}
	@Override
	public void setup() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		setBlockDisplayData(Material.YELLOW_STAINED_GLASS.createBlockData());
		getKoth().setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}
	@Override
	public void clean() {
		var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
		Ioc.resolve(Vi6Game.class).getGuards().forEach(
				p -> statusEffectModule.removeEffect(p, unlimitedDarkened));
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
