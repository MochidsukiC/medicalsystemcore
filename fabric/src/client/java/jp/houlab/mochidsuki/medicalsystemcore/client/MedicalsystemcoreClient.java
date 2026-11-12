package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.fabricmc.api.ClientModInitializer;

public class MedicalsystemcoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Medicalsystemcore.LOGGER.info("MedicalSystemCore client initialized");
	}
}
