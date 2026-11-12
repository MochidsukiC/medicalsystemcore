package jp.houlab.mochidsuki.medicalsystemcore;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Medicalsystemcore implements ModInitializer {
	public static final String MODID = "medicalsystemcore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		LOGGER.info("MedicalSystemCore initialized");
	}
}
