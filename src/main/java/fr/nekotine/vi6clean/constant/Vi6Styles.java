package fr.nekotine.vi6clean.constant;

import fr.nekotine.core.text.style.TextStyle;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public enum Vi6Styles {
	TOOL_LORE(TextStyle.build(
		TagResolver.resolver("lore",Tag.styling(b -> b.color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, State.NOT_SET))),
		TagResolver.resolver("variable", Tag.styling(NamedTextColor.GREEN)),
		TagResolver.resolver("important", Tag.styling(NamedTextColor.AQUA))));
	
	//
	
	private TextStyle style;
	private Vi6Styles(TextStyle style) {
		this.style = style;
	}
	
	//
	
	public TextStyle getStyle() {
		return style;
	}
}
