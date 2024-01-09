package fr.nekotine.vi6clean.impl.status.effect.invisibility;

public class SilentInvisibilityStatusEffectType extends AbstractInvisibilityStatusEffectType{
	private static SilentInvisibilityStatusEffectType instance;
	public static final SilentInvisibilityStatusEffectType get() {
		if (instance == null) {
			instance = new SilentInvisibilityStatusEffectType();
		}
		return instance;
	}
}
