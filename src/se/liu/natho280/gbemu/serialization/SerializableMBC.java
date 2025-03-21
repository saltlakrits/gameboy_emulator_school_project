package se.liu.natho280.gbemu.serialization;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.rom.AbstractMBC;
import se.liu.natho280.gbemu.rom.MBC0;
import se.liu.natho280.gbemu.rom.MBC1;

import java.util.logging.Level;

/**
 * An MBC will be coerced to a SerializableMBC before serializing a save state, this is to more easily implement loading of save states.
 * It can technically be done in other ways (letting gson deal with polymorphism), but they are more complex than should be required
 * for our use-case.
 */
public class SerializableMBC {
    private MBCType type;
    public int[] mbcThings = null;

    public SerializableMBC(MBCType type, int[] mbcThings) {
	this.type = type;
	this.mbcThings = mbcThings;
    }

    public AbstractMBC getMBC() {
	switch (type) {
	    case MBC0:
		return new MBC0();
	    case MBC1:
		return new MBC1(this);
	}

	throw new IllegalStateException("Invalid MBC type.");
    }
}
