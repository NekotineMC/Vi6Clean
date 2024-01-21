package fr.nekotine.vi6clean.impl.map.koth;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.nekotine.core.configuration.ConfigurationUtil;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.vi6clean.constant.Vi6Team;

public abstract class AbstractKothEffect {
	private final Logger logger = new NekotineLogger(getClass());
	private final String code;
	private Koth koth;
	private Configuration configuration;
	public AbstractKothEffect() {
		var an = getClass().getDeclaredAnnotation(KothCode.class);
		code = an.value();
		try {
			configuration = ConfigurationUtil.updateAndLoadYaml("koths/" + code + ".yml", "/koths/" + code + ".yml");
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Erreur lors du chargement du fichier de configuration du koth " + code, e);
			configuration = new YamlConfiguration();
		}
	}
	
	//
	
	public abstract void tick();
	public abstract void capture(Vi6Team owning, Vi6Team losing);
	public abstract void setup();
	public abstract void clean();

	//
	
	public Configuration getConfiguration() {
		return configuration;
	}
	public double getProbability() {
		return getConfiguration().getDouble("probability", 0);
	}
	public Koth getKoth() {
		return koth;
	}
	public void setKoth(Koth koth) {
		this.koth = koth;
	}
	public void setBlockDisplayData(BlockData data) {
		koth.setBlockDisplayData(data);
	}
	
	public String getCode() {
		return code;
	}
}
