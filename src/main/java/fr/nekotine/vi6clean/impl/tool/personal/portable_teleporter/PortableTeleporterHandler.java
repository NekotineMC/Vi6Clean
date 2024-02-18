package fr.nekotine.vi6clean.impl.tool.personal.portable_teleporter;

import org.bukkit.entity.Player;

import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("portable_teleporter")
public class PortableTeleporterHandler extends ToolHandler<PortableTeleporter>{

	public PortableTeleporterHandler() {
		super(PortableTeleporter::new);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onAttachedToPlayer(PortableTeleporter tool, Player player) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onDetachFromPlayer(PortableTeleporter tool, Player player) {
		// TODO Auto-generated method stub
		
	}

}
