package fr.nekotine.vi6clean.impl.status.effect.invisibility;

public class InvisibilityStatusEffectType extends AbstractInvisibilityStatusEffectType{
	private static InvisibilityStatusEffectType instance;
	public static final InvisibilityStatusEffectType get() {
		if (instance == null) {
			instance = new InvisibilityStatusEffectType();
		}
		return instance;
	}
}
