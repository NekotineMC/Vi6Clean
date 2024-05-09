package fr.nekotine.vi6clean.impl.map.vent;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.bukkit.util.BlockVector;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.map.Vi6Map;

public class VentManager {

	private List<Vent> vents = new LinkedList<>();
	
	public VentManager() {
		var random = new Random();
		var map = Ioc.resolve(Vi6Map.class);
		var allVents = map.getVents();
		var totalVents = allVents.size();
		var nbVents = random.nextInt(2, allVents.size()+1);
	}
	
	public void dispose() {
		
	}
	
	private class Vent {

		private BlockVector ventLocation;
		
		private List<Vent> connectedVents = new LinkedList<>();
		
	}
	
}
