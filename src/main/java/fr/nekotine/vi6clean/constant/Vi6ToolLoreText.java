package fr.nekotine.vi6clean.constant;

import java.util.LinkedList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

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
	);
	
	private static final MiniMessage loreMiniMessage = MiniMessage.builder().tags(TagResolver.builder().resolvers(
			StandardTags.defaults(),
			TagResolver.resolver("lore",Tag.styling(b -> b.color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, State.NOT_SET))),
			TagResolver.resolver("variable", Tag.styling(NamedTextColor.GREEN)),
			TagResolver.resolver("important", Tag.styling(NamedTextColor.AQUA))
			).build()
			)
			.build();
	
	private String[] minimessageFormatted;
	
	private Vi6ToolLoreText(String ... messages) {
		minimessageFormatted = messages;
	}
	
	public List<Component> make(TagResolver ... tagResolvers) {
		var resolver = TagResolver.builder().resolvers(tagResolvers).build();
		var list = new LinkedList<Component>();
		for (var msg : minimessageFormatted) {
			list.add(loreMiniMessage.deserialize(msg, resolver));
		}
		return list;
	}
	
}
