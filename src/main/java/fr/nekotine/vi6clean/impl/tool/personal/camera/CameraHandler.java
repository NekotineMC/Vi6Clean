package fr.nekotine.vi6clean.impl.tool.personal.camera;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.game.phase.PhaseMachine;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.map.MapCamera;
import fr.nekotine.vi6clean.impl.map.MapCamera.State;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

@ToolCode("camera")
public class CameraHandler extends ToolHandler<Camera>{
	private class CameraInfo {
		private int lifeTime;
		private int chargeTime;
		public CameraInfo(int lifeTime, int chargeTime) {
			this.setLifeTime(lifeTime);
			this.setChargeTime(chargeTime);
		}
		public int getLifeTime() {
			return lifeTime;
		}
		public void setLifeTime(int lifeTime) {
			this.lifeTime = lifeTime;
		}
		public int getChargeTime() {
			return chargeTime;
		}
		public void setChargeTime(int chargeTime) {
			this.chargeTime = chargeTime;
		}
	}
	protected ItemStack ITEM = new ItemStackBuilder(Material.ENDER_CHEST)
			.name(getDisplayName())
			.lore(getLore())
			.flags(ItemFlag.values())
			.build();
		
	private final int CHARGES_PER_PLAYER = getConfiguration().getInt("charges",2);
	private final int LIFE_TIME = (int)(20*getConfiguration().getDouble("lifetime",30));
	private final int CHARGE_TIME = (int)(20*getConfiguration().getDouble("chargetime",3));
	private final int GLOW_DURATION = (int)(20*getConfiguration().getDouble("glow_duration",3));
	private int charges = 0;
	private MenuInventory menu;
	private final HashMap<MapCamera, CameraInfo> infos = new HashMap<MapCamera, CameraInfo>();
	public CameraHandler() {
		super(Camera::new);
		Ioc.resolve(PhaseMachine.class).getPhase(Vi6PhaseInMap.class).getMap().getCameras().values().forEach(
				c -> infos.put(c, new CameraInfo(LIFE_TIME, CHARGE_TIME)));
		
	}

	//
	
	private void tick() {
		for(var entry : infos.entrySet()) {
			var camera = entry.getKey();
			var info = entry.getValue();
			switch(camera.getState()) {
			case INACTIVE: break;
			case STARTING:
				var chargeTime = info.getChargeTime();
				if(--chargeTime==0) {
					activeCamera(camera);
				}
				info.setChargeTime(chargeTime);
				break;
			case ACTIVE:
				var lifeTime = info.getLifeTime();
				if(--lifeTime == 0) {
					disableCamera(camera);
				}
				info.setLifeTime(lifeTime);
			}
		}
	}
	private void chargeCamera(MapCamera camera) {
		if(charges == 0) return;
		if(!infos.containsKey(camera)) return;
		var info = infos.get(camera);
		camera.setState(State.STARTING);
		info.setChargeTime(CHARGE_TIME);
		charges--;
	}
	private void activeCamera(MapCamera camera) {
		if(!infos.containsKey(camera)) return;
		var info = infos.get(camera);
		camera.setState(State.ACTIVE);
		info.setLifeTime(LIFE_TIME);
	}
	private void disableCamera(MapCamera camera) {
		if(camera.getState()==State.INACTIVE) return;
		if(!infos.containsKey(camera)) return;
		camera.setState(State.INACTIVE);
		charges++;
	}
	/*
	private void toggleCamera(MapCamera camera) {
		switch(camera.getState()) {
		case INACTIVE: chargeCamera(camera);break;
		case STARTING:
		case ACTIVE: disableCamera(camera);
		}
	}*/
	
	//
	
	protected void addPlayerCharges() {
		this.charges += CHARGES_PER_PLAYER;
	}
	protected void removePlayerCharges() {
		this.charges -= CHARGES_PER_PLAYER;
	}
	@Override
	protected void onAttachedToPlayer(Camera tool, Player player) {
	}
	@Override
	protected void onDetachFromPlayer(Camera tool, Player player) {
	}
}
