package se.liu.natho280.gbemu;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

/**
 * Static object for logging. Logs logs for logistical logging purposes.
 */
public class CuteLogger {
	private static final int fileLogLimit = 1024 * 1024;
    private static final Logger LOGGER = Logger.getLogger("se.liu.natho280.gbemu");

    public static void log(Level l, String message) {

	FileHandler fileHandler = null;
	try {
	    fileHandler = new FileHandler("cuteLog.log", fileLogLimit, 1, true);
	} catch (IOException e) {
	    LOGGER.log(Level.SEVERE, "Could not open log file:", e + "\nContinuing without a log file.");
	}

	LOGGER.setLevel(Level.ALL);

	if (fileHandler != null) {
	    LOGGER.addHandler(fileHandler);
	    SimpleFormatter simpleFormatter = new SimpleFormatter();
	    fileHandler.setFormatter(simpleFormatter);
	} else {
	    LOGGER.log(Level.SEVERE, "Level: " + l + ", " + message);
	}

    }
}
