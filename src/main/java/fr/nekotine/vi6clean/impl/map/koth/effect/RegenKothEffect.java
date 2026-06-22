package fr.nekotine.vi6clean.impl.map.koth.effect;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;

@KothCode("regen")
public class RegenKothEffect extends AbstractKothEffect implements TextPlaceholder {
	private final int AMOUNT_FOR_OTHER_CAPTURE = getConfiguration().getInt("capture_amount_other", 200);
	private final int AMOUNT_FOR_GUARD_CAPTURE = getConfiguration().getInt("capture_amount_guard", 400);
	private final String DISPLAY_TEXT = getConfiguration().getString("display_text", "NO TEXT");
	private final int REGEN_DELAY_TICKS = (int) (20 * getConfiguration().getDouble("delay_between_heal", 3.0));
	private final double REGEN_AMOUNT = getConfiguration().getDouble("heal_amount", 1);
	private int elapsed = 0;

	//

	@Override
	public void tick() {
		getKoth().setText(textDisplay.buildFirst(getKoth()));
		if (getKoth().getOwningTeam() == Vi6Team.THIEF) {
			elapsed++;
			if (elapsed >= REGEN_DELAY_TICKS) {
				elapsed = 0;
				Ioc.resolve(Vi6Game.class).getThiefs().forEach(p -> {
					p.setHealth(
							Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + REGEN_AMOUNT));
				});
			}
		}
	}

	@Override
	public void capture(Vi6Team owning, Vi6Team losing) {
		var game = Ioc.resolve(Vi6Game.class);
		if (losing == Vi6Team.GUARD) {
			elapsed = 0;
			getKoth().setCaptureAmountNeeded(AMOUNT_FOR_GUARD_CAPTURE);
			game.getGuards().sendTitlePart(TitlePart.TITLE,
					Component.text("Les voleurs ont activé le régénérateur", NamedTextColor.LIGHT_PURPLE));
			game.getGuards()
					.sendMessage(Component.text("Les voleurs ont activé le régénérateur", NamedTextColor.LIGHT_PURPLE));
			game.getThiefs().sendTitlePart(TitlePart.TITLE,
					Component.text("Votre équipe a activé le régénérateur", NamedTextColor.GREEN));
			game.getThiefs().sendMessage(Component.text("Votre équipe a activé le régénérateur", NamedTextColor.GREEN));
		} else if (owning == Vi6Team.GUARD) {
			getKoth().setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
			game.getThiefs().sendTitlePart(TitlePart.TITLE,
					Component.text("Les gardes ont désactivé le régénérateur", NamedTextColor.RED));
			game.getThiefs()
					.sendMessage(Component.text("Les gardes ont désactivé le régénérateur", NamedTextColor.RED));
			game.getGuards().sendTitlePart(TitlePart.TITLE,
					Component.text("Votre équipe a désactivé le régénérateur", NamedTextColor.GREEN));
			game.getGuards()
					.sendMessage(Component.text("Votre équipe a désactivé le régénérateur", NamedTextColor.GREEN));
		}
	}

	@Override
	public void setup() {
		Ioc.resolve(ModuleManager.class).tryLoad(StatusEffectModule.class);
		setBlockDisplayData(Material.PINK_WOOL.createBlockData());
		getKoth().setCaptureAmountNeeded(AMOUNT_FOR_OTHER_CAPTURE);
	}

	@Override
	public void clean() {
	}

	//

	private final Builder textDisplay = Ioc.resolve(TextModule.class)
			.message(Leaf.builder().addStyle(NekotineStyles.STANDART).addLine(DISPLAY_TEXT).addPlaceholder(this));

	@Override
	public <T> List<Pair<String, String>> resolve(T resolveData) {
		var koth = (Koth) resolveData;
		var owningTeam = koth.getOwningTeam();
		var tickAdvancement = koth.getTickAdvancement();
		var percentage = (int) (((float) koth.getCaptureAdvancement() / koth.getCaptureAmountNeeded()) * 100);
		var status = (owningTeam == Vi6Team.GUARD) ? "<red>Désactivé</red>" : "<green>Actif</green>";
		var power = (owningTeam == Vi6Team.GUARD) ? percentage + "%" : (100 - percentage) + "%";
		if (owningTeam == Vi6Team.GUARD)
			tickAdvancement = -tickAdvancement;
		var evolution = tickAdvancement == 0 ? "-" : (tickAdvancement > 0 ? "<red>↓</red>" : "<green>↑</green>");

		return List.of(Pair.from("status", status), Pair.from("power", power), Pair.from("evolution", evolution));
	}
}
