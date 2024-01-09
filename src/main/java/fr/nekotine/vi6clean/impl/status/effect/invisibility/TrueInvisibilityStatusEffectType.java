package fr.nekotine.vi6clean.impl.status.effect.invisibility;

public class TrueInvisibilityStatusEffectType extends AbstractInvisibilityStatusEffectType{
	private static TrueInvisibilityStatusEffectType instance;
	public static final TrueInvisibilityStatusEffectType get() {
		if (instance == null) {
			instance = new TrueInvisibilityStatusEffectType();
		}
		return instance;
	}
}
