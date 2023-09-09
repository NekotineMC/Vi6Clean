package fr.nekotine.vi6clean.constant;

import fr.nekotine.core.effect.CustomEffectType;
import fr.nekotine.vi6clean.impl.effect.InvisibleEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public enum Vi6EffectType {

	INVISIBLE(new InvisibleEffect(), "invisible", MiniMessage.miniMessage().deserialize("<green><bold>Invisible:</bold><br>-Rend invisible aux ennemis<br>-Pas de bruits de pas"));
	
	private final CustomEffectType effect;
	
	private final String statusName;
	
	private final Component description;
	
	private Vi6EffectType(CustomEffectType effect, String statusName,Component description) {
		this.effect = effect;
		this.statusName = statusName;
		this.description = description;
	}

	public CustomEffectType getEffect() {
		return effect;
	}

	public String getStatusName() {
		return statusName;
	}

	public Component getDescription() {
		return description;
	}
	
}
