package fr.nekotine.vi6clean.impl.tool.personal.emp;

import java.util.List;

import org.bukkit.entity.Player;

import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.tool.ToolType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class EmpHandler extends ToolHandler<Emp>{
	private static final int EMP_DURATION_TICKS = 100;
	public static final List<Component> LORE = Vi6ToolLoreText.EMP.make(
			Placeholder.unparsed("duration", (EMP_DURATION_TICKS/20)+"s"));
	public EmpHandler() {
		super(ToolType.EMP, Emp::new);
	}
	@Override
	protected void onAttachedToPlayer(Emp tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Emp tool, Player player) {
	}

}
