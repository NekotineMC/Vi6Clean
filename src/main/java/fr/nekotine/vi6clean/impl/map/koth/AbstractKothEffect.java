package fr.nekotine.vi6clean.impl.map.koth;

import fr.nekotine.core.configuration.ConfigurationUtil;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.vi6clean.constant.Vi6Team;
import java.io.IOException;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractKothEffect {
	private final ComponentLogger logger = NekotineLogger.make();
	private final String code;
	private Koth koth;
	private Configuration configuration;

	public AbstractKothEffect() {
		var an = getClass().getDeclaredAnnotation(KothCode.class);
		code = an.value();
		
		// load configuration
		try {
			if (Ioc.resolve(JavaPlugin.class).getConfig().getBoolean("replace_tool_configs", false)) {
				configuration = ConfigurationUtil.overrideAndLoadYaml("koths/" + code + ".yml", "/koths/" + code + ".yml");
			} else {
				configuration = ConfigurationUtil.updateAndLoadYaml("koths/" + code + ".yml", "/koths/" + code + ".yml");
			}
		} catch (IOException e) {
			logger.error("Erreur lors du chargement du fichier de configuration du koth " + code, e);
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

	public Key getModelKey() {
		var keystring = getConfiguration().getString("model");
		if (keystring == null) {
			return null;
		}
		return Key.key(keystring);
	}
}
