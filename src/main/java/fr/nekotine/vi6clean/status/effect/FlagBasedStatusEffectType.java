package fr.nekotine.vi6clean.status.effect;

import java.util.function.Supplier;

import org.bukkit.entity.LivingEntity;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffectType;
import fr.nekotine.core.status.flag.StatusFlag;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.vi6clean.status.flag.AsthmaStatusFlag;
import fr.nekotine.vi6clean.status.flag.DarkenedStatusFlag;
import fr.nekotine.vi6clean.status.flag.DiarrheaStatusFlag;
import fr.nekotine.vi6clean.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.status.flag.MurmurStatusFlag;
import fr.nekotine.vi6clean.status.flag.OmniCaptedStatusFlag;
import fr.nekotine.vi6clean.status.flag.SuffocatingStatusFlag;
import fr.nekotine.vi6clean.status.flag.TazedStatusFlag;

public class FlagBasedStatusEffectType implements StatusEffectType {

	public static final FlagBasedStatusEffectType EMP = new FlagBasedStatusEffectType(EmpStatusFlag::get);

	public static final FlagBasedStatusEffectType DARKENED = new FlagBasedStatusEffectType(DarkenedStatusFlag::get);

	public static final FlagBasedStatusEffectType DIARRHEA = new FlagBasedStatusEffectType(DiarrheaStatusFlag::get);

	public static final FlagBasedStatusEffectType MURMUR = new FlagBasedStatusEffectType(MurmurStatusFlag::get);

	public static final FlagBasedStatusEffectType SUFFOCATING = new FlagBasedStatusEffectType(SuffocatingStatusFlag::get);

	public static final FlagBasedStatusEffectType OMNICAPTED = new FlagBasedStatusEffectType(OmniCaptedStatusFlag::get);

	public static final FlagBasedStatusEffectType TAZED = new FlagBasedStatusEffectType(TazedStatusFlag::get);

	public static final FlagBasedStatusEffectType ASTHMA = new FlagBasedStatusEffectType(AsthmaStatusFlag::get);

	private final Supplier<StatusFlag> flagSupplier;

	public FlagBasedStatusEffectType(Supplier<StatusFlag> flagSupplier) {
		this.flagSupplier = flagSupplier;
	}

	@Override
	public void applyEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).addFlag(target, flagSupplier.get());
	}

	@Override
	public void removeEffect(LivingEntity target) {
		Ioc.resolve(StatusFlagModule.class).removeFlag(target, flagSupplier.get());
	}

	public StatusFlag getFlag() {
		return flagSupplier.get();
	}
}
