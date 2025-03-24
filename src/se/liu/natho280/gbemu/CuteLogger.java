package se.liu.natho280.gbemu;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

/**
 * Static object for logging. Logs logs for logical, logistical logging purposes.
 */
public class CuteLogger {
    private static final int KILOBYTE = 1024;
    private static final int FILE_LOG_LIMIT = KILOBYTE * KILOBYTE; // a MiB
    private static final Logger LOGGER = Logger.getLogger("se.liu.natho280.gbemu");

    public static void log(Level l, String message) {

	FileHandler fileHandler = null;
	try {
	    fileHandler = new FileHandler("cuteLog.log", FILE_LOG_LIMIT, 1, true);
	} catch (IOException e) {
	    LOGGER.log(Level.SEVERE, "Could not open log file:", e + "\nContinuing without a log file.");
	}

	LOGGER.setLevel(Level.ALL);

	if (fileHandler != null) {
	    LOGGER.addHandler(fileHandler);
	    SimpleFormatter simpleFormatter = new SimpleFormatter();
	    fileHandler.setFormatter(simpleFormatter);
	    LOGGER.log(l, message);
	    fileHandler.close();
	} else {
	    LOGGER.log(Level.SEVERE, "Level: " + l + ", " + message);
	}
    }
}
