package se.liu.natho280.gbemu.serialization;

import com.google.gson.Gson;
import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Registers;
import se.liu.natho280.gbemu.rom.AbstractMBC;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class SerializationWrapper {
    private static final Gson GSON = new Gson();
    private Registers registers = null;
    private Memory memory = null;
    private SerializableMBC smbc = null;

    public SerializationWrapper(Registers registers, Memory memory) {
	this.registers = registers;
	this.memory = memory;
	this.smbc = this.memory.getROM().getMBC().makeSerializable();
	if (this.smbc == null) {
	    throw new IllegalStateException("Serialization failed");
	}
    }

    public SerializationWrapper(String loadPath) {
	try (FileReader fileReader = new FileReader(loadPath)) {
	    SerializationWrapper sw = GSON.fromJson(fileReader, SerializationWrapper.class);
	    this.registers = sw.registers;
	    this.memory = sw.memory;
	    this.smbc = sw.smbc;
	} catch (IOException e) {
	    CuteLogger.log(Level.INFO, e.getMessage());
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

    public Memory getMemory() {
	return this.memory;
    }

    public Registers getRegisters() {
	return this.registers;
    }

    public AbstractMBC getMBC() {
	return this.smbc.getMBC();
    }

    public static void main(String[] args) {
	SerializationWrapper sw = new SerializationWrapper(new Registers(), new Memory("roms/tetris_rev_1.gb"));

	System.out.println(sw.getMemory().getROM().getMBC().getClass());

	sw.serialize("heck.json");
    }
}
