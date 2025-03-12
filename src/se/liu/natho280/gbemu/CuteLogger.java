package se.liu.natho280.gbemu;

import java.util.logging.Logger;
import java.util.logging.Level;

public class CuteLogger {

    private static final Logger LOGGER = Logger.getLogger("se.liu.natho280.gbemu");

    public static void log(Level l, String message) {
	LOGGER.log(l, message);
    }
}
