package se.liu.natho280.gbemu.serialization;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.CPU;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.rom.AbstractMBC;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Wrapper object (DTO) for save states; packs the important parts of the emulator and serializes them. Will perform the
 * same thing in reverse.
 */
public class SerializationWrapper {
    private static final Gson GSON = new Gson();
    private CPU cpu = null;
    private Memory memory = null;
    private SerializableMBC smbc = null;

    public SerializationWrapper(CPU cpu, Memory memory) {
	this.cpu = cpu;
	this.memory = memory;
	this.smbc = this.memory.getSerializableMBC();
	if (this.smbc == null) {
	    CuteLogger.log(Level.WARNING, "Save state failed because ROM is null");
	    JOptionPane.showMessageDialog(null, "No ROM loaded!", "Error", JOptionPane.ERROR_MESSAGE);
	}
    }

    public SerializationWrapper(String loadPath) {
	try (FileReader fileReader = new FileReader(loadPath)) {
	    SerializationWrapper sw = GSON.fromJson(fileReader, SerializationWrapper.class);
	    this.cpu = sw.cpu;
	    this.memory = sw.memory;
	    this.smbc = sw.smbc;
	} catch (JsonSyntaxException | IOException e) {
	    CuteLogger.log(Level.INFO, "Loading of save state failed! Error: " + e.getMessage());
	    JOptionPane.showMessageDialog(null, "Loading of save state failed!\n\nError: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	}
    }

    public void serialize(String savePath) {
	try (BufferedWriter writer = new BufferedWriter(new FileWriter(savePath));) {
	    GSON.toJson(this, SerializationWrapper.class, writer);
	} catch (IOException e) {
	    CuteLogger.log(Level.SEVERE, e.getMessage());
	    JOptionPane.showMessageDialog(null, "Save state failed!\n\nError: " + e.getMessage(), "Error!", JOptionPane.ERROR_MESSAGE);
	}
    }

    public CPU getCPU() {
	return this.cpu;
    }

    public Memory getMemory() {
	return this.memory;
    }

    public AbstractMBC getMBC() {
	return this.smbc.getMBC();
    }
}
