package fr.nekotine.vi6clean.constant;

import java.util.List;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.tree.Leaf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public enum Vi6ToolLoreText {
	
	/**
	 * <range> = distance entre le joueur et l'ennemi
	 */
	INVISNEAK("<lore>Vous rend <variable><statusname></variable>",
			"<lore>lorsque vous êtes <important>accroupis (<key:key.sneak>).</important>",
			"<lore>Vous êtes visible si un ennemi est",
			"<lore>à moins de <variable><range></variable> de vous"
	),
	OMNICAPTOR("<lore>Pose un capteur qui affiche",
			"<lore>en <variable><statusname></variable> les ennemis",
			"<lore>à moins de <variable><range></variable>.",
			"<lore>Peut être récupéré et déplacé."
	),
	SONAR(	"<lore>Détecte un ennemi à",
			"<lore>moins de <variable><range></variable>",
			"<lore>toutes les <variable><delay></variable>."
	),
	DOUBLEJUMP(	"<lore>Permet de sauter une fois",
			"<lore>en l'air, en appuyant",
			"<lore>deux fois sur <important><key:key.jump></important>."
	),
	TAZER(	"<lore>Rend <variable><statusname></variable> les ennemis en ligne de mire.",
			"<lore>Utilisez <important><key:key.attack></important> pour tirer.",
			"<lore>Temps de recharge: <variable><cooldown></variable>"
	),
	LANTERN(	"<lore>Permet de poser jusqu'à <variable><maxlantern></variable> lanternes",
			"<lore>que vos alliés peuvent prendre pour se téléporter à vous."
	),
	RADAR(	"<lore>Permet de poser un radar qui, après <variable><delay></variable>,",
			"<lore>indique le nombre de voleurs dans un rayon de <variable><range></variable>."
	);

	//
	
	private String[] minimessageFormatted;
	private Vi6ToolLoreText(String ... messages) {
		minimessageFormatted = messages;
	}
	
	//
	
	public String[] text(){
		return minimessageFormatted;
	}
	public List<Component> make(TagResolver... additionalStyles){
		return NekotineCore.MODULES.get(TextModule.class).message(
			Leaf.builder()
				.addLine(minimessageFormatted)
				.addStyle(Vi6Styles.TOOL_LORE)
				.addStyle(additionalStyles)
			).build();
	}
}
