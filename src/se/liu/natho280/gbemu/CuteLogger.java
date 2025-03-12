package se.liu.natho280.gbemu;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

public class CuteLogger {

    private static final Logger LOGGER = Logger.getLogger("se.liu.natho280.gbemu");

    public static void log(Level l, String message) {

	FileHandler fileHandler = null;
	try {
	    fileHandler = new FileHandler("cuteLog.log", 1024 * 1024, 1, true);
	} catch (IOException e) {
	    System.err.println("Can't open cuteLog.log, error: " + e.getMessage());
	}

	LOGGER.setLevel(Level.ALL);

	if (fileHandler != null) {
	    LOGGER.addHandler(fileHandler);
	    SimpleFormatter simpleFormatter = new SimpleFormatter();
	    fileHandler.setFormatter(simpleFormatter);
	} else {
	    System.err.println("Level: " + l + ", " + message);
	}

    }
}
