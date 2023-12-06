package fr.nekotine.vi6clean.impl.map.koth;

import fr.nekotine.vi6clean.constant.Vi6Team;

public interface KothEffect {
	public void tick();
	public void capture(Vi6Team owning, Vi6Team losing);
	public void setup(Koth koth);
	public void clean();
}
